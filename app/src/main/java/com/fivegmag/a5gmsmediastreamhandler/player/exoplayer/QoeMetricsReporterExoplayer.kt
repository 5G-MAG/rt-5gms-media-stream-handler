package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi

import androidx.media3.exoplayer.source.MediaLoadData
import com.fasterxml.jackson.dataformat.xml.XmlMapper


import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.MetricReportingSchemes
import com.fivegmag.a5gmscommonlibrary.helpers.Metrics
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.helpers.XmlSchemaStrings
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.BufferLevelTracker
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.InitialPlayoutDelay
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.PlayoutDelayforMediaStartup

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInformation
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.ReceptionReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.Delimiter

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.RepresentationSwitchList

import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.InitialPlayoutDelayTracker
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.PlayoutDelayForMediaStartupTracker
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.DeviceInformationTracker
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.ThroughputTracker
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.PlayListTracker

import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.RepresentationSwitchTracker
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.MpdInformationTracker
import com.fivegmag.a5gmsmediastreamhandler.player.IQoeMetricsReporter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Timer
import java.util.TimerTask

@UnstableApi
class QoeMetricsReporterExoplayer(
    private val exoPlayerAdapter: IExoPlayerAdapter
) : IQoeMetricsReporter {
    private val utils: Utils = Utils()
    private val bufferLevelTracker: BufferLevelTracker = BufferLevelTracker(exoPlayerAdapter, utils)
    private val representationSwitchTracker: RepresentationSwitchTracker =
        RepresentationSwitchTracker(exoPlayerAdapter, utils)
    private val mpdInformationTracker: MpdInformationTracker = MpdInformationTracker()
    private var lastQoeMetricsRequest: QoeMetricsRequest? = null

    // Initial playout delay tracking per TS 26.247 clause 10.2.5
    private val initialPlayoutDelayTracker: InitialPlayoutDelayTracker = InitialPlayoutDelayTracker()

    // Playout delay for media start-up tracking per TS 26.247 clause 10.2.9
    private val playoutDelayForMediaStartupTracker: PlayoutDelayForMediaStartupTracker = PlayoutDelayForMediaStartupTracker()

    // Device information tracking per TS 26.247 clause 10.2.10
    private val deviceInformationTracker: DeviceInformationTracker = DeviceInformationTracker(exoPlayerAdapter, utils)

    // Average throughput tracking per TS 26.247 clause 10.2.4
    private val throughputTracker: ThroughputTracker = ThroughputTracker(utils)

    // PlayList tracking per TS 26.247 clause 10.2.6
    private val playListTracker: PlayListTracker = PlayListTracker(exoPlayerAdapter)


    companion object {
        const val TAG = "5GMS-QoeMetricsReporterExoplayer"
        const val SCHEME = MetricReportingSchemes.THREE_GPP_DASH_METRIC_REPORTING
    }

    override fun setLastQoeMetricsRequest(lastQoeMetricsRequest: QoeMetricsRequest) {
        this.lastQoeMetricsRequest = lastQoeMetricsRequest
    }



    // Buffer level handling moved to BufferLevelTracker

    // HttpList handling moved to HttpListTracker

    override fun initialize(lastQoeMetricsRequest: QoeMetricsRequest) {
        // EventBus.getDefault().register(this) // No longer needed as we moved all event handling to trackers
        initialPlayoutDelayTracker.initialize()
        playoutDelayForMediaStartupTracker.initialize()
        deviceInformationTracker.initialize()
        throughputTracker.initialize()
        playListTracker.initialize()
        bufferLevelTracker.initialize()
        representationSwitchTracker.initialize()
        mpdInformationTracker.initialize()
        bufferLevelTracker.configure(lastQoeMetricsRequest)
        setLastQoeMetricsRequest(lastQoeMetricsRequest)
    }

    @SuppressLint("Range")
    override fun getQoeMetricsReport(
        qoeMetricsRequest: QoeMetricsRequest,
        reportingClientId: String,
        recordingSessionId: String
    ): String {
        try {
            val qoeMetricsReport = QoeReport()
            qoeMetricsReport.reportTime = utils.getCurrentXsDateTime()
            qoeMetricsReport.periodId = exoPlayerAdapter.getCurrentPeriodId()
            qoeMetricsReport.reportPeriod = qoeMetricsRequest.reportingInterval?.toInt()
            qoeMetricsReport.recordingSessionId = recordingSessionId
            qoeMetricsReport.delimiters = arrayListOf(Delimiter(), Delimiter(), Delimiter(), Delimiter())

            if (shouldReportMetric(Metrics.BUFFER_LEVEL, qoeMetricsRequest.metrics)) {
                bufferLevelTracker.addCurrentEntry()
                val bufferLevel = bufferLevelTracker.getBufferLevel()
                if (bufferLevel.entries.size > 0) {
                    qoeMetricsReport.bufferLevel = arrayListOf(bufferLevel)
                }
            }

            if (shouldReportMetric(Metrics.REP_SWITCH_LIST, qoeMetricsRequest.metrics)) {
                val representationSwitchList = representationSwitchTracker.getRepresentationSwitchList()
                if (representationSwitchList.entries.size > 0) {
                    qoeMetricsReport.representationSwitchList =
                        arrayListOf(representationSwitchList)
                }
            }

            if (shouldReportMetric(Metrics.MPD_INFORMATION, qoeMetricsRequest.metrics)) {
                val mpdInformation = mpdInformationTracker.getMpdInformation()
                if (mpdInformation.size > 0) {
                    qoeMetricsReport.mpdInformation = mpdInformation
                }
            }

            if (shouldReportMetric(Metrics.INITIAL_PLAYOUT_DELAY, qoeMetricsRequest.metrics)) {
                val initialPlayoutDelay = initialPlayoutDelayTracker.getInitialPlayoutDelay()
                if (initialPlayoutDelay != null) {
                    qoeMetricsReport.initialPlayoutDelay = arrayListOf(InitialPlayoutDelay(initialPlayoutDelay))
                }
            }

            if (shouldReportMetric(Metrics.PLAYOUT_DELAY_FOR_MEDIA_STARTUP, qoeMetricsRequest.metrics)) {
                val playoutDelayForMediaStartup = playoutDelayForMediaStartupTracker.getPlayoutDelayForMediaStartup()
                if (playoutDelayForMediaStartup != null) {
                    qoeMetricsReport.playoutDelayForMediaStartup = arrayListOf(PlayoutDelayforMediaStartup(playoutDelayForMediaStartup))
                }
            }

            if (shouldReportMetric(Metrics.DEVICE_INFORMATION, qoeMetricsRequest.metrics)) {
                val deviceInformation = deviceInformationTracker.getDeviceInformation()
                if (deviceInformation.entries.size > 0) {
                    val supplementQoeMetric = com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.SupplementQoeMetric(deviceInformation)
                    qoeMetricsReport.supplementQoEMetric = arrayListOf(supplementQoeMetric)
                }
            }

            if (shouldReportMetric(Metrics.AVG_THROUGHPUT, qoeMetricsRequest.metrics)) {
                throughputTracker.addCurrentEntry()
                val avgThroughputList = throughputTracker.getAvgThroughputList()
                if (avgThroughputList.size > 0) {
                    qoeMetricsReport.avgThroughputList = avgThroughputList
                }
            }

            if (shouldReportMetric(Metrics.PLAY_LIST, qoeMetricsRequest.metrics)) {
                val playListSnapshot = playListTracker.createSnapshot()
                if (playListSnapshot != null && playListSnapshot.entries.isNotEmpty()) {
                    qoeMetricsReport.playList = arrayListOf(playListSnapshot)
                }
            }

            val receptionReport =
                ReceptionReport(qoeReport = qoeMetricsReport, contentUri = exoPlayerAdapter.getCurrentManifestUrl())
            receptionReport.schemaVersion = "TSG105-Rel18"
            receptionReport.xmlns =
                XmlSchemaStrings.THREE_GPP_METADATA_2017_HSD_RECEPTION_REPORT.SCHEMA
            receptionReport.schemaLocation =
                XmlSchemaStrings.THREE_GPP_METADATA_2017_HSD_RECEPTION_REPORT.SCHEMA + " " + XmlSchemaStrings.THREE_GPP_METADATA_2017_HSD_RECEPTION_REPORT.LOCATION
            receptionReport.xsi = XmlSchemaStrings.THREE_GPP_METADATA_2017_HSD_RECEPTION_REPORT.XSI
            receptionReport.sv = XmlSchemaStrings.THREE_GPP_METADATA_2017_HSD_RECEPTION_REPORT.SV
            receptionReport.sup = "urn:3gpp:metadata:2016:PSS:SupplementQoEMetric"
            receptionReport.clientId = reportingClientId
            receptionReport.delimiter = Delimiter()

            val xml = serializeReceptionReportToXml(receptionReport)

            return xml
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            return ""
        }
    }

    // Sampling timer moved to BufferLevelTracker



    private fun shouldReportMetric(metric: String, metricsList: ArrayList<String>?): Boolean {
        return metricsList.isNullOrEmpty() || metricsList.contains(metric)
    }



    @SuppressLint("Range")
    private fun serializeReceptionReportToXml(input: ReceptionReport): String {
        val xmlMapper = XmlMapper()
        val serializedResult = xmlMapper.writeValueAsString(input)

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>$serializedResult"
    }

    override fun reset() {
        resetState()
        initialPlayoutDelayTracker.unregister()
        playoutDelayForMediaStartupTracker.unregister()
        deviceInformationTracker.unregister()
        throughputTracker.unregister()
        playListTracker.unregister()

        bufferLevelTracker.unregister()
        representationSwitchTracker.unregister()
        mpdInformationTracker.unregister()
        lastQoeMetricsRequest = null
    }

    @SuppressLint("Range")
    override fun resetState() {
        representationSwitchTracker.reset()

        bufferLevelTracker.reset()
        mpdInformationTracker.reset()
        deviceInformationTracker.reset()
        throughputTracker.reset()
        playListTracker.reset()
        initialPlayoutDelayTracker.reset()
        playoutDelayForMediaStartupTracker.reset()
    }

}