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
     * What the playback request carries for CMCD besides the configurations: the client-data-reporting-mode of the 3GPP Service URL and the client
     * data reporting of the Media Player Entry (clause 10.5.1). The mode of the URL takes precedence over the setting of the application, and that
     * over the entry.
     */
    fun configureCmcdMediaPlayerEntry(
        entries: List<com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry>,
        clientDataReportingMode: String?,
        context: com.fivegmag.a5gmscommonlibrary.session.MediaPlayerEntryContext? = null
    )

    /** serviceDescriptions[] of the settings API (clause 13.2.4): they define or modify service descriptions for the next playback */
    fun setServiceDescriptions(descriptions: List<ServiceDescriptionSettings>)

    /** serviceDescriptionId of the settings API (clause 13.2.4): the service description that applies to the next playback; null: none selected */
    fun setServiceDescriptionId(id: Long?)

    /**
     * The cmcdReporting setting of the configuration and settings API (TS 26.512 V19.3.0 clause 13.2.4): keys that are reported in addition to
     * those of the client metrics reporting configurations (clause 10.5.1: the Media Player reports the union), the content identifier and
     * the transmission mode. The sid is that of the media delivery session. Null values leave the setting out.
     */
    fun setCmcdReporting(keys: List<String>?, contentId: String?, useQueryParameters: Boolean?)

    /**
     * The part of the media delivery session for which each provisioned CMCD configuration reports (TS 26.510 V19.2.0 table 8.7.3.1-1:
     * reportingStartOffset and reportingDuration), counted from this call. The sid is sent in every request regardless.
     */
    /** Whether the UE is inside the locationFilter of a CMCD configuration; the keys of such a configuration are sent only while it is */
    fun setCmcdLocated(configurationId: String, located: Boolean)

    /**
     * The client metrics reporting configurations changed during the session (TS 26.510 V19.2.0 clause 5.3.5.1): the keys, the windows and the
     * locationFilters follow them from the next request on. The media delivery session identifier, the content identifier, the start of the
     * session and what is known of the location stay.
     */
    fun updateCmcdConfiguration(
        schemes: Collection<String>,
        metricsByScheme: Map<String, List<String>>,
        requests: List<com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest>
    )

    fun configureCmcdReportingWindows(requests: List<com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest>)
    /** The content identifier that the 5GMS Client knows for the media being consumed, if any (the one that populates the CMCD cid key) */
    fun getContentIdentifier(): String?
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
    /** The buffer length in milliseconds, the unit of the BufferLevel metric; getStatusInformation(BUFFER_LENGTH) gives seconds */
    fun getBufferLength(): Long
    fun getAverageThroughput(): Long
    fun getCurrentPeriodId(): String
    fun getStatusInformation(status: String): Any?
    fun getPlayerState(): String

    /** The state of the Media Player of TS 26.512 V19.3.0 table 13.2.2-1 (the method getPlayerState gives the state of the playback of the library) */
    fun getMediaPlayerState(): String

    /** The notifications of the Media Player (clause 13.2.5) go to the listeners, with the media delivery session identifier */
    fun addNotificationListener(listener: MediaPlayerNotificationListener)
    fun removeNotificationListener(listener: MediaPlayerNotificationListener)

    // Device information for QoE metrics
    fun getVideoWidth(): Int
    fun getVideoHeight(): Int
    fun getScreenWidth(): Int
    fun getScreenHeight(): Int
    fun getPixelDensityX(): Float
    fun getPixelDensityY(): Float

    /** The refresh rate of the display in Hz, 0 if the platform gives none (VrDeviceInformation, TS 26.118 V19.0.0 clause 9.3.4) */
    fun getDisplayRefreshRate(): Float
}