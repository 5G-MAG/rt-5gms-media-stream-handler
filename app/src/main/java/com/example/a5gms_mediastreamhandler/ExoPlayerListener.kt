package com.example.a5gms_mediastreamhandler

import android.util.Log
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException

// See https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.Listener.html for possible events
class ExoPlayerListener : Player.Listener {

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            Log.d("ExoPlayer","Is Playing")
        } else {
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val cause = error.cause
        if (cause is HttpDataSourceException) {
            // An HTTP error occurred.
            // It's possible to find out more about the error both by casting and by
            // querying the cause.
            if (cause is InvalidResponseCodeException) {
                // Cast to InvalidResponseCodeException and retrieve the response code,
                // message and headers.
            } else {
                // Try calling httpError.getCause() to retrieve the underlying cause,
                // although note that it may be null.
            }
        }
    }

}