package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import androidx.media3.common.C
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadStartedEvent
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
    private var currentRepresentationId: String? = null
    private val pendingSwitches = mutableMapOf<String, RepresentationSwitch>()

    fun initialize() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLoadStartedEvent(loadStartedEvent: LoadStartedEvent) {
        val formatId = loadStartedEvent.mediaLoadData.trackFormat?.id ?: return
        
        if (formatId != currentRepresentationId && !pendingSwitches.containsKey(formatId)) {
            val t: String = utils.getCurrentXsDateTime()
            val startTimeMs = loadStartedEvent.mediaLoadData.mediaStartTimeMs
            val mt: String? = if (startTimeMs != C.TIME_UNSET) {
                utils.millisecondsToISO8601(startTimeMs)
            } else {
                null
            }
            pendingSwitches[formatId] = RepresentationSwitch(t, mt, formatId)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownstreamFormatChangedEvent(downstreamFormatChangedEvent: DownstreamFormatChangedEvent) {
        val formatId = downstreamFormatChangedEvent.mediaLoadData.trackFormat?.id ?: return
        
        if (formatId != currentRepresentationId) {
            val pendingSwitch = pendingSwitches.remove(formatId)
            if (pendingSwitch != null) {
                representationSwitchList.entries.add(pendingSwitch)
            } else {
                val t: String = utils.getCurrentXsDateTime()
                val startTimeMs = downstreamFormatChangedEvent.mediaLoadData.mediaStartTimeMs
                val mt: String? = if (startTimeMs != C.TIME_UNSET) {
                    utils.millisecondsToISO8601(startTimeMs)
                } else {
                    null
                }
                representationSwitchList.entries.add(RepresentationSwitch(t, mt, formatId))
            }
            currentRepresentationId = formatId
            pendingSwitches.clear()
        }
    }

    fun getRepresentationSwitchList(): RepresentationSwitchList {
        return representationSwitchList
    }

    fun reset() {
        representationSwitchList.entries.clear()
        currentRepresentationId = null
        pendingSwitches.clear()
    }

    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
}
