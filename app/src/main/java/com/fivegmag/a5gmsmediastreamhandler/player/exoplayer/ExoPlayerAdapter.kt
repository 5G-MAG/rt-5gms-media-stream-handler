package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.CmcdConfiguration.MODE_QUERY_PARAMETER
import androidx.media3.exoplayer.upstream.CmcdConfiguration.MODE_REQUEST_HEADER
import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.StatusInformation
import com.fivegmag.a5gmscommonlibrary.helpers.UserAgentTokens
import java.util.UUID

@UnstableApi
class ExoPlayerAdapter: IExoPlayerAdapter {
    override val eventBus: org.greenrobot.eventbus.EventBus = org.greenrobot.eventbus.EventBus.builder().build()
    private lateinit var playerInstance: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var context: Context
    private var activeMediaItem: MediaItem? = null
    private lateinit var activeManifestUrl: String
    private lateinit var playerListener: ExoPlayerListener
    private lateinit var bandwidthMeter: DefaultBandwidthMeter

    companion object {
        // the keys of the provisioned schemes, plus sid: every M4d request cites the media delivery session identifier
        // in the CMCD-Session header (TS 26.512 clauses 10.1A and 6.2.3.6), also when no CMCD scheme is provisioned
        fun getCmcdAllowedKeys(schemes: Collection<String>, sessionId: String?): Set<String> {
            val keys = HashSet<String>()
            for (scheme in schemes) {
                CmcdSchemes.KEYS[scheme]?.let { keys.addAll(it) }
            }
            if (keys.isNotEmpty() || sessionId != null) keys.add("sid")
            return keys
        }
    }

    // the CMCD keys that the provisioned schemes allow, the sid and cid of the media delivery session and the transmission mode
    @Volatile private var cmcdAllowedKeys: Set<String> = emptySet()
    @Volatile private var cmcdSessionId: String? = null
    @Volatile private var cmcdContentId: String? = null
    @Volatile private var cmcdUseQueryParameters = false

    override fun configureCmcd(schemes: Collection<String>, sessionId: String?, contentId: String?) {
        cmcdAllowedKeys = getCmcdAllowedKeys(schemes, sessionId)
        cmcdSessionId = sessionId
        cmcdContentId = contentId
    }

    override fun setCmcdUseQueryParameters(useQueryParameters: Boolean) {
        cmcdUseQueryParameters = useQueryParameters
    }


    override fun initialize(
        exoPlayerView: PlayerView,
        context: Context
    ) {
        val defaultUserAgent = Util.getUserAgent(context, "A5GMSMediaStreamHandler")
        val deviceName = android.os.Build.MODEL
        val osVersion = android.os.Build.VERSION.RELEASE
        val modifiedUserAgent =
            "${UserAgentTokens.FIVE_G_MS_REL_17_MEDIA_STREAM_HANDLER} $defaultUserAgent (Android $osVersion; $deviceName)"
        val httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(modifiedUserAgent)

        // transferListenerRef is captured by the factory lambda and set after playerListener is created.
        // DataSources are created lazily when media loads, so the listener is guaranteed to be set by then.
        var transferListenerRef: ExoPlayerListener? = null
        val dataSourceFactory =
            DataSource.Factory {
                val dataSource = httpDataSourceFactory.createDataSource()
                transferListenerRef?.let { dataSource.addTransferListener(it) }
                dataSource
            }

        val cmcdConfigurationFactory = object : CmcdConfiguration.Factory {
            override fun createCmcdConfiguration(mediaItem: MediaItem): CmcdConfiguration {
                val cmcdConfig = object : CmcdConfiguration.RequestConfig {
                    override fun isKeyAllowed(key: String): Boolean {
                        if (key == "cid" && cmcdContentId == null) return false
                        return cmcdAllowedKeys.contains(key)
                    }

                    override fun getRequestedMaximumThroughputKbps(throughputKbps: Int): Int {
                        return 5 * throughputKbps
                    }
                }

                // the media delivery session identifier is the CMCD sid (clause 10.5.1); the Media Session Handler gives it before playback
                val sessionId = cmcdSessionId ?: UUID.randomUUID().toString()
                val mode = if (cmcdUseQueryParameters) MODE_QUERY_PARAMETER else MODE_REQUEST_HEADER

                return CmcdConfiguration(sessionId, cmcdContentId, cmcdConfig, mode)
            }
        }

        playerInstance = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
                    .setCmcdConfigurationFactory(cmcdConfigurationFactory)

            )
            .build()

        playerInstance.addAnalyticsListener(EventLogger())
        bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        this.context = context
        playerView = exoPlayerView
        playerView.player = playerInstance
        playerListener =
            ExoPlayerListener(playerInstance, playerView, eventBus)
        playerInstance.addAnalyticsListener(playerListener)
        transferListenerRef = playerListener
    }

    override fun attach(url: String, contentType: String) {
        // Reset listener state for new presentation
        playerListener.newPlaybackSession()
        
        val mediaItem: MediaItem
        when (contentType) {
            ContentTypes.DASH -> {
                mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
            }

            ContentTypes.HLS -> {
                mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            }

            else -> {
                mediaItem = MediaItem.fromUri(url)
            }
        }

        playerInstance.setMediaItem(mediaItem)
        activeMediaItem = mediaItem
        activeManifestUrl = url
    }

    override fun hasActiveMediaItem() : Boolean {
        return activeMediaItem != null
    }

    override fun getCurrentManifestUri(): String {
        return activeManifestUrl
    }

    override fun getCurrentManifestUrl(): String {
        return playerInstance.currentMediaItem?.localConfiguration?.uri.toString()
    }

    override fun preload() {
        playerInstance.prepare()
    }

    override fun play() {
        playerInstance.play()
    }

    override fun pause() {
        playerInstance.pause()
    }

    override fun seek(time: Long) {
        TODO("Not yet implemented")
    }

    override fun stop() {
        playerInstance.stop()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun destroy() {
        playerInstance.release()
    }

    override fun getPlayerInstance(): ExoPlayer {
        return playerInstance
    }

    override fun getPlaybackState(): Int {
        return playerInstance.playbackState
    }

    override fun getCurrentPosition(): Long {
        return playerInstance.currentPosition
    }

    override fun getBufferLength(): Long {
        return playerInstance.totalBufferedDuration
    }
    override fun getAverageThroughput(): Long {
        return bandwidthMeter.bitrateEstimate
    }

    private fun getLiveLatency(): Long {
        return playerInstance.currentLiveOffset
    }

    override fun getCurrentPeriodId(): String {
        try {
            val manifest = playerInstance.currentManifest
            if (manifest is DashManifest) {
                val periodId = manifest.getPeriod(playerInstance.currentPeriodIndex).id
                if (periodId != null) {
                    return periodId
                }
            }
        } catch (e: Exception) {
            // Manifest not loaded yet or period index out of bounds
            return ""
        }
        return ""
    }

    override fun getStatusInformation(status: String): Any? {
        when (status) {
            StatusInformation.AVERAGE_THROUGHPUT -> return getAverageThroughput()
            StatusInformation.BUFFER_LENGTH -> return getBufferLength()
            StatusInformation.LIVE_LATENCY -> return getLiveLatency()
            else -> {
                return null
            }
        }
    }

    override fun getPlayerState(): String {
        val state: String?
        if (playerInstance.isPlaying) {
            state = PlayerStates.PLAYING
        } else if (playerInstance.playbackState == Player.STATE_READY && !playerInstance.playWhenReady) {
            state = PlayerStates.PAUSED
        } else {
            state = mapStateToConstant(playerInstance.playbackState)
        }

        return state
    }

    override fun getVideoWidth(): Int {
        // Return displayed video width (rendered size), not encoded resolution
        return playerView.width
    }

    override fun getVideoHeight(): Int {
        // Return displayed video height (rendered size), not encoded resolution
        return playerView.height
    }

    override fun getScreenWidth(): Int {
        return context.resources.displayMetrics.widthPixels
    }

    override fun getScreenHeight(): Int {
        return context.resources.displayMetrics.heightPixels
    }

    override fun getPixelDensityX(): Float {
        return context.resources.displayMetrics.xdpi
    }

    override fun getPixelDensityY(): Float {
        return context.resources.displayMetrics.ydpi
    }
}