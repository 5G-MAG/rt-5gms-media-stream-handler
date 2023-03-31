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
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.Player

import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.StatusInformation
import com.fivegmag.a5gmsmediastreamhandler.helpers.mapStateToConstant


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