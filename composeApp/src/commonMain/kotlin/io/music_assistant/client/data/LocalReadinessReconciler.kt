package io.music_assistant.client.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The audio hello can arrive before MA publishes the player as available.
 * Refetch the authoritative state a bounded number of times in that gap.
 * This never declares a player ready and never issues playback commands.
 */
internal fun reconcileLocalReadiness(
    scope: CoroutineScope,
    connected: Flow<Boolean>,
    available: Flow<Boolean>,
    refresh: suspend () -> Unit,
): Job = scope.launch {
    combine(connected, available) { audio, server -> audio to server }
        .distinctUntilChanged()
        .collectLatest { (audio, server) ->
            if (!audio || server) return@collectLatest
            // Relative delays: 0.2, 0.8, 2.0, 4.0 seconds after the mismatch.
            for (waitMs in listOf(200L, 600L, 1_200L, 2_000L)) {
                delay(waitMs)
                refresh()
            }
        }
}
