package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.RepresentationSwitch
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.RepresentationSwitchList
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import androidx.media3.exoplayer.source.MediaLoadData
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class RepresentationSwitchTracker(
    private val exoPlayerAdapter: IExoPlayerAdapter,
    private val utils: Utils = Utils()
) {
    private val representationSwitchList: RepresentationSwitchList = RepresentationSwitchList(ArrayList())

    fun initialize() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownstreamFormatChangedEvent(downstreamFormatChangedEvent: DownstreamFormatChangedEvent) {
        addRepresentationSwitch(downstreamFormatChangedEvent.mediaLoadData)
    }

    private fun addRepresentationSwitch(mediaLoadData: MediaLoadData) {
        val t: String = utils.getCurrentXsDateTime()
        val currentPosition = exoPlayerAdapter.getCurrentPosition()
        val mt: String? = utils.millisecondsToISO8601(currentPosition)
        val to: String? = mediaLoadData.trackFormat?.id
        val representationSwitch = to?.let { RepresentationSwitch(t, mt, it) }

        if (representationSwitch != null) {
            representationSwitchList.entries.add(representationSwitch)
        }
    }

    fun getRepresentationSwitchList(): RepresentationSwitchList {
        return representationSwitchList
    }

    fun reset() {
        representationSwitchList.entries.clear()
    }

    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
}
