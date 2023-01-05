package com.example.a5gms_mediastreamhandler

import android.util.Log
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player

// See https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.Listener.html for possible events
class ExoPlayerListener : Player.Listener {

    object PLAYBACK_STATES {
        const val IDLE = "IDLE"
        const val BUFFERING = "BUFFERING"
        const val ENDED = "ENDED"
        const val READY = "READY"
        const val UNKNOWN = "UNKNOWN"
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val state = when (playbackState) {
            Player.STATE_IDLE -> PLAYBACK_STATES.IDLE
            Player.STATE_BUFFERING -> PLAYBACK_STATES.BUFFERING
            Player.STATE_ENDED -> PLAYBACK_STATES.ENDED
            Player.STATE_READY -> PLAYBACK_STATES.READY
            else -> PLAYBACK_STATES.UNKNOWN
        }
        Log.d("ExoPlayer", "Playback state$state")
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            // Active playback.
        } else {
            // Not playing because playback is paused, ended, suppressed, or the player
            // is buffering, stopped or failed. Check player.getPlayWhenReady,
            // player.getPlaybackState, player.getPlaybackSuppressionReason and
            // player.getPlaybackError for details.
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.d("ExoPlayer", "Error")
    }

}