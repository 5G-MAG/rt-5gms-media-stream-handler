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
    private val currentRepresentationIds = mutableMapOf<Int, String>()
    private val pendingSwitches = mutableMapOf<Int, MutableMap<String, RepresentationSwitch>>()

    fun initialize() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLoadStartedEvent(loadStartedEvent: LoadStartedEvent) {
        val formatId = loadStartedEvent.mediaLoadData.trackFormat?.id ?: return
        val trackType = loadStartedEvent.mediaLoadData.trackType
        
        val currentRepresentationId = currentRepresentationIds[trackType]
        val trackPendingSwitches = pendingSwitches.getOrPut(trackType) { mutableMapOf() }

        if (formatId != currentRepresentationId && !trackPendingSwitches.containsKey(formatId)) {
            val t: String = utils.getCurrentXsDateTime()
            val startTimeMs = loadStartedEvent.mediaLoadData.mediaStartTimeMs
            val mt: String? = if (startTimeMs != C.TIME_UNSET) {
                utils.millisecondsToISO8601(startTimeMs)
            } else {
                val currentPosition = exoPlayerAdapter.getCurrentPosition()
                utils.millisecondsToISO8601(if (currentPosition < 0) 0L else currentPosition)
            }
            trackPendingSwitches[formatId] = RepresentationSwitch(t, mt, formatId)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownstreamFormatChangedEvent(downstreamFormatChangedEvent: DownstreamFormatChangedEvent) {
        val formatId = downstreamFormatChangedEvent.mediaLoadData.trackFormat?.id ?: return
        val trackType = downstreamFormatChangedEvent.mediaLoadData.trackType
        
        val currentRepresentationId = currentRepresentationIds[trackType]
        val trackPendingSwitches = pendingSwitches.getOrPut(trackType) { mutableMapOf() }

        if (formatId != currentRepresentationId) {
            val pendingSwitch = trackPendingSwitches.remove(formatId)
            if (pendingSwitch != null) {
                representationSwitchList.entries.add(pendingSwitch)
            } else {
                val t: String = utils.getCurrentXsDateTime()
                val startTimeMs = downstreamFormatChangedEvent.mediaLoadData.mediaStartTimeMs
                val mt: String? = if (startTimeMs != C.TIME_UNSET) {
                    utils.millisecondsToISO8601(startTimeMs)
                } else {
                    val currentPosition = exoPlayerAdapter.getCurrentPosition()
                    utils.millisecondsToISO8601(if (currentPosition < 0) 0L else currentPosition)
                }
                representationSwitchList.entries.add(RepresentationSwitch(t, mt, formatId))
            }
            currentRepresentationIds[trackType] = formatId
            trackPendingSwitches.clear()
        }
    }

    fun getRepresentationSwitchList(): RepresentationSwitchList {
        return representationSwitchList
    }

    fun reset() {
        representationSwitchList.entries.clear()
        currentRepresentationIds.clear()
        pendingSwitches.clear()
    }

    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
}
