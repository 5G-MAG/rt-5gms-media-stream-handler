package com.fivegmag.a5gmsmediastreamhandler.controller

import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest

interface Controller {

    fun reset()

    fun resetState()

    fun initialize()

    fun handleTriggerPlayback(playbackRequest: PlaybackRequest)

}