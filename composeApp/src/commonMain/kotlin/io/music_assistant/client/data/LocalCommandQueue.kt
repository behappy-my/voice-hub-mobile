package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local-player commands travel over the MA API, not over Sendspin. While the
 * command transport is not ready they are queued (deduplicated per action) and
 * replayed by one worker that wakes when [isReady] turns true or when a
 * command is queued while already ready (a send that failed across a
 * readiness bounce). Sendspin activation is not a trigger: the audio
 * connection may stay up through an MA-only outage.
 *
 * Queueing also calls [requestRecovery]. Every command here comes from a user
 * gesture or an interruption, so the queue asks for the session back instead of
 * waiting for readiness to arrive on its own — a backgrounded session torn down
 * for idleness would otherwise only come back on the next foreground.
 *
 * A replay is sent once; a failure during replay is logged and dropped, so
 * a persistently failing command cannot spin the worker.
 */
internal class LocalCommandQueue(
    private val isReady: StateFlow<Boolean>,
    private val send: suspend (Request) -> Result<*>,
    scope: CoroutineScope,
    private val playbackReady: StateFlow<Boolean> = isReady,
    private val requestRecovery: () -> Unit,
) {
    private val log = Logger.withTag("LocalCommandQueue")
    private val mutex = Mutex()
    private val deliveryMutex = Mutex()
    private val queue = mutableListOf<Entry>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val pendingExpiry = scope

    private data class Entry(val action: PlayerAction, val request: Request)

    init {
        scope.launch { combine(isReady, playbackReady) { api, audio -> api to audio }.collect { wake.trySend(Unit) } }
        scope.launch { wake.consumeEach { if (isReady.value) drain() } }
    }

    suspend fun sendOrQueue(action: PlayerAction, request: Request) = deliveryMutex.withLock {
        // A newer transport intention must cancel a queued resume, even if the
        // newer Pause can be sent immediately while audio is reconnecting.
        if (action == PlayerAction.Play || action == PlayerAction.Pause) {
            mutex.withLock { queue.removeAll {
                it.action == PlayerAction.Play || it.action == PlayerAction.Pause ||
                    (action == PlayerAction.Pause && it.action.needsAudio)
            } }
        }
        if (!readyFor(action)) {
            // Enqueue first: the drain must see the entry when readiness flips.
            enqueue(action, request)
            requestRecovery()
            return@withLock
        }
        if (send(request).isFailure) {
            enqueue(action, request)
            wake.trySend(Unit)
        }
    }

    suspend fun clear() = mutex.withLock { queue.clear() }

    private fun readyFor(action: PlayerAction): Boolean = isReady.value &&
        (!action.needsAudio || playbackReady.value)

    private val PlayerAction.needsAudio: Boolean
        get() = this == PlayerAction.Play || this == PlayerAction.TogglePlayPause ||
            this == PlayerAction.Next || this == PlayerAction.Previous ||
            this is PlayerAction.SeekTo || this is PlayerAction.SeekBy

    private suspend fun drain() {
        while (true) {
            val delivered = deliveryMutex.withLock {
                val entry = mutex.withLock {
                    val index = queue.indexOfFirst { readyFor(it.action) }
                    if (index < 0) null else queue.removeAt(index)
                } ?: return@withLock false
                send(entry.request).onFailure { log.w { "Replay of ${entry.request.command} failed: ${it.message}" } }
                true
            }
            if (!delivered) return
            delay(REPLAY_SPACING_MS)
        }
    }

    private suspend fun enqueue(action: PlayerAction, request: Request) {
        mutex.withLock {
            val entry = Entry(action, request)
            fun toggle(match: (PlayerAction) -> Boolean) {
                val idx = queue.indexOfFirst { match(it.action) }
                if (idx >= 0) queue.removeAt(idx) else queue.add(entry)
            }
            when (action) {
                PlayerAction.TogglePlayPause -> toggle { it is PlayerAction.TogglePlayPause }
                PlayerAction.Play, PlayerAction.Pause -> {
                    queue.removeAll { it.action is PlayerAction.Play || it.action is PlayerAction.Pause }
                    queue.add(entry)
                }

                is PlayerAction.ToggleShuffle -> toggle { it is PlayerAction.ToggleShuffle }
                is PlayerAction.ToggleRepeatMode -> {
                    queue.removeAll { it.action is PlayerAction.ToggleRepeatMode }
                    queue.add(entry)
                }

                is PlayerAction.ToggleDontStopTheMusic -> toggle { it is PlayerAction.ToggleDontStopTheMusic }
                is PlayerAction.ToggleCrossfade -> toggle { it is PlayerAction.ToggleCrossfade }
                is PlayerAction.SeekTo -> {
                    queue.removeAll { it.action is PlayerAction.SeekTo }
                    queue.add(entry)
                }

                else -> queue.add(entry)
            }
            // Never replay yesterday's Play after a prolonged outage. Identity
            // comparison avoids an older expiry deleting a replacement intent.
            pendingExpiry.launch {
                delay(10_000)
                mutex.withLock {
                    if (queue.removeAll { it === entry }) log.w { "Expired queued ${entry.request.command}" }
                }
            }
        }
    }

    private companion object {
        const val REPLAY_SPACING_MS = 100L
    }
}
