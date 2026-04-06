package lavalink.server.config

import lavalink.server.player.transport.BridgeHandshakeInterceptor
import lavalink.server.player.transport.BridgeWebSocketServer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@ConditionalOnProperty(prefix = "lavalink.server", name = ["transport-mode"], havingValue = "external_bridge")
class BridgeWebSocketConfig(
    private val serverConfig: ServerConfig
) : WebSocketConfigurer {

    companion object {
        private val log = LoggerFactory.getLogger(BridgeWebSocketConfig::class.java)
    }

    @Bean
    fun bridgeWebSocketServer(): BridgeWebSocketServer {
        val token = serverConfig.bridge?.authToken ?: "change-me"
        log.info("[Bridge] Initializing bridge WebSocket server")
        return BridgeWebSocketServer(token)
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val token = serverConfig.bridge?.authToken ?: "change-me"
        registry.addHandler(bridgeWebSocketServer(), "/bridge/v1")
            .addInterceptors(BridgeHandshakeInterceptor(token))
    }
}
