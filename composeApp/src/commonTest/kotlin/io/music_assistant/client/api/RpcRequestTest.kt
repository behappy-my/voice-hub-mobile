package io.music_assistant.client.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RpcRequestTest {
    private fun engine() = RpcEngine({}, {})
    private fun answer(id: String) = Json.parseToJsonElement("""{"message_id":"$id","result":null}""") as JsonObject

    @Test
    fun disconnectFinishesTheWaiterAndAReconnectedRequestWorks() = runTest {
        val rpc = engine()
        val old = async { rpc.awaitResponse(Request("players/cmd/play", messageId = "old"), 10_000) {} }
        runCurrent()
        rpc.clear()
        runCurrent()
        assertTrue(old.isCompleted)
        assertIs<RpcDeliveryUnknownException>(old.await().exceptionOrNull())
        val fresh = async {
            rpc.awaitResponse(Request("players/cmd/play", messageId = "fresh"), 10_000) {}
        }
        runCurrent()
        rpc.handleResponse(answer("old"))
        assertFalse(fresh.isCompleted, "an old socket's reply must not complete the new request")
        rpc.handleResponse(answer("fresh"))
        assertTrue(fresh.await().isSuccess)
    }

    @Test
    fun missingReplyHasADeadlineEvenWhenTheSocketClaimsToBeConnected() = runTest {
        val rpc = engine()
        val pending = async { rpc.awaitResponse(Request("players/cmd/next", messageId = "old"), 1_000) {} }
        runCurrent()
        advanceTimeBy(1_001)
        assertIs<RpcDeliveryUnknownException>(pending.await().exceptionOrNull())
        rpc.handleResponse(answer("old")) // late reply is harmless
        rpc.clear()
    }

    @Test
    fun aBlockedSocketSendIsAlsoBounded() = runTest {
        val rpc = engine()
        val never = CompletableDeferred<Unit>()
        val pending = async {
            rpc.awaitResponse(Request("players/cmd/play"), 1_000) { never.await() }
        }
        advanceTimeBy(1_001)
        assertIs<RpcDeliveryUnknownException>(pending.await().exceptionOrNull())
    }

    @Test
    fun callerCancellationIsNotConvertedIntoAPlaybackRetry() = runTest {
        val rpc = engine()
        var returned = false
        val pending = launch {
            rpc.awaitResponse(Request("players/cmd/play", messageId = "old"), 10_000) {}
            returned = true
        }
        runCurrent()
        pending.cancel()
        pending.join()
        assertFalse(returned)
        rpc.handleResponse(answer("old"))
        rpc.clear()
    }

    @Test
    fun clearNotifiesEachPendingFailureExactlyOnceAndNotCompletedRequests() {
        val rpc = engine()
        var successes = 0
        var failures = 0
        rpc.registerCallback("done", onFailure = { failures++ }) { successes++ }
        rpc.registerCallback("waiting", onFailure = { failures++ }) { successes++ }
        rpc.handleResponse(answer("done"))
        rpc.clear()
        rpc.clear()
        rpc.handleResponse(answer("waiting"))
        assertEquals(1, successes)
        assertEquals(1, failures)
    }
}
