package lavalink.server.player.transport

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.AbstractWebSocketHandler
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

class BridgeWebSocketServer(
    private val authToken: String
) : AbstractWebSocketHandler() {

    companion object {
        private val log = LoggerFactory.getLogger(BridgeWebSocketServer::class.java)
        private val objectMapper = ObjectMapper()
    }

    private val clients = CopyOnWriteArrayList<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info("[Bridge] Client connected: ${session.id}")
        clients.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        log.info("[Bridge] Client disconnected: ${session.id} (${status.code})")
        clients.remove(session)
    }

    fun sendControlMessage(payload: Map<String, String>) {
        val json = objectMapper.writeValueAsString(payload)
        val message = TextMessage(json)
        broadcastToClients(message)
    }

    fun sendBinaryFrame(data: ByteBuffer) {
        val message = BinaryMessage(data)
        broadcastToClients(message)
    }

    private fun broadcastToClients(message: WebSocketMessage<*>) {
        clients.forEach { session ->
            try {
                if (session.isOpen) {
                    session.sendMessage(message)
                }
            } catch (e: Exception) {
                log.warn("[Bridge] Failed to send message to ${session.id}", e)
            }
        }
    }
}
