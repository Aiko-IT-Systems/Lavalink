package lavalink.server.player.transport

/**
 * Holds Discord voice connection state information.
 * Stub for Phase 1 — will be replaced by the full implementation.
 */
data class VoiceStateInfo(
    val sessionId: String,
    val endpoint: String,
    val token: String,
    val channelId: Long
)
