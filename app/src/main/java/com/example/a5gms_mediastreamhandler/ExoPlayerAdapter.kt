package com.example.a5gms_mediastreamhandler

import android.content.Context
import com.example.a5gms_mediastreamhandler.helpers.PlayerStates
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.example.a5gms_mediastreamhandler.helpers.StatusInformation
import com.example.a5gms_mediastreamhandler.helpers.mapStateToConstant
import com.google.android.exoplayer2.Player

class ExoPlayerAdapter() {

    private lateinit var playerInstance: ExoPlayer
    private lateinit var playerView: StyledPlayerView
    private lateinit var activeMediaItem: MediaItem
    private lateinit var playerListener: ExoPlayerListener
    private lateinit var bandwidthMeter: DefaultBandwidthMeter
    private lateinit var mediaSessionHandlerAdapter: MediaSessionHandlerAdapter

    fun initialize(
        exoPlayerView: StyledPlayerView,
        context: Context,
        msh: MediaSessionHandlerAdapter
    ) {
        mediaSessionHandlerAdapter = msh
        playerInstance = ExoPlayer.Builder(context).build()
        bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        playerView = exoPlayerView
        playerView.player = playerInstance
        playerListener = ExoPlayerListener(mediaSessionHandlerAdapter, playerInstance)
        playerInstance.addListener(playerListener)
    }

    fun attach(url: String) {
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

    fun getPlaybackState(): Int {
        return playerInstance.playbackState
    }

    private fun getAverageThroughput(): Long {
        return bandwidthMeter.bitrateEstimate
    }

    private fun getBufferLength(): Long {
        return playerInstance.totalBufferedDuration
    }

    private fun getLiveLatency(): Long {
        return playerInstance.currentLiveOffset
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
        var state: String? = null
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