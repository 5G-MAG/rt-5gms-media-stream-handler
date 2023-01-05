package com.example.a5gms_mediastreamhandler

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView

class ExoplayerAdapter {

    private lateinit var playerInstance : ExoPlayer
    private lateinit var playerView : StyledPlayerView

    fun initialize(exoPlayerView: StyledPlayerView, context: Context) {
        playerInstance = ExoPlayer.Builder(context).build()
        playerView = exoPlayerView
        playerView.player = playerInstance
    }

    fun attach(url: String) {
        val mediaItem: MediaItem = MediaItem.fromUri(url)
        playerInstance.addMediaItem(mediaItem)
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

    fun getPlayerInstance() : ExoPlayer {
        return playerInstance
    }

}