/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2023 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsmediastreamhandler

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.helpers.MetricReportingSchemes
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.StatusInformation
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitchList
import com.fivegmag.a5gmsmediastreamhandler.helpers.mapStateToConstant


@UnstableApi
class ExoPlayerAdapter() {

    private val TAG: String = "ExoPlayerAdapter"
    private lateinit var playerInstance: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var activeMediaItem: MediaItem
    private lateinit var playerListener: ExoPlayerListener
    private lateinit var bandwidthMeter: DefaultBandwidthMeter
    private lateinit var mediaSessionHandlerAdapter: MediaSessionHandlerAdapter
    private lateinit var playbackStatsListener: PlaybackStatsListener
    private var supportedMetricsSchemes =
        listOf(
            MetricReportingSchemes.FIVE_G_MAG_EXOPLAYER_COMBINED_PLAYBACK_STATS,
            MetricReportingSchemes.THREE_GPP_DASH_METRIC_REPORTING
        )

    var httpDataSourceFactory: HttpDataSource.Factory =
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

    val dataSourceFactory =
        DataSource.Factory {
            val dataSource = httpDataSourceFactory.createDataSource()
            dataSource
        }

    fun initialize(
        exoPlayerView: PlayerView,
        context: Context,
        msh: MediaSessionHandlerAdapter
    ) {
        mediaSessionHandlerAdapter = msh
        playerInstance = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .build()
        playerInstance.addAnalyticsListener(EventLogger())
        bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        playerView = exoPlayerView
        playerView.player = playerInstance
        playerListener = ExoPlayerListener(mediaSessionHandlerAdapter, playerInstance, playerView)
        playbackStatsListener = PlaybackStatsListener(true) {
                _: AnalyticsListener.EventTime?,
                _: PlaybackStats?,
            -> // Analytics data for the session started at `eventTime` is ready.
        }
        playerInstance.addAnalyticsListener(playerListener)
        playerInstance.addAnalyticsListener(playbackStatsListener)
    }

    fun attach(url: String) {
        playerListener.reset()
        val mediaItem: MediaItem = MediaItem.fromUri(url)
        playerInstance.setMediaItem(mediaItem)
        activeMediaItem = mediaItem
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

    fun getCurrentManifestUrl() : String {
        return playerInstance.currentMediaItem?.localConfiguration?.uri.toString()
    }

    fun getPlaybackState(): Int {
        return playerInstance.playbackState
    }

    fun getRepresentationSwitchList(): RepresentationSwitchList {
        return playerListener.getRepresentationSwitchList()
    }

    fun getHttpList(): HttpList {
        return playerListener.getHttpList()
    }

    fun resetListenerValues() {
        playerListener.reset()
    }

    private fun getAverageThroughput(): Long {
        return bandwidthMeter.bitrateEstimate
    }

    fun getBufferLength(): Long {
        return playerInstance.totalBufferedDuration
    }

    private fun getLiveLatency(): Long {
        return playerInstance.currentLiveOffset
    }

    fun getCombinedPlaybackStats(): PlaybackStats {
        return playbackStatsListener.combinedPlaybackStats
    }

    fun getPlaybackStats(): PlaybackStats? {
        return playbackStatsListener.playbackStats
    }

    fun isMetricsSchemeSupported(metricsScheme: String): Boolean {
        Log.d(TAG, "Checking if metrics scheme $metricsScheme is supported")
        return supportedMetricsSchemes.contains(metricsScheme)
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