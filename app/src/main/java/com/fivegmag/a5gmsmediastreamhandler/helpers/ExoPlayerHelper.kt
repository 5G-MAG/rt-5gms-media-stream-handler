package com.fivegmag.a5gmsmediastreamhandler.helpers

import androidx.media3.common.Player
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates

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