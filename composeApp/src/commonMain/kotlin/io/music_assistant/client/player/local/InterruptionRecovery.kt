package io.music_assistant.client.player.local

/** A call-mode callback and a focus callback may describe the same interruption.
 * Resume once, only after both blockers clear. A noisy-route/permanent loss wins.
 */
class InterruptionRecovery {
    enum class Action { NONE, INTERRUPT, RESUME }

    private var interrupted = false
    private var permanent = false
    private var focusBlocked = false
    private var callActive = false

    fun focusLost(transient: Boolean): Action {
        focusBlocked = true
        if (!transient) permanent = true
        return interrupt()
    }

    fun focusGained(): Action {
        focusBlocked = false
        return resumeIfReady()
    }

    fun callMode(active: Boolean): Action {
        callActive = active
        return if (active) interrupt() else resumeIfReady()
    }

    fun outputDisconnected(): Action {
        permanent = true
        return interrupt()
    }

    private fun interrupt(): Action {
        if (interrupted) return Action.NONE
        interrupted = true
        return Action.INTERRUPT
    }

    private fun resumeIfReady(): Action {
        if (!interrupted || permanent || focusBlocked || callActive) return Action.NONE
        interrupted = false
        return Action.RESUME
    }
}
