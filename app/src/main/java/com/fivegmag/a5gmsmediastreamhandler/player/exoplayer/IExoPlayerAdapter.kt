package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

interface IExoPlayerAdapter {

    /** The event bus of this instance: the events of its player reach only the components of the same instance */
    val eventBus: org.greenrobot.eventbus.EventBus

    fun initialize(
        exoPlayerView: PlayerView,
        context: Context
    )

    fun attach(url: String, contentType: String = "")

    /**
     * CMCD (TS 26.512 V19.3.0 clause 10.5.1): only the classes of the CMCD metrics schemes in the client metrics reporting
     * configurations are reported, with the media delivery session identifier as sid; none without such a scheme. The default
     * transmission mode is HTTP request headers (clause 7.8, the Media Session Handler); the application may use query parameters.
     */
    fun configureCmcd(schemes: Collection<String>, sessionId: String?, contentId: String?)
    fun setCmcdUseQueryParameters(useQueryParameters: Boolean)
    fun hasActiveMediaItem() : Boolean
    fun getCurrentManifestUri(): String
    fun getCurrentManifestUrl(): String
    fun preload()
    fun play()
    fun pause()
    fun seek(time: Long)
    fun stop()
    fun reset()
    fun destroy()
    fun getPlayerInstance(): ExoPlayer
    fun getPlaybackState(): Int
    fun getCurrentPosition(): Long
    fun getBufferLength(): Long
    fun getAverageThroughput(): Long
    fun getCurrentPeriodId(): String
    fun getStatusInformation(status: String): Any?
    fun getPlayerState(): String

    // Device information for QoE metrics
    fun getVideoWidth(): Int
    fun getVideoHeight(): Int
    fun getScreenWidth(): Int
    fun getScreenHeight(): Int
    fun getPixelDensityX(): Float
    fun getPixelDensityY(): Float
}