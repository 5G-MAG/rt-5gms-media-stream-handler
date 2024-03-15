package com.fivegmag.a5gmsmediastreamhandler.controller

import android.os.Message
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

interface IQoEMetricsReportingController : IController {

    fun triggerQoeMetricsReports()

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent)
    fun handleGetQoeMetricsReport(msg: Message)
}