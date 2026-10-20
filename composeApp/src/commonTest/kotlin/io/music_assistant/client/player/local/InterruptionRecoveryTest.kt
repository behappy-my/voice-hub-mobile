package io.music_assistant.client.player.local

import io.music_assistant.client.player.local.InterruptionRecovery.Action.*
import kotlin.test.Test
import kotlin.test.assertEquals

class InterruptionRecoveryTest {
    @Test fun callWithoutFocusCallbackResumesWhenCallEnds() {
        val state = InterruptionRecovery()
        assertEquals(INTERRUPT, state.callMode(true))
        assertEquals(RESUME, state.callMode(false))
        assertEquals(NONE, state.callMode(false))
    }
    @Test fun focusAndCallCallbacksProduceOnlyOnePause() {
        val state = InterruptionRecovery()
        assertEquals(INTERRUPT, state.focusLost(true))
        assertEquals(NONE, state.callMode(true))
        assertEquals(NONE, state.focusGained())
        assertEquals(RESUME, state.callMode(false))
        assertEquals(NONE, state.focusGained())
    }
    @Test fun callEndingDoesNotOverrideFocusHeldByAnotherApp() {
        val state = InterruptionRecovery()
        state.callMode(true)
        state.focusLost(true)
        assertEquals(NONE, state.callMode(false))
        assertEquals(RESUME, state.focusGained())
    }
    @Test fun permanentFocusLossNeverAutoResumes() {
        val state = InterruptionRecovery()
        state.focusLost(false)
        state.callMode(true)
        state.callMode(false)
        assertEquals(NONE, state.focusGained())
    }
    @Test fun bluetoothDisconnectDuringCallCancelsResume() {
        val state = InterruptionRecovery()
        state.callMode(true)
        state.outputDisconnected()
        assertEquals(NONE, state.callMode(false))
        assertEquals(NONE, state.focusGained())
    }
    @Test fun normalIdleModeDoesNotStartPlayback() {
        val state = InterruptionRecovery()
        assertEquals(NONE, state.callMode(false))
        assertEquals(NONE, state.focusGained())
    }
}
