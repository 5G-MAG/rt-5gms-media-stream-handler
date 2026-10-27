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
    fun configureCmcd(
        schemes: Collection<String>,
        sessionId: String?,
        contentId: String?,
        metricsByScheme: Map<String, List<String>> = emptyMap()
    )
    fun setCmcdUseQueryParameters(useQueryParameters: Boolean)

    /**
     * The part of the media delivery session for which each provisioned CMCD configuration reports (TS 26.510 V19.2.0 table 8.7.3.1-1:
     * reportingStartOffset and reportingDuration), counted from this call. The sid is sent in every request regardless.
     */
    /** Whether the UE is inside the locationFilter of a CMCD configuration; the keys of such a configuration are sent only while it is */
    fun setCmcdLocated(configurationId: String, located: Boolean)

    fun configureCmcdReportingWindows(requests: List<com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest>)
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