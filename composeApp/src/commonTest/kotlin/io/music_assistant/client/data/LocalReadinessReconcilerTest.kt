package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LocalReadinessReconcilerTest {
    @Test
    fun earlyUnavailableSnapshotIsRefetchedAndQueuedPlayIsSentOnce() = runTest {
        val connected = MutableStateFlow(true)
        val available = MutableStateFlow(false)
        val sent = mutableListOf<String>()
        var queries = 0
        val queue = LocalCommandQueue(MutableStateFlow(true), {
            sent += it.command
            Result.success(Unit)
        }, backgroundScope, available) {}
        reconcileLocalReadiness(backgroundScope, connected, available) {
            queries++
            available.value = true // authoritative response, not a speculative ready flag
        }
        queue.sendOrQueue(PlayerAction.Play, Request(command = "play"))
        runCurrent()
        assertEquals(emptyList(), sent)
        advanceTimeBy(201)
        runCurrent()
        assertEquals(listOf("play"), sent)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, queries)
        assertEquals(listOf("play"), sent)
    }

    @Test
    fun serverRemainingUnavailableDoesNotCauseEndlessQueries() = runTest {
        var queries = 0
        reconcileLocalReadiness(backgroundScope, MutableStateFlow(true), MutableStateFlow(false)) { queries++ }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(4, queries)
    }

    @Test
    fun disconnectCancelsPendingReadsAndReconnectStartsANewBoundedWindow() = runTest {
        val connected = MutableStateFlow(true)
        var queries = 0
        reconcileLocalReadiness(backgroundScope, connected, MutableStateFlow(false)) { queries++ }
        runCurrent()
        advanceTimeBy(201)
        runCurrent()
        connected.value = false
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, queries)
        connected.value = true
        runCurrent()
        advanceTimeBy(201)
        runCurrent()
        assertEquals(2, queries)
    }

    @Test
    fun healthyConnectionDoesNotPoll() = runTest {
        var queries = 0
        reconcileLocalReadiness(backgroundScope, MutableStateFlow(true), MutableStateFlow(true)) { queries++ }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, queries)
    }

    @Test
    fun pauseBeforeRegistrationPreventsLateAutomaticPlayback() = runTest {
        val available = MutableStateFlow(false)
        val sent = mutableListOf<String>()
        val queue = LocalCommandQueue(MutableStateFlow(true), {
            sent += it.command
            Result.success(Unit)
        }, backgroundScope, available) {}
        reconcileLocalReadiness(backgroundScope, MutableStateFlow(true), available) { available.value = true }
        queue.sendOrQueue(PlayerAction.Play, Request(command = "play"))
        queue.sendOrQueue(PlayerAction.Pause, Request(command = "pause"))
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf("pause"), sent)
    }
}
