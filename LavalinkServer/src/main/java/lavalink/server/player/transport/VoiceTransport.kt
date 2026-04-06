package lavalink.server.player.transport

import lavalink.server.player.LavalinkPlayer

/**
 * Abstraction over how audio frames reach Discord.
 * The default implementation ([KoeVoiceTransport]) delegates to the Koe library.
 * Alternative implementations (e.g., [ExternalBridgeTransport]) can export frames over WebSocket.
 */
interface VoiceTransport {
    /**
     * Called when the Lavalink REST API receives a voice state update for a player.
     * The transport should establish or update its connection to Discord's voice servers.
     */
    fun onVoiceStateUpdate(player: LavalinkPlayer, state: VoiceStateInfo)

    /**
     * Called when a player starts or resumes playback.
     * The transport should begin polling [LavalinkPlayer.createFrameProvider] for audio frames.
     */
    fun attachFrameSource(player: LavalinkPlayer)

    /**
     * Called when a player is destroyed.
     * The transport should stop frame delivery and clean up resources for this guild.
     */
    fun destroyPlayer(guildId: Long)

    /**
     * Returns whether the transport has an active connection for the given guild.
     */
    fun isConnected(guildId: Long): Boolean

    /**
     * Returns the connection ping in ms for the given guild, or -1 if unavailable.
     */
    fun getPing(guildId: Long): Long

    /**
     * Returns the current voice state info for the given guild, or null if not connected.
     */
    fun getVoiceStateInfo(guildId: Long): VoiceStateInfo?

    /**
     * Shuts down the transport entirely, cleaning up all resources.
     */
    fun shutdown()
}
