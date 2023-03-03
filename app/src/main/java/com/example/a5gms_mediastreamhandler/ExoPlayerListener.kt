package com.example.a5gms_mediastreamhandler

import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player

import com.example.a5gms_mediastreamhandler.helpers.mapStateToConstant
import com.example.a5gmscommonlibrary.helpers.PlayerStates

// See https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.Listener.html for possible events
class ExoPlayerListener(
    private val mediaSessionHandlerAdapter: MediaSessionHandlerAdapter,
    private val playerInstance: ExoPlayer
) :
    Player.Listener {

    override fun onPlaybackStateChanged(playbackState: Int) {
        val state : String = mapStateToConstant(playbackState)
        mediaSessionHandlerAdapter.updatePlaybackState(state)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        var state: String? = null
        if (isPlaying) {
            state = PlayerStates.PLAYING
        } else if (playerInstance.playbackState == Player.STATE_READY && !playerInstance.playWhenReady) {
            state = PlayerStates.PAUSED
        }
        if (state != null) {
            mediaSessionHandlerAdapter.updatePlaybackState(state)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.d("ExoPlayer", "Error")
    }

}