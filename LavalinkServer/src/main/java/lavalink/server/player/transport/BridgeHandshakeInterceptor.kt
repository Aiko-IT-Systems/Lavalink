package lavalink.server.player.transport

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

class BridgeHandshakeInterceptor(
    private val authToken: String
) : HandshakeInterceptor {

    companion object {
        private val log = LoggerFactory.getLogger(BridgeHandshakeInterceptor::class.java)
    }

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val auth = request.headers.getFirst("Authorization")
        if (auth == null || auth != "Bearer $authToken") {
            log.warn("[Bridge] Unauthorized connection attempt from ${request.remoteAddress}")
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        // No-op
    }
}
