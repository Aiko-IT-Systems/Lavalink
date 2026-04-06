package lavalink.server.player.transport

import dev.arbjerg.lavalink.protocol.v4.Message
import lavalink.server.io.SocketContext
import lavalink.server.io.SocketServer
import lavalink.server.player.LavalinkPlayer
import moe.kyokobot.koe.KoeClient
import moe.kyokobot.koe.KoeEventAdapter
import moe.kyokobot.koe.MediaConnection
import moe.kyokobot.koe.VoiceServerInfo
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * Default [VoiceTransport] implementation that delegates to the Koe UDP voice library.
 *
 * @param koe         The per-session [KoeClient] that manages voice gateway connections.
 * @param contextRef  Lazy reference to the owning [SocketContext] (set after construction).
 */
class KoeVoiceTransport(
    private val koe: KoeClient,
    private val contextRef: () -> SocketContext
) : VoiceTransport {

    companion object {
        private val log = LoggerFactory.getLogger(KoeVoiceTransport::class.java)
    }

    override fun onVoiceStateUpdate(player: LavalinkPlayer, state: VoiceStateInfo) {
        val guildId = player.guildId
        val oldConn = koe.getConnection(guildId)
        if (oldConn == null ||
            oldConn.gatewayConnection?.isOpen == false ||
            oldConn.voiceServerInfo == null ||
            oldConn.voiceServerInfo?.endpoint != state.endpoint ||
            oldConn.voiceServerInfo?.token != state.token ||
            oldConn.voiceServerInfo?.sessionId != state.sessionId ||
            oldConn.voiceServerInfo?.channelId != state.channelId
        ) {
            koe.destroyConnection(guildId)
            val conn = getOrCreateConnection(player)
            conn.connect(
                VoiceServerInfo.builder()
                    .setSessionId(state.sessionId)
                    .setEndpoint(state.endpoint)
                    .setToken(state.token)
                    .setChannelId(state.channelId)
                    .build()
            ).toCompletableFuture().join()
            conn.audioSender = player.createFrameProvider()
        }
    }

    override fun attachFrameSource(player: LavalinkPlayer) {
        val conn = getOrCreateConnection(player)
        conn.audioSender = player.createFrameProvider()
    }

    override fun destroyPlayer(guildId: Long) {
        koe.destroyConnection(guildId)
    }

    override fun isConnected(guildId: Long): Boolean {
        return koe.getConnection(guildId)?.gatewayConnection?.isOpen == true
    }

    override fun getPing(guildId: Long): Long {
        return koe.getConnection(guildId)?.gatewayConnection?.ping ?: -1L
    }

    override fun getVoiceStateInfo(guildId: Long): VoiceStateInfo? {
        val info = koe.getConnection(guildId)?.voiceServerInfo ?: return null
        return VoiceStateInfo(
            sessionId = info.sessionId,
            endpoint = info.endpoint,
            token = info.token,
            channelId = info.channelId
        )
    }

    override fun shutdown() {
        koe.close()
    }

    private fun getOrCreateConnection(player: LavalinkPlayer): MediaConnection {
        val guildId = player.guildId
        var conn = koe.getConnection(guildId)
        if (conn == null) {
            conn = koe.createConnection(guildId)
            conn.registerListener(WsEventHandler(player))
        }
        return conn
    }

    private inner class WsEventHandler(private val player: LavalinkPlayer) : KoeEventAdapter() {
        override fun gatewayClosed(code: Int, reason: String?, byRemote: Boolean) {
            val context = contextRef()
            val event = Message.EmittedEvent.WebSocketClosedEvent(
                player.guildId.toString(),
                code,
                reason ?: "",
                byRemote
            )
            context.sendMessage(Message.Serializer, event)
            SocketServer.sendPlayerUpdate(context, player)
        }

        override fun gatewayReady(target: InetSocketAddress?, ssrc: Int) {
            SocketServer.sendPlayerUpdate(contextRef(), player)
        }

        override fun gatewayError(cause: Throwable) {
            log.error("Koe encountered a voice gateway exception for guild ${player.guildId}", cause)
        }
    }
}
