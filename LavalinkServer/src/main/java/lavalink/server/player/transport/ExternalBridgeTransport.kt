package lavalink.server.player.transport

import io.netty.buffer.Unpooled
import lavalink.server.player.LavalinkPlayer
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.*

class ExternalBridgeTransport(
    private val bridgeServer: BridgeWebSocketServer
) : VoiceTransport {

    companion object {
        private val log = LoggerFactory.getLogger(ExternalBridgeTransport::class.java)
        private const val FRAME_INTERVAL_MS = 20L
        private const val PROTOCOL_VERSION: Byte = 1
        private const val PACKET_TYPE_OPUS: Byte = 0
        private const val SAMPLES_PER_FRAME = 960 // 48 kHz * 20 ms
    }

    private data class GuildState(
        val voiceState: VoiceStateInfo? = null,
        var pollingFuture: ScheduledFuture<*>? = null,
        var sequence: Int = 0,
        var timestamp: Int = 0
    )

    private val guilds = ConcurrentHashMap<Long, GuildState>()
    private val scheduler = Executors.newScheduledThreadPool(2) { r ->
        Thread(r).apply {
            name = "bridge-frame-poller"
            isDaemon = true
        }
    }

    override fun onVoiceStateUpdate(player: LavalinkPlayer, state: VoiceStateInfo) {
        val guildId = player.guildId
        guilds.compute(guildId) { _, existing ->
            (existing ?: GuildState()).copy(voiceState = state)
        }

        bridgeServer.sendControlMessage(mapOf(
            "op" to "voice_state",
            "guildId" to guildId.toString(),
            "sessionId" to state.sessionId,
            "endpoint" to state.endpoint,
            "token" to state.token,
            "channelId" to state.channelId.toString()
        ))
    }

    override fun attachFrameSource(player: LavalinkPlayer) {
        val guildId = player.guildId
        val provider = player.createFrameProvider()

        guilds.compute(guildId) { _, existing ->
            val gs = existing ?: GuildState()
            gs.pollingFuture?.cancel(false)
            gs.sequence = 0
            gs.timestamp = 0

            gs.pollingFuture = scheduler.scheduleAtFixedRate({
                try {
                    if (provider.canProvide()) {
                        val buf = Unpooled.buffer(1024)
                        provider.provideFrame(buf)
                        val opusBytes = ByteArray(buf.readableBytes())
                        buf.readBytes(opusBytes)
                        buf.release()

                        sendOpusFrame(guildId, gs.sequence++, gs.timestamp, 20, opusBytes)
                        gs.timestamp += SAMPLES_PER_FRAME
                    }
                } catch (e: Exception) {
                    log.error("Error polling frames for guild $guildId", e)
                }
            }, 0, FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS)

            gs
        }

        bridgeServer.sendControlMessage(mapOf(
            "op" to "player_attached",
            "guildId" to guildId.toString()
        ))
    }

    override fun destroyPlayer(guildId: Long) {
        guilds.remove(guildId)?.let { state ->
            state.pollingFuture?.cancel(false)
        }

        bridgeServer.sendControlMessage(mapOf(
            "op" to "player_detached",
            "guildId" to guildId.toString()
        ))
    }

    override fun isConnected(guildId: Long): Boolean {
        return guilds[guildId]?.pollingFuture?.isCancelled == false
    }

    override fun getPing(guildId: Long): Long {
        // Bridge mode has no direct voice gateway ping
        return -1L
    }

    override fun getVoiceStateInfo(guildId: Long): VoiceStateInfo? {
        return guilds[guildId]?.voiceState
    }

    override fun shutdown() {
        guilds.forEach { (_, state) ->
            state.pollingFuture?.cancel(false)
        }
        guilds.clear()
        scheduler.shutdown()
    }

    private fun sendOpusFrame(guildId: Long, sequence: Int, timestamp: Int, durationMs: Int, opus: ByteArray) {
        val headerSize = 20
        val frame = ByteBuffer.allocate(headerSize + opus.size)
        frame.put(PROTOCOL_VERSION)
        frame.put(PACKET_TYPE_OPUS)
        frame.putLong(guildId)
        frame.putInt(sequence)
        frame.putInt(timestamp)
        frame.putShort(durationMs.toShort())
        frame.put(opus)
        frame.flip()

        bridgeServer.sendBinaryFrame(frame)
    }
}
