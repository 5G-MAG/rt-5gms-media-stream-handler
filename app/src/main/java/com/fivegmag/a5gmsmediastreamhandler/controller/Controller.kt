package com.fivegmag.a5gmsmediastreamhandler.controller

import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest

abstract class Controller {

    lateinit var reportingClientId: String

    abstract fun reset()

    abstract fun resetState()

    abstract fun initialize()

    abstract fun handleTriggerPlayback(playbackRequest: PlaybackRequest)

    fun setReportingClientId(id: String) {
        reportingClientId = id
    }
}