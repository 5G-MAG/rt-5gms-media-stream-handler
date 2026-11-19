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
import com.fivegmag.a5gmscommonlibrary.helpers.VrMetrics
import com.fivegmag.a5gmscommonlibrary.helpers.XmlSchemaStrings
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.BufferLevelTracker
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.InitialPlayoutDelay
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.PlayoutDelayforMediaStartup

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInfo
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.MpdInformation
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.ReceptionReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.VrMetric
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.Delimiter
import com.fivegmag.a5gmsmediastreamhandler.controller.MetricThresholdCrossedEvent
import com.fivegmag.a5gmsmediastreamhandler.controller.ThresholdCrossing
import com.fivegmag.a5gmsmediastreamhandler.controller.ThresholdMonitor

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.RepresentationSwitchList

import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.InitialPlayoutDelayTracker
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.PlayoutDelayForMediaStartupTracker
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.DeviceInformationTracker
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.VrDeviceInformationTracker
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

    // Clock (ms) and start of the QoE session, for reportPeriod of a report without a reporting interval (CR-048)
    internal var clock: () -> Long = { System.currentTimeMillis() }
    private var sessionStartMs: Long = clock()

    // The crossing thresholds of the configuration (TS 26.510 V19.2.0 clause 5.2.11.1): the values that have a single number are sampled
    // every samplingPeriod, BufferLevel (the level in ms) and InitialPlayoutDelay (in ms); other metrics are lists or text and have no value to
    // compare (DEFECTS K9: the text does not say which value is compared, this is the reading)
    private var thresholdMonitor: ThresholdMonitor? = null
    private val thresholdHandler = Handler(Looper.getMainLooper())
    private var thresholdSampler: Runnable? = null

    // Initial playout delay tracking per TS 26.247 clause 10.2.5
    private val initialPlayoutDelayTracker: InitialPlayoutDelayTracker = InitialPlayoutDelayTracker()

    // Playout delay for media start-up tracking per TS 26.247 clause 10.2.9
    private val playoutDelayForMediaStartupTracker: PlayoutDelayForMediaStartupTracker = PlayoutDelayForMediaStartupTracker()

    // Device information tracking per TS 26.247 clause 10.2.10
    private val deviceInformationTracker: DeviceInformationTracker = DeviceInformationTracker(exoPlayerAdapter, utils)

    // VR device information per TS 26.118 clause 9.3.4
    private val vrDeviceInformationTracker: VrDeviceInformationTracker = VrDeviceInformationTracker(exoPlayerAdapter, utils)

    // Average throughput tracking per TS 26.247 clause 10.2.4
    private val throughputTracker: ThroughputTracker = ThroughputTracker(utils)

    // PlayList tracking per TS 26.247 clause 10.2.6
    private val playListTracker: PlayListTracker = PlayListTracker(exoPlayerAdapter)


    companion object {
        const val TAG = "5GMS-QoeMetricsReporterExoplayer"
        const val SCHEME = MetricReportingSchemes.THREE_GPP_DASH_METRIC_REPORTING
        const val VR_SCHEME = MetricReportingSchemes.THREE_GPP_VR_METRIC_REPORTING

        /**
         * Whether the configuration asks for virtual reality metrics: its scheme is the one of the VR metrics, or the metrics of TS 26.118 clause 9.3
         * are listed in it (clause 9.4.1: the configuration of TS 26.247 clauses 10.4 and 10.5 "can also include any metrics defined in clause 9.3",
         * which a manifest does under the scheme QM10)
         */
        fun asksForVrMetrics(request: QoeMetricsRequest): Boolean =
            request.scheme == VR_SCHEME || request.metrics?.any { it.startsWith(VrMetrics.NAMESPACE + "#") } == true
    }

    override fun setLastQoeMetricsRequest(lastQoeMetricsRequest: QoeMetricsRequest) {
        this.lastQoeMetricsRequest = lastQoeMetricsRequest
    }



    // Buffer level handling moved to BufferLevelTracker

    // HttpList handling moved to HttpListTracker

    override fun initialize(lastQoeMetricsRequest: QoeMetricsRequest) {
        sessionStartMs = clock()
        // the trackers listen on the event bus of the instance that owns the player
        val bus = exoPlayerAdapter.eventBus
        initialPlayoutDelayTracker.eventBus = bus
        playoutDelayForMediaStartupTracker.eventBus = bus
        deviceInformationTracker.eventBus = bus
        throughputTracker.eventBus = bus
        playListTracker.eventBus = bus
        representationSwitchTracker.eventBus = bus
        mpdInformationTracker.eventBus = bus
        // EventBus.getDefault().register(this) // No longer needed as we moved all event handling to trackers
        initialPlayoutDelayTracker.initialize()
        playoutDelayForMediaStartupTracker.initialize()
        deviceInformationTracker.initialize()
        throughputTracker.initialize()
        playListTracker.initialize()
        bufferLevelTracker.initialize()
        representationSwitchTracker.initialize()
        mpdInformationTracker.initialize()
        if (asksForVrMetrics(lastQoeMetricsRequest)) {
            vrDeviceInformationTracker.eventBus = bus
            vrDeviceInformationTracker.initialize()
        }
        bufferLevelTracker.configure(lastQoeMetricsRequest)
        setLastQoeMetricsRequest(lastQoeMetricsRequest)
        startThresholdSampling(lastQoeMetricsRequest, bus)
    }

    private fun startThresholdSampling(request: QoeMetricsRequest, bus: EventBus) {
        stopThresholdSampling()
        val monitor = ThresholdMonitor(request.positiveCrossingThresholds, request.negativeCrossingThresholds)
        if (monitor.isEmpty() || request.samplingPeriod <= 0) return
        thresholdMonitor = monitor
        val period = request.samplingPeriod * 1000L
        val sampler = object : Runnable {
            override fun run() {
                val crossings = ArrayList<ThresholdCrossing>()
                if (Metrics.BUFFER_LEVEL.substringAfter('#') in monitor.metrics())
                    crossings += monitor.sample(Metrics.BUFFER_LEVEL.substringAfter('#'), exoPlayerAdapter.getBufferLength().toDouble())
                initialPlayoutDelayTracker.getInitialPlayoutDelay()?.let {
                    if ("InitialPlayoutDelay" in monitor.metrics()) crossings += monitor.sample("InitialPlayoutDelay", it.toDouble())
                }
                playoutDelayForMediaStartupTracker.getPlayoutDelayForMediaStartup()?.let {
                    if ("PlayoutDelayforMediaStartup" in monitor.metrics()) crossings += monitor.sample("PlayoutDelayforMediaStartup", it.toDouble())
                }
                for (crossing in crossings)
                    bus.post(MetricThresholdCrossedEvent(request.metricsReportingConfigurationId, crossing))
                thresholdHandler.postDelayed(this, period)
            }
        }
        thresholdSampler = sampler
        thresholdHandler.postDelayed(sampler, period)
    }

    private fun stopThresholdSampling() {
        thresholdSampler?.let { thresholdHandler.removeCallbacks(it) }
        thresholdSampler = null
        thresholdMonitor = null
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
            // Implementation choice, text silent, see CR-048 (open item O-2): TS 26.247 V19.1.0 clause 10.6.2 does not define reportPeriod, which the
            // schema requires. The period the report covers: the configured reporting interval (seconds, as in listing 10.6.3-1) or, for a single final
            // report (no interval), the time in seconds since the QoE session started.
            qoeMetricsReport.reportPeriod = reportPeriodSeconds(qoeMetricsRequest)
            qoeMetricsReport.recordingSessionId = recordingSessionId
            qoeMetricsReport.delimiters = arrayListOf(Delimiter(), Delimiter(), Delimiter(), Delimiter())

            if (shouldReportMetric(Metrics.BUFFER_LEVEL, qoeMetricsRequest)) {
                bufferLevelTracker.addCurrentEntry()
                val bufferLevel = bufferLevelTracker.getBufferLevel()
                if (bufferLevel.entries.size > 0) {
                    qoeMetricsReport.bufferLevel = arrayListOf(bufferLevel)
                }
            }

            if (shouldReportMetric(Metrics.REP_SWITCH_LIST, qoeMetricsRequest)) {
                val representationSwitchList = representationSwitchTracker.getRepresentationSwitchList()
                if (representationSwitchList.entries.size > 0) {
                    qoeMetricsReport.representationSwitchList =
                        arrayListOf(representationSwitchList)
                }
            }

            if (shouldReportMetric(Metrics.MPD_INFORMATION, qoeMetricsRequest)) {
                val mpdInformation = mpdInformationTracker.getMpdInformation()
                if (mpdInformation.size > 0) {
                    qoeMetricsReport.mpdInformation = mpdInformation
                }
            }

            if (shouldReportMetric(Metrics.INITIAL_PLAYOUT_DELAY, qoeMetricsRequest)) {
                val initialPlayoutDelay = initialPlayoutDelayTracker.getInitialPlayoutDelay()
                if (initialPlayoutDelay != null) {
                    qoeMetricsReport.initialPlayoutDelay = arrayListOf(InitialPlayoutDelay(initialPlayoutDelay))
                }
            }

            if (shouldReportMetric(Metrics.PLAYOUT_DELAY_FOR_MEDIA_STARTUP, qoeMetricsRequest)) {
                val playoutDelayForMediaStartup = playoutDelayForMediaStartupTracker.getPlayoutDelayForMediaStartup()
                if (playoutDelayForMediaStartup != null) {
                    qoeMetricsReport.playoutDelayForMediaStartup = arrayListOf(PlayoutDelayforMediaStartup(playoutDelayForMediaStartup))
                }
            }

            if (shouldReportMetric(Metrics.DEVICE_INFORMATION, qoeMetricsRequest)) {
                val deviceInformation = deviceInformationTracker.getDeviceInformation()
                if (deviceInformation.entries.size > 0) {
                    val supplementQoeMetric = com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.SupplementQoeMetric(deviceInformation)
                    qoeMetricsReport.supplementQoEMetric = arrayListOf(supplementQoeMetric)
                }
            }

            if (shouldReportMetric(Metrics.AVG_THROUGHPUT, qoeMetricsRequest)) {
                throughputTracker.addCurrentEntry()
                val avgThroughputList = throughputTracker.getAvgThroughputList()
                if (avgThroughputList.size > 0) {
                    qoeMetricsReport.avgThroughputList = avgThroughputList
                }
            }

            if (shouldReportMetric(Metrics.PLAY_LIST, qoeMetricsRequest)) {
                val playListSnapshot = playListTracker.createSnapshot()
                if (playListSnapshot != null && playListSnapshot.entries.isNotEmpty()) {
                    qoeMetricsReport.playList = arrayListOf(playListSnapshot)
                }
            }

            // TS 26.118 V19.0.0 clauses 9.4.2 and 9.4.3: the report is of the type VrQoeReportType, the QoeReportType extended by the VR metrics and the
            // schema version 1
            val asksForVr = asksForVrMetrics(qoeMetricsRequest)
            if (asksForVr) {
                qoeMetricsReport.xsiType = "vr:" + XmlSchemaStrings.THREE_GPP_METADATA_2020_VR_METRICS.TYPE
                qoeMetricsReport.vrMetricSchemaVersion = VrMetrics.SCHEMA_VERSION
                getVrMetric(qoeMetricsRequest)?.let { qoeMetricsReport.vrMetric = arrayListOf(it) }
            }

            // TS 26.247 V19.1.0 clause 10.6.2: "If a supplementQoEMetric needs to be sent when no ordinary QoEMetric is due, a dummy MPDInformation
            // metric shall be sent with codecs="none", bandwidth=0, mimeType="none", representationId="none"." The extension of TS 26.118 has the same
            // need (the base type requires a QoeMetric) and no rule of its own: the rule is applied to a report that carries a vrMetric (open item O-41)
            if ((qoeMetricsReport.supplementQoEMetric != null || qoeMetricsReport.vrMetric != null) && !hasOrdinaryMetric(qoeMetricsReport)) {
                qoeMetricsReport.mpdInformation = arrayListOf(MpdInformation("none", null, MpdInfo("none", 0, "none")))
            }

            // Implementation choice, text silent, see CR-050 (open item O-3): the schema requires at least one QoeMetric and TS 26.247 V19.1.0
            // clause 10.6.2 gives the dummy rule only for a supplement metric. A report with no metric at all would be schema-invalid and carries no
            // data: it is not sent (an empty string; the controller sends nothing).
            if (!hasOrdinaryMetric(qoeMetricsReport) && qoeMetricsReport.supplementQoEMetric == null && qoeMetricsReport.vrMetric == null) {
                Log.d(TAG, "No metric to report for configuration ${qoeMetricsRequest.metricsReportingConfigurationId}: no report")
                return ""
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
            if (asksForVr) receptionReport.vr = XmlSchemaStrings.THREE_GPP_METADATA_2020_VR_METRICS.SCHEMA
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



    private fun reportPeriodSeconds(request: QoeMetricsRequest): Int {
        val interval = request.reportingInterval
        if (interval != null && interval > 0) return interval.toInt()
        return ((clock() - sessionStartMs) / 1000).toInt().coerceAtLeast(0)
    }

    private fun hasOrdinaryMetric(report: QoeReport): Boolean =
        report.representationSwitchList != null || report.bufferLevel != null || report.mpdInformation != null ||
            report.initialPlayoutDelay != null || report.playoutDelayForMediaStartup != null || report.avgThroughputList != null ||
            report.playList != null

    // Without terms the configuration has the whole set of its scheme (TS 26.510 V19.2.0 table 9.2.3.1-1): the VR metrics for the VR scheme, the
    // metrics of TS 26.247 for the others. With terms, those of either vocabulary select (TS 26.118 clause 9.3.1: the metrics of TS 26.247 are valid
    // for a VR client too)
    private fun shouldReportMetric(metric: String, request: QoeMetricsRequest): Boolean {
        val terms = request.metrics
        if (terms.isNullOrEmpty()) return metric.startsWith(VrMetrics.NAMESPACE + "#") == (request.scheme == VR_SCHEME)
        return terms.any { termSelectsMetric(it, metric) }
    }

    /**
     * The VR metrics of the report (TS 26.118 V19.0.0 clause 9.3), those that the client can source: the device information. CompQualLatency
     * (clause 9.3.2) needs the gaze (observation point 3), the quality of the regions of the viewport (point 2), the rendering of the viewport
     * (point 4) and the field of view of the device (point 5), RenderedViewports (clause 9.3.3) the gaze and the field of view; the library has no
     * VR renderer or viewport tracker that supplies them, so they are not reported and no value is made up (open item O-40).
     */
    private fun getVrMetric(request: QoeMetricsRequest): VrMetric? {
        for (unavailable in listOf(VrMetrics.COMP_QUAL_LATENCY, VrMetrics.RENDERED_VIEWPORTS)) {
            if (shouldReportMetric(unavailable, request)) {
                Log.d(TAG, "${unavailable.substringAfter('#')} is configured and has no source: no VR renderer supplies viewport data")
            }
        }
        if (!shouldReportMetric(VrMetrics.VR_DEVICE_INFORMATION, request)) return null
        val deviceInformation = vrDeviceInformationTracker.getEntries()
        return if (deviceInformation.isEmpty()) null else VrMetric(vrDeviceInformation = deviceInformation)
    }

    // TS 26.512 V19.3.0 clause E.2.1: a term may carry a path below the metric key (AvgThroughput/numbytes), and pruning the
    // path selects everything below the key, so the first path element decides whether the metric is reported
    private fun termSelectsMetric(term: String, metric: String): Boolean {
        val namespace = metric.substringBefore('#') + "#"
        return term.startsWith(namespace) &&
                term.substring(namespace.length).substringBefore('/') == metric.substring(namespace.length)
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
        vrDeviceInformationTracker.unregister()
        throughputTracker.unregister()
        playListTracker.unregister()

        stopThresholdSampling()
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
        vrDeviceInformationTracker.reset()
        throughputTracker.reset()
        playListTracker.reset()
        initialPlayoutDelayTracker.reset()
        playoutDelayForMediaStartupTracker.reset()
    }

}