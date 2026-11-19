package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import com.fivegmag.a5gmscommonlibrary.helpers.Metrics
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.greenrobot.eventbus.EventBus

import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QoeMetricsReporterExoplayerTest {

    private lateinit var reporter: QoeMetricsReporterExoplayer
    private val mockExoPlayerAdapter = mockk<IExoPlayerAdapter>(relaxed = true)

    // the tests post the player events on the default bus: the adapter hands out that bus
    init {
        io.mockk.every { mockExoPlayerAdapter.eventBus } returns org.greenrobot.eventbus.EventBus.getDefault()
    }

    @Before
    fun setUp() {
        every { mockExoPlayerAdapter.getCurrentPeriodId() } returns "Period1"
        reporter = QoeMetricsReporterExoplayer(mockExoPlayerAdapter)
    }

    @org.junit.After
    fun tearDown() {
        // Unregister all trackers from EventBus to prevent duplicate subscriptions in subsequent tests
        reporter.reset()
    }

    /**
     * Helper: creates a QoeMetricsRequest that asks for ALL metrics.
     * An empty metrics list means "report everything" (per shouldReportMetric logic).
     */
    private fun requestAllMetrics(): QoeMetricsRequest {
        val request = mockk<QoeMetricsRequest>(relaxed = true)
        // Empty list = report all metrics
        every { request.metrics } returns ArrayList()
        every { request.reportingInterval } returns 5000L
        return request
    }

    /**
     * Helper: creates a QoeMetricsRequest that asks for specific metrics.
     */
    private fun requestSpecificMetrics(vararg metrics: String): QoeMetricsRequest {
        val request = mockk<QoeMetricsRequest>(relaxed = true)
        every { request.metrics } returns ArrayList(metrics.toList())
        every { request.reportingInterval } returns 5000L
        return request
    }

    private fun writeReport(fileName: String, content: String) {
        val buildDir = File("build/reports/qoe")
        if (!buildDir.exists()) {
            buildDir.mkdirs()
        }
        val file = File(buildDir, fileName)
        file.writeText(content)
        println("Report written to: ${file.absolutePath}")
    }

    // ==================== XML Structure Tests ====================

    @Test
    fun getQoeMetricsReport_returnsValidXml() {
        val request = requestAllMetrics()
        reporter.initialize(request)

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("basic_report.xml", xmlReport)

        assertTrue("Report should start with XML declaration", xmlReport.startsWith("<?xml"))
        assertTrue("Report should contain ReceptionReport", xmlReport.contains("ReceptionReport"))
    }

    // ==================== Full Session Tests ====================

    @Test
    fun fullSession_reportContainsRepresentationSwitch() {
        val request = requestSpecificMetrics(Metrics.REP_SWITCH_LIST)
        reporter.initialize(request)

        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("repswitch_report.xml", xmlReport)

        assertTrue("Report should contain RepSwitchList", xmlReport.contains("RepSwitchList"))
        
        // Assert specific switch details
        assertTrue("RepSwitchList should target video_720p", xmlReport.contains("to=\"video_720p\""))
        assertTrue("RepSwitchList should have media time", xmlReport.contains("mt=\"P0Y0DT0H0M0S\"")) 
    }

    @Test
    fun fullSession_reportContainsMpdInformation() {
        val request = requestSpecificMetrics(Metrics.MPD_INFORMATION)
        reporter.initialize(request)

        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("mpd_info_report.xml", xmlReport)

        // The XML tag is <MPDInformation> defined in QoeReport
        assertTrue("Report should contain MPDInformation", xmlReport.contains("MPDInformation"))
        
        // Assert specific attributes based on simulation
        assertTrue("MpdInformation should contain correct codec", xmlReport.contains("codecs=\"avc1.4d401f\""))
        assertTrue("MpdInformation should contain correct bandwidth", xmlReport.contains("bandwidth=\"2500000\""))
        assertTrue("MpdInformation should contain correct width", xmlReport.contains("width=\"1280\""))
        assertTrue("MpdInformation should contain correct height", xmlReport.contains("height=\"720\""))
    }

    @Test
    fun fullSession_reportContainsInitialPlayoutDelay() {
        val request = requestSpecificMetrics(Metrics.INITIAL_PLAYOUT_DELAY)
        reporter.initialize(request)

        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("playout_delay_report.xml", xmlReport)

        // InitialPlayoutDelay = FirstFrameRendered(1350) - FirstMediaSegmentRequested(1100) = 250ms
        assertTrue("Report should contain InitialPlayoutDelay", xmlReport.contains("InitialPlayoutDelay"))
    }

    // ==================== Metric Filtering Tests ====================

    @Test
    fun metricFiltering_onlyRequestedMetricsAppearInReport() {
        // Request ONLY RepSwitchList — other metrics should NOT appear
        val request = requestSpecificMetrics(Metrics.REP_SWITCH_LIST)
        reporter.initialize(request)

        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("filtered_report.xml", xmlReport)

        assertTrue("RepSwitchList SHOULD be present", xmlReport.contains("RepSwitchList"))
        // BufferLevel is NOT in the request, so it should be excluded
        assertFalse("BufferLevel should NOT be present", xmlReport.contains("BufferLevel"))
        assertFalse("MPDInformation should NOT be present", xmlReport.contains("MPDInformation"))
    }

    @Test
    fun fullSession_reportContainsPlayList() {
        val request = requestSpecificMetrics(Metrics.PLAY_LIST)
        reporter.initialize(request)

        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("playlist_report.xml", xmlReport)
        
        // Assert Report Structure
        assertTrue("Report should contain PlayList entries (Trace)", xmlReport.contains("Trace"))
        
        // Assert First Trace (Start)
    assertTrue("Should start with NewPlayoutRequest trace", xmlReport.contains("startType=\"NewPlayoutRequest\""))
    assertTrue("First trace should start at 0s media time", xmlReport.contains("mstart=\"P0Y0DT0H0M0S\""))
        
        // Assert Pause Event matches UserRequest
    assertTrue("PlayList should contain UserRequest stop reason", xmlReport.contains("stopReason=\"UserRequest\""))

        // Assert Seek Event
    assertTrue("PlayList should contain OtherUserRequest trace", xmlReport.contains("startType=\"OtherUserRequest\""))
    assertTrue("Seek target should be 30s", xmlReport.contains("mstart=\"P0Y0DT0H0M30S\""))

        // Assert Speed Change: the trace before it stops for UserRequest, the next trace carries the new speed (the XSD of TS 26.247
        // clause 10.2.7 has the playbackSpeed attribute; it defines no stop reason for a speed change)
        assertFalse("A speed change is not reported as an Other stop reason", xmlReport.contains("stopReasonOther="))
        assertTrue("Should have playbackSpeed='1.5'", xmlReport.contains("playbackSpeed=\"1.5\""))
        
        // Assert Representation ID
        assertTrue("Should track representationId", xmlReport.contains("representationId=\"video_720p\""))
    }

    @Test
    fun fullSession_reportContainsBufferLevel() {
        val request = requestSpecificMetrics(Metrics.BUFFER_LEVEL)
        reporter.initialize(request)

        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("buffer_level_report.xml", xmlReport)

        assertTrue("Report should contain BufferLevel", xmlReport.contains("BufferLevel"))
        assertTrue("Report should contain BufferLevel entries", xmlReport.contains("BufferLevelEntry"))
        
        // Assert buffer level values are present (e.g. level="...")
        assertTrue("BufferLevelEntry should have level attribute", xmlReport.contains("level="))
    }

    // ==================== Reset Tests ====================

    @Test
    fun reset_clearsMetrics_repSwitchListGoneAfterReset() {
        val request = requestSpecificMetrics(Metrics.REP_SWITCH_LIST)
        reporter.initialize(request)

        // Phase 1: Simulate playback, verify data exists
        PlaybackSessionSimulator.simulateFullPlaybackSession()
        val reportBefore = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("reset_before_report.xml", reportBefore)
        assertTrue("BEFORE reset: RepSwitchList should be present", reportBefore.contains("RepSwitchList"))

        // Phase 2: Reset and verify data is gone
        reporter.resetState()
        val reportAfter = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("reset_after_report.xml", reportAfter)
        assertFalse("AFTER reset: RepSwitchList should NOT be present", reportAfter.contains("RepSwitchList"))
    }


    @Test
    fun termWithPathBelowKey_selectsOnlyThatMetric() {
        // TS 26.512 V19.3.0 clause E.2.1 example 1: the term names a member of the metric, the metric is reported
        val request = requestSpecificMetrics(Metrics.AVG_THROUGHPUT + "/numbytes")
        reporter.initialize(request)

        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        assertTrue("Report should contain AvgThroughput", xmlReport.contains("AvgThroughput"))
        assertFalse("Report should NOT contain BufferLevel", xmlReport.contains("BufferLevel"))
    }

    @Test
    fun metricKeysAreTheKeyNamesOfTs26247() {
        // TS 26.512 V19.3.0 clause E.2.1: the term identifiers are the key names of TS 26.247 clause 10.2
        assertEquals("urn:3GPP:ns:PSS:DASH:QM10#DeviceInformationList", Metrics.DEVICE_INFORMATION)
        assertEquals("urn:3GPP:ns:PSS:DASH:QM10#PlayoutDelayforMediaStartup", Metrics.PLAYOUT_DELAY_FOR_MEDIA_STARTUP)
    }

    @Test
    fun fullSession_reportContainsAllMetrics() {
        // Request ALL metrics explicitly
        val request = requestSpecificMetrics(
            Metrics.BUFFER_LEVEL,
            Metrics.REP_SWITCH_LIST,
            Metrics.MPD_INFORMATION,
            Metrics.INITIAL_PLAYOUT_DELAY,
            Metrics.PLAYOUT_DELAY_FOR_MEDIA_STARTUP,
            Metrics.DEVICE_INFORMATION,
            Metrics.AVG_THROUGHPUT,
            Metrics.PLAY_LIST
        )
        reporter.initialize(request)

        // Simulate a full playback session
        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "session-1")
        writeReport("all_metrics_report.xml", xmlReport)

        // Assert all metrics are present
        assertTrue("Report should contain BufferLevel", xmlReport.contains("BufferLevel"))
        assertTrue("Report should contain RepSwitchList", xmlReport.contains("RepSwitchList"))
        assertTrue("Report should contain MPDInformation", xmlReport.contains("MPDInformation"))
        assertTrue("Report should contain InitialPlayoutDelay", xmlReport.contains("InitialPlayoutDelay"))
        assertTrue("Report should contain PlayoutDelayforMediaStartup", xmlReport.contains("PlayoutDelayforMediaStartup"))
        assertTrue("Report should contain supplementQoEMetric", xmlReport.contains("supplementQoEMetric"))
        assertTrue("Report should contain deviceinformation", xmlReport.contains("deviceinformation"))
        assertTrue("Report should contain AvgThroughput", xmlReport.contains("AvgThroughput"))
        assertFalse("Report should NOT contain AvgThroughputList", xmlReport.contains("AvgThroughputList"))

        // Assert compliance: schemaVersion present
        assertTrue("Report should contain schemaVersion", xmlReport.contains("sv:schemaVersion"))
        assertTrue("Report should contain TSG105-Rel18", xmlReport.contains("TSG105-Rel18"))

        // Assert compliance: 2017 namespace
        assertTrue("Report should use 2017 namespace", xmlReport.contains("urn:3gpp:metadata:2017:HSD:receptionreport"))
        assertFalse("Report should NOT use 2011 namespace", xmlReport.contains("urn:3gpp:metadata:2011:HSD:receptionreport"))
    }



    // TS 26.247 V19.1.0 clause 10.6.2: a supplementQoEMetric that goes out alone needs a dummy MPDInformation (a QoeReport has at least one QoeMetric)
    @Test
    fun aSupplementWithoutAnOrdinaryMetricGetsTheDummyMpdInformation() {
        val request = requestSpecificMetrics(Metrics.DEVICE_INFORMATION)
        reporter.initialize(request)

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")
        writeReport("supplement_only_report.xml", xmlReport)

        assertTrue("Report should contain supplementQoEMetric", xmlReport.contains("supplementQoEMetric"))
        assertTrue("Report should contain the dummy MPDInformation", xmlReport.contains("<MPDInformation representationId=\"none\">"))
        assertTrue("Dummy should have codecs none", xmlReport.contains("codecs=\"none\""))
        assertTrue("Dummy should have bandwidth 0", xmlReport.contains("bandwidth=\"0\""))
        assertTrue("Dummy should have mimeType none", xmlReport.contains("mimeType=\"none\""))
        QoeSchemaValidator.validate(xmlReport)
    }

    @Test
    fun aSupplementWithAnOrdinaryMetricGetsNoDummy() {
        val request = requestSpecificMetrics(Metrics.DEVICE_INFORMATION, Metrics.INITIAL_PLAYOUT_DELAY)
        reporter.initialize(request)

        PlaybackSessionSimulator.simulateFullPlaybackSession()

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")
        assertTrue("Report should contain InitialPlayoutDelay", xmlReport.contains("InitialPlayoutDelay"))
        assertFalse("Report should NOT contain a dummy", xmlReport.contains("representationId=\"none\""))
        QoeSchemaValidator.validate(xmlReport)
    }

    // Implementation choice, text silent, see CR-048 (O-2): reportPeriod is required by the schema; a single final report (no reporting interval) has
    // the time in seconds since the QoE session started
    @Test
    fun theReportPeriodOfAFinalReportIsTheSecondsSinceTheSessionStarted() {
        var now = 1_000_000L
        reporter.clock = { now }
        val request = requestSpecificMetrics(Metrics.DEVICE_INFORMATION)
        every { request.reportingInterval } returns 0L
        reporter.initialize(request)
        now += 42_900L

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")

        assertTrue("reportPeriod is the elapsed seconds: $xmlReport", xmlReport.contains("reportPeriod=\"42\""))
        QoeSchemaValidator.validate(xmlReport)
    }

    @Test
    fun theReportPeriodWithAReportingIntervalIsTheInterval() {
        val request = requestSpecificMetrics(Metrics.DEVICE_INFORMATION)
        every { request.reportingInterval } returns 7L
        reporter.initialize(request)

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")

        assertTrue("reportPeriod is the interval: $xmlReport", xmlReport.contains("reportPeriod=\"7\""))
    }

    // Implementation choice, text silent, see CR-050 (O-3): a report with no metric at all is not made (it would be invalid against the schema)
    @Test
    fun aReportWithNoMetricAtAllIsNotMade() {
        val request = requestSpecificMetrics(Metrics.INITIAL_PLAYOUT_DELAY)
        reporter.initialize(request)

        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")

        assertEquals("", xmlReport)
    }
}
