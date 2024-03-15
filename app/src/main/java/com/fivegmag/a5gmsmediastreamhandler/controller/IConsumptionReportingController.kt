package com.fivegmag.a5gmsmediastreamhandler.controller

import android.os.Message
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

interface IConsumptionReportingController : IController {
    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent)

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onCellInfoUpdatedEvent(event: CellInfoUpdatedEvent)
    fun triggerConsumptionReport()
    fun updateLastConsumptionRequest(msg: Message)
    fun handleGetConsumptionReport(msg: Message)
}