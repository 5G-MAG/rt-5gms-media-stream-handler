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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.StatusInformation
import com.fivegmag.a5gmscommonlibrary.helpers.UserAgentTokens

@UnstableApi
class ExoPlayerAdapter() {

    private lateinit var playerInstance: ExoPlayer
    private lateinit var playerView: PlayerView
    private var activeMediaItem: MediaItem? = null
    private lateinit var activeManifestUrl: String
    private lateinit var playerListener: ExoPlayerListener
    private lateinit var bandwidthMeter: DefaultBandwidthMeter


    fun initialize(
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
        val dataSourceFactory =
            DataSource.Factory {
                val dataSource = httpDataSourceFactory.createDataSource()
                dataSource
            }
        playerInstance = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .build()
        playerInstance.addAnalyticsListener(EventLogger())
        bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        playerView = exoPlayerView
        playerView.player = playerInstance
        playerListener =
            ExoPlayerListener(playerInstance, playerView)
        playerInstance.addAnalyticsListener(playerListener)
    }

    fun attach(url: String, contentType: String = "") {
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

    fun hasActiveMediaItem() : Boolean {
        return activeMediaItem != null
    }

    fun getCurrentManifestUri(): String {
        return activeManifestUrl
    }

    fun getCurrentManifestUrl(): String {
        return playerInstance.currentMediaItem?.localConfiguration?.uri.toString()
    }

    fun preload() {
        playerInstance.prepare()
    }

    fun play() {
        playerInstance.play()
    }

    fun pause() {
        playerInstance.pause()
    }

    fun seek(time: Long) {
        TODO("Not yet implemented")
    }

    fun stop() {
        playerInstance.stop()
    }

    fun reset() {
        TODO("Not yet implemented")
    }

    fun destroy() {
        playerInstance.release()
    }

    fun getPlayerInstance(): ExoPlayer {
        return playerInstance
    }

    fun getPlaybackState(): Int {
        return playerInstance.playbackState
    }

    fun getCurrentPosition(): Long {
        return playerInstance.currentPosition
    }

    fun getBufferLength(): Long {
        return playerInstance.totalBufferedDuration
    }
    fun getAverageThroughput(): Long {
        return bandwidthMeter.bitrateEstimate
    }

    private fun getLiveLatency(): Long {
        return playerInstance.currentLiveOffset
    }

    fun getCurrentPeriodId(): String {
        val dashManifest = playerInstance.currentManifest as DashManifest
        val periodId = dashManifest.getPeriod(playerInstance.currentPeriodIndex).id

        if (periodId != null) {
            return periodId
        }

        return ""
    }

    fun getStatusInformation(status: String): Any? {
        when (status) {
            StatusInformation.AVERAGE_THROUGHPUT -> return getAverageThroughput()
            StatusInformation.BUFFER_LENGTH -> return getBufferLength()
            StatusInformation.LIVE_LATENCY -> return getLiveLatency()
            else -> {
                return null
            }
        }
    }

    fun getPlayerState(): String {
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
}