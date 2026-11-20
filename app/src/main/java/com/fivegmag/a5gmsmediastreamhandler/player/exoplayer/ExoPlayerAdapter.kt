package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.CmcdConfiguration.MODE_REQUEST_HEADER
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.StatusInformation
import com.fivegmag.a5gmscommonlibrary.helpers.UserAgentTokens
import java.util.UUID

@UnstableApi
class ExoPlayerAdapter : IExoPlayerAdapter {
    private lateinit var playerInstance: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var context: Context
    private var mediaDeliverySessionId: String? = null
    private var released = false
    private var activeMediaItem: MediaItem? = null
    private var activeManifestUrl: String = ""
    private lateinit var playerListener: ExoPlayerListener
    private lateinit var bandwidthMeter: DefaultBandwidthMeter

    override fun initialize(exoPlayerView: PlayerView, context: Context) {
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
                addMediaDeliverySessionHeader(dataSource)
                transferListenerRef?.let { dataSource.addTransferListener(it) }
                dataSource
            }

        val cmcdConfigurationFactory = object : CmcdConfiguration.Factory {
            override fun createCmcdConfiguration(mediaItem: MediaItem): CmcdConfiguration {
                val cmcdConfig = object : CmcdConfiguration.RequestConfig {
                    override fun isKeyAllowed(key: String): Boolean = true

                    override fun getRequestedMaximumThroughputKbps(throughputKbps: Int): Int = 5 * throughputKbps
                }

                val contentId = UUID.randomUUID().toString()

                return CmcdConfiguration(mediaDeliverySessionId, contentId, cmcdConfig, MODE_REQUEST_HEADER)
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
            ExoPlayerListener(playerInstance, playerView)
        playerInstance.addAnalyticsListener(playerListener)
        transferListenerRef = playerListener
    }

    private fun addMediaDeliverySessionHeader(dataSource: HttpDataSource) {
        /*
            Sends the media delivery session UUID in the CMCD-Session header
            TS 26.512 Rel-18, 10.1A
            TS 26.512 Rel-18, 6.2.3.6
         */
        val sessionId = mediaDeliverySessionId ?: return
        dataSource.setRequestProperty("CMCD-Session", "sid=\"$sessionId\"")
    }

    override fun attach(url: String, contentType: String, mediaDeliverySessionId: String, playbackId: String) {
        this.mediaDeliverySessionId = mediaDeliverySessionId

        // Reset listener state for new presentation
        playerListener.newPlaybackSession()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(playbackId)
        when (contentType) {
            ContentTypes.DASH -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }

            ContentTypes.HLS -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }

        val mediaItem = mediaItemBuilder.build()
        playerInstance.setMediaItem(mediaItem)
        activeMediaItem = mediaItem
        activeManifestUrl = url
    }

    /**
     * True while a media item is attached, even after [destroy], so callers can still send the
     * final reports for it. [reset] clears it.
     */
    override fun hasActiveMediaItem(): Boolean = activeMediaItem != null

    override fun getCurrentManifestUri(): String = activeManifestUrl

    override fun getCurrentManifestUrl(): String =
        playerInstance.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()

    override fun preload() {
        playerInstance.prepare()
    }

    override fun play() {
        playerInstance.play()
    }

    override fun pause() {
        playerInstance.pause()
    }

    /**
     * Seeks to the given position in the current media item.
     *
     * @param time Position in milliseconds
     */
    override fun seek(time: Long) {
        playerInstance.seekTo(time)
    }

    override fun stop() {
        if (released) {
            return
        }
        playerInstance.stop()
    }

    /**
     * Stops playback, removes the active media item and restores the default playback state, so
     * the next media item only starts after [play] and at normal speed. The player instance is
     * kept and can be reused with [attach]. Use [destroy] to release it.
     */
    override fun reset() {
        mediaDeliverySessionId = null
        activeMediaItem = null
        activeManifestUrl = ""
        if (released) {
            return
        }
        playerInstance.stop()
        playerInstance.clearMediaItems()
        playerInstance.playWhenReady = false
        playerInstance.playbackParameters = PlaybackParameters.DEFAULT
        playerListener.newPlaybackSession()
    }

    override fun destroy() {
        if (released) {
            return
        }
        released = true
        playerInstance.release()
    }

    override fun getPlayerInstance(): ExoPlayer = playerInstance

    override fun getPlaybackState(): Int = playerInstance.playbackState

    override fun getCurrentPosition(): Long = playerInstance.currentPosition

    override fun getBufferLength(): Long = playerInstance.totalBufferedDuration
    override fun getAverageThroughput(): Long = bandwidthMeter.bitrateEstimate

    private fun getLiveLatency(): Long = playerInstance.currentLiveOffset

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

    override fun getScreenWidth(): Int = context.resources.displayMetrics.widthPixels

    override fun getScreenHeight(): Int = context.resources.displayMetrics.heightPixels

    override fun getPixelDensityX(): Float = context.resources.displayMetrics.xdpi

    override fun getPixelDensityY(): Float = context.resources.displayMetrics.ydpi
}
