package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

interface IExoPlayerAdapter {

    fun initialize(
        exoPlayerView: PlayerView,
        context: Context
    )

    fun attach(url: String, contentType: String = "")
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