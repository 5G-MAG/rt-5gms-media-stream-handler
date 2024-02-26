package com.fivegmag.a5gmsmediastreamhandler.qoeMetricsReporting

import android.annotation.SuppressLint
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadCompletedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.Metrics
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.helpers.XmlSchemaStrings
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.HttpListEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.HttpListEntryType
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInfo
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInformation
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.ReceptionReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.RepresentationSwitch
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.RepresentationSwitchList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.Trace
import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fivegmag.a5gmscommonlibrary.helpers.MetricReportingSchemes.THREE_GPP_DASH_METRIC_REPORTING
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.Exception

@UnstableApi
class QoeMetricsReporterExoplayer(
    private val exoPlayerAdapter: ExoPlayerAdapter
) : QoeMetricsReporter {
    private val representationSwitchList: RepresentationSwitchList = RepresentationSwitchList(
        ArrayList<RepresentationSwitch>()
    )
    private val httpList: HttpList = HttpList(ArrayList<HttpListEntry>())
    private val bufferLevel: BufferLevel = BufferLevel(ArrayList<BufferLevelEntry>())
    private val mpdInformation: ArrayList<MpdInformation> = ArrayList()
    private val utils: Utils = Utils()

    companion object {
        const val TAG = "5GMS-QoeMetricsReporterExoplayer"
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownstreamFormatChangedEvent(downstreamFormatChangedEvent: DownstreamFormatChangedEvent) {
        val isRepresentationSwitchAdded =
            addRepresentationSwitch(downstreamFormatChangedEvent.mediaLoadData)

        if (isRepresentationSwitchAdded) {
            addMpdInformation(downstreamFormatChangedEvent.mediaLoadData)
        }
    }

    private fun addRepresentationSwitch(mediaLoadData: MediaLoadData): Boolean {
        val t: String = utils.getCurrentXsDateTime()
        val currentPosition = exoPlayerAdapter.getCurrentPosition()
        val mt: String? = utils.millisecondsToISO8601(currentPosition)
        val to: String? = mediaLoadData.trackFormat?.id
        val representationSwitch = to?.let { RepresentationSwitch(t, mt, it) }

        if (representationSwitch != null) {
            representationSwitchList.entries.add(representationSwitch)
            return true
        }

        return false
    }

    private fun addMpdInformation(mediaLoadData: MediaLoadData) {
        val format = mediaLoadData.trackFormat
        if (format != null) {
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackStateChangedEvent(playbackStateChangedEvent: PlaybackStateChangedEvent) {
        if (playbackStateChangedEvent.playbackState == PlayerStates.BUFFERING) {
            addBufferLevelEntry()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLoadCompleted(
        loadCompletedEvent: LoadCompletedEvent
    ) {
        addHttpListEntry(loadCompletedEvent.mediaLoadData, loadCompletedEvent.loadEventInfo)
        addBufferLevelEntry()
    }

    private fun addBufferLevelEntry() {
        val level: Int = exoPlayerAdapter.getBufferLength().toInt()
        val time: String = utils.getCurrentXsDateTime()
        val entry = BufferLevelEntry(time, level)
        bufferLevel.entries.add(entry)
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

    override fun initialize() {
        EventBus.getDefault().register(this)
    }

    override fun getQoeMetricsReportingScheme(): String {
        return THREE_GPP_DASH_METRIC_REPORTING
    }

    @SuppressLint("Range")
    override fun getQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest): String {
        try {
            val qoeMetricsReport = QoeReport()
            qoeMetricsReport.reportTime = utils.getCurrentXsDateTime()
            qoeMetricsReport.periodId = exoPlayerAdapter.getCurrentPeriodId()
            qoeMetricsReport.reportPeriod = qoeMetricsRequest.reportPeriod?.toInt()

            if (shouldReportMetric(Metrics.BUFFER_LEVEL, qoeMetricsRequest.metrics)) {
                if (bufferLevel.entries.size > 0) {
                    qoeMetricsReport.bufferLevel = arrayListOf(bufferLevel)
                }
            }

            if (shouldReportMetric(Metrics.REP_SWITCH_LIST, qoeMetricsRequest.metrics)) {
                if (representationSwitchList.entries.size > 0) {
                    qoeMetricsReport.representationSwitchList =
                        arrayListOf(representationSwitchList)
                }
            }

            if (shouldReportMetric(Metrics.HTTP_LIST, qoeMetricsRequest.metrics)) {
                if (httpList.entries.size > 0) {
                    qoeMetricsReport.httpList = arrayListOf(httpList)
                }
            }

            if (shouldReportMetric(Metrics.MPD_INFORMATION, qoeMetricsRequest.metrics)) {
                if (mpdInformation.size > 0) {
                    qoeMetricsReport.mpdInformation = mpdInformation
                }
            }

            val receptionReport =
                ReceptionReport(qoeMetricsReport, exoPlayerAdapter.getCurrentManifestUrl())
            receptionReport.xmlns =
                XmlSchemaStrings.THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.SCHEMA
            receptionReport.schemaLocation =
                XmlSchemaStrings.THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.SCHEMA + " " + XmlSchemaStrings.THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.LOCATION
            receptionReport.xsi = XmlSchemaStrings.THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.XSI
            receptionReport.sv = XmlSchemaStrings.THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.SV

            var xml = serializeReceptionReportToXml(receptionReport)
            xml = addDelimiter(xml)

            return xml
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            return ""
        }
    }

    private fun shouldReportMetric(metric: String, metricsList: ArrayList<String>?): Boolean {
        // Special handling for HTTP List. Needs to be enabled explicitly as not part of TS 26.247
        if (metric == Metrics.HTTP_LIST) {
            if (metricsList != null) {
                return metricsList.contains(metric)
            }
        }
        return metricsList.isNullOrEmpty() || metricsList.contains(metric)
    }

    private fun addDelimiter(xmlString: String): String {
        val delimiter = "<sv:delimiter>0</sv:delimiter>"
        val qoeMetricEndTag = "</QoeMetric>"

        // Find the last occurrence of the </QoeMetric> tag
        val lastIndex = xmlString.lastIndexOf(qoeMetricEndTag)
        if (lastIndex != -1) {
            // Insert the delimiter after the last </QoeMetric> tag
            val stringBuilder = StringBuilder(xmlString)
            stringBuilder.insert(lastIndex + qoeMetricEndTag.length, delimiter)
            return stringBuilder.toString()
        }

        // If the </QoeMetric> tag is not found, return the original XML string
        return xmlString
    }

    @SuppressLint("Range")
    private fun serializeReceptionReportToXml(input: ReceptionReport): String {
        val xmlMapper = XmlMapper()
        val serializedResult = xmlMapper.writeValueAsString(input)

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>$serializedResult"
    }

    override fun reset() {
        representationSwitchList.entries.clear()
        httpList.entries.clear()
        bufferLevel.entries.clear()
        mpdInformation.clear()
    }
}