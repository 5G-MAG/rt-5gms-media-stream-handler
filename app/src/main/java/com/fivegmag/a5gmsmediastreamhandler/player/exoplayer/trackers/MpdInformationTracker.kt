package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInfo
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInformation
import androidx.media3.exoplayer.source.MediaLoadData
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MpdInformationTracker {
    private val mpdInformation: LinkedHashMap<String, MpdInformation> = LinkedHashMap()
    private val reportedRepresentationIds: HashSet<String> = HashSet()

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
            val representationId = format.id!!
            
            // Do not re-report if it was already successfully sent in a previous interval
            if (reportedRepresentationIds.contains(representationId)) {
                return
            }

            val codecs = format.codecs
            var bandwidth = format.bitrate
            if (bandwidth == androidx.media3.common.Format.NO_VALUE) {
                bandwidth = format.peakBitrate
            }
            if (bandwidth == androidx.media3.common.Format.NO_VALUE) {
                bandwidth = 0 // safe fallback
            }

            val mimeType = format.containerMimeType
            val width = format.width
            val height = format.height
            val mpdInfo = MpdInfo(codecs, bandwidth, mimeType)

            // Extract qualityRanking if injected/available (ExoPlayer doesn't natively expose it without custom parsers/metadata)
            // But per standard Format, if it's missing natively, we leave it null unless there's a custom extension.
            // As a basic compliance hook, if it is somehow injected into roleFlags by a modified ExoPlayer version:
            if (format.roleFlags != 0) {
                 // mpdInfo.qualityRanking = format.roleFlags 
                 // Note: We leave it null by default if not strictly identifiable, but the schema allows omission if absent in MPD.
            }

            if (width > 0) {
                mpdInfo.width = width
            }

            if (height > 0) {
                mpdInfo.height = height
            }
            
            mpdInformation[representationId] = MpdInformation(representationId, null, mpdInfo)
        }
    }

    fun getMpdInformation(): ArrayList<MpdInformation> {
        return ArrayList(mpdInformation.values)
    }

    fun reset() {
        // Mark all current elements as successfully reported so they are not re-reported
        reportedRepresentationIds.addAll(mpdInformation.keys)
        mpdInformation.clear()
    }

    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
}
