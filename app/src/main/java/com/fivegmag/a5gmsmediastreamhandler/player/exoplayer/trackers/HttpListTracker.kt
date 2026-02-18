package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import com.fivegmag.a5gmscommonlibrary.eventbus.LoadCompletedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.HttpListEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.HttpListEntryType
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.Trace
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class HttpListTracker(
    private val utils: Utils = Utils()
) {
    private val httpList: HttpList = HttpList(ArrayList())

    fun initialize() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLoadCompleted(loadCompletedEvent: LoadCompletedEvent) {
        addHttpListEntry(loadCompletedEvent.mediaLoadData, loadCompletedEvent.loadEventInfo)
    }

    private fun addHttpListEntry(mediaLoadData: MediaLoadData, loadEventInfo: LoadEventInfo) {
        val tcpId = null
        val type = getRequestType(mediaLoadData)
        val url = loadEventInfo.uri.toString()
        val actualUrl = loadEventInfo.uri.toString()
        val range = ""
        val tRequest =
            utils.convertTimestampToXsDateTime(utils.getCurrentTimestamp() - loadEventInfo.loadDurationMs)
        val tResponse =
            utils.convertTimestampToXsDateTime(utils.getCurrentTimestamp() - loadEventInfo.loadDurationMs)
        val responseCode = 200
        val interval = loadEventInfo.loadDurationMs.toInt()
        val bytes = loadEventInfo.bytesLoaded.toInt()
        val trace = Trace(
            tResponse,
            loadEventInfo.loadDurationMs,
            bytes
        )
        val traceList = ArrayList<Trace>()
        traceList.add(trace)
        val httpListEntry = HttpListEntry(
            tcpId,
            type,
            url,
            actualUrl,
            range,
            tRequest,
            tResponse,
            responseCode,
            interval,
            traceList
        )

        httpList.entries.add(httpListEntry)
    }

    private fun getRequestType(mediaLoadData: MediaLoadData): String {
        return when (mediaLoadData.dataType) {
            androidx.media3.common.C.DATA_TYPE_UNKNOWN -> HttpListEntryType.OTHER.value
            androidx.media3.common.C.DATA_TYPE_MEDIA -> HttpListEntryType.MEDIA_SEGMENT.value
            androidx.media3.common.C.DATA_TYPE_MEDIA_INITIALIZATION -> HttpListEntryType.INITIALIZATION_SEGMENT.value
            androidx.media3.common.C.DATA_TYPE_MANIFEST -> HttpListEntryType.MPD.value
            else -> HttpListEntryType.OTHER.value

        }
    }

    fun getHttpList(): HttpList {
        return httpList
    }

    fun reset() {
        httpList.entries.clear()
    }

    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
}
