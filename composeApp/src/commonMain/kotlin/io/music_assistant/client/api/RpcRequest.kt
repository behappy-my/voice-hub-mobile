package io.music_assistant.client.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** The server may have executed the command. Replaying it could skip twice or resume after Pause. */
internal class RpcDeliveryUnknownException(message: String) : IllegalStateException(message)

/** A lost reply must not hold a caller (notably the local media-command lock) forever. */
internal suspend fun RpcEngine.awaitResponse(
    request: Request,
    timeoutMs: Long,
    send: suspend () -> Unit,
): Result<Answer> {
    val response = CompletableDeferred<Result<Answer>>()
    registerCallback(request.messageId, onFailure = { response.complete(Result.failure(it)) }) {
        response.complete(Result.success(it))
    }
    return try {
        withTimeoutOrNull(timeoutMs) {
            try {
                send()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                response.complete(Result.failure(error))
            }
            response.await()
        } ?: Result.failure(RpcDeliveryUnknownException("RPC ${request.command} timed out after ${timeoutMs}ms"))
    } finally {
        removeCallback(request.messageId)
        response.cancel()
    }
}
