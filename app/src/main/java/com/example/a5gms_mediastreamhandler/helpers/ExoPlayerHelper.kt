package com.example.a5gms_mediastreamhandler.helpers

import com.example.a5gmscommonlibrary.helpers.PlayerStates
import com.google.android.exoplayer2.Player

fun mapStateToConstant(playbackState: Int) : String {
    val state = when (playbackState) {
        Player.STATE_IDLE -> PlayerStates.IDLE
        Player.STATE_BUFFERING -> PlayerStates.BUFFERING
        Player.STATE_ENDED -> PlayerStates.ENDED
        Player.STATE_READY -> PlayerStates.READY
        else -> PlayerStates.UNKNOWN
    }

    return state
}