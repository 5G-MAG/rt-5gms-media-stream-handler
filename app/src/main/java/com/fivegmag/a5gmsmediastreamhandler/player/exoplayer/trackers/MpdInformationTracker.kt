package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInfo
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInformation
import androidx.media3.exoplayer.source.MediaLoadData
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MpdInformationTracker {
    private val mpdInformation: ArrayList<MpdInformation> = ArrayList()

    fun initialize() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownstreamFormatChangedEvent(downstreamFormatChangedEvent: DownstreamFormatChangedEvent) {
        addMpdInformation(downstreamFormatChangedEvent.mediaLoadData)
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun addMpdInformation(mediaLoadData: MediaLoadData) {
        val format = mediaLoadData.trackFormat
        if (format != null && format.id != null) {
            val representationId = mediaLoadData.trackFormat!!.id
            val codecs = mediaLoadData.trackFormat!!.codecs
            val bandwidth = mediaLoadData.trackFormat!!.peakBitrate
            val mimeType = mediaLoadData.trackFormat!!.containerMimeType
            val frameRate = mediaLoadData.trackFormat!!.frameRate
            val width = mediaLoadData.trackFormat!!.width
            val height = mediaLoadData.trackFormat!!.height
            val mpdInfo = MpdInfo(codecs, bandwidth, mimeType)

            if (frameRate > 0) {
                mpdInfo.frameRate = frameRate.toDouble()
            }
            if (width > 0) {
                mpdInfo.width = width
            }

            if (height > 0) {
                mpdInfo.height = height
            }
            mpdInformation.add(MpdInformation(representationId, null, mpdInfo))
        }
    }

    fun getMpdInformation(): ArrayList<MpdInformation> {
        return mpdInformation
    }

    fun reset() {
        mpdInformation.clear()
    }

    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
}
