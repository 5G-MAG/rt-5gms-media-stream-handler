package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fivegmag.a5gmscommonlibrary.helpers.MetricReportingSchemes
import com.fivegmag.a5gmscommonlibrary.helpers.Metrics
import com.fivegmag.a5gmscommonlibrary.helpers.VrMetrics
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.CompQualLatencyEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.Delimiter
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.InitialPlayoutDelay
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QualityLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.ReceptionReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.RenderedViewportEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.ViewportData
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.ViewportItem
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.VrDeviceInformationEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.VrMetric
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The report of the virtual reality metrics of TS 26.118 V19.0.0 clause 9 (clauses 9.4.2 and 9.4.3), checked against the schema of clause 9.4.3
 * (`schemas/qoe/vr.xsd`, the printed schema with the corrections that its header lists)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VrMetricsReportTest {
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true)
    private lateinit var reporter: QoeMetricsReporterExoplayer

    init {
        every { adapter.eventBus } returns org.greenrobot.eventbus.EventBus.getDefault()
    }

    @Before
    fun setUp() {
        every { adapter.getCurrentPeriodId() } returns "Period1"
        every { adapter.getDisplayRefreshRate() } returns 59.94f
        reporter = QoeMetricsReporterExoplayer(adapter)
    }

    @After
    fun tearDown() = reporter.reset()

    private fun request(scheme: String, vararg metrics: String) =
        QoeMetricsRequest(scheme, 1, 5, ArrayList(metrics.toList()), "c1")

    private fun report(request: QoeMetricsRequest): String {
        reporter.initialize(request)
        return reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")
    }

    // clause 9.3.4: the information is logged at the start of the session; what the platform gives is reported and what it does not is 0
    @Test
    fun theVrSchemeWithoutTermsReportsTheDeviceInformationInAReportOfTheExtendedType() {
        val xml = report(request(MetricReportingSchemes.THREE_GPP_VR_METRIC_REPORTING))
        assertTrue(xml, xml.contains("xmlns:vr=\"urn:3gpp:metadata:2020:VR:metrics\""))
        assertTrue(xml, xml.contains("xsi:type=\"vr:VrQoeReportType\""))
        assertTrue(xml, xml.contains("<vr:vrMetricSchemaVersion>1</vr:vrMetricSchemaVersion>"))
        assertTrue(xml, xml.contains("<vr:refreshRate>60</vr:refreshRate>"))
        assertTrue(xml, xml.contains("<vr:deviceIdentifier>"))
        assertTrue(xml, xml.contains("<vr:horizontalResolution>0</vr:horizontalResolution>"))
        assertTrue(xml, xml.contains("<vr:renderedVerticalFoV>0</vr:renderedVerticalFoV>"))
        QoeSchemaValidator.validateVr(xml)
    }

    // clauses 9.3.2 and 9.3.3: no source in the library, so no value; the metrics of TS 26.247 are not in the default set of the VR scheme
    @Test
    fun theViewportMetricsAreNeverMadeUp() {
        val xml = report(request(MetricReportingSchemes.THREE_GPP_VR_METRIC_REPORTING))
        assertFalse(xml, xml.contains("compQualLatency"))
        assertFalse(xml, xml.contains("renderedViewports"))
        assertFalse(xml, xml.contains("<BufferLevel"))
        assertFalse(xml, xml.contains("<RepSwitchList"))
    }

    // a configuration of the viewport metrics only has no metric to report (no source, no value made up), so no report is made (CR-050, O-3)
    @Test
    fun aConfigurationOfTheViewportMetricsOnlyHasNothingToReport() {
        val xml = report(request(MetricReportingSchemes.THREE_GPP_VR_METRIC_REPORTING, VrMetrics.COMP_QUAL_LATENCY, VrMetrics.RENDERED_VIEWPORTS + "/duration"))
        assertEquals("", xml)
    }

    // clause E.2.2: a term below the key selects the metric (the terminal path elements are pruned to select all below a branch)
    @Test
    fun aTermBelowTheKeySelectsTheDeviceInformation() {
        val xml = report(request(MetricReportingSchemes.THREE_GPP_VR_METRIC_REPORTING, VrMetrics.VR_DEVICE_INFORMATION + "/refreshRate"))
        assertTrue(xml, xml.contains("<vr:vrDeviceInformation>"))
    }

    // clause 9.4.1: a manifest lists the metrics of clause 9.3 with those of TS 26.247, under the scheme QM10; the report carries both
    @Test
    fun aQm10ConfigurationThatListsAVrMetricGetsTheExtendedReportWithTheOrdinaryMetric() {
        every { adapter.getBufferLength() } returns 4000L
        val xml = report(request(MetricReportingSchemes.THREE_GPP_DASH_METRIC_REPORTING, Metrics.BUFFER_LEVEL, VrMetrics.VR_DEVICE_INFORMATION))
        assertTrue(xml, xml.contains("xsi:type=\"vr:VrQoeReportType\""))
        assertTrue(xml, xml.contains("<BufferLevel"))
        assertTrue(xml, xml.contains("<vr:vrDeviceInformation>"))
        assertFalse("an ordinary metric is there, no dummy", xml.contains("representationId=\"none\""))
        QoeSchemaValidator.validateVr(xml)
    }

    // the same need as that of a supplementQoEMetric (TS 26.247 clause 10.6.2): the base type requires a QoeMetric
    @Test
    fun aReportWithOnlyAVrMetricGetsTheDummyMpdInformation() {
        val xml = report(request(MetricReportingSchemes.THREE_GPP_VR_METRIC_REPORTING))
        assertTrue(xml, xml.contains("<MPDInformation representationId=\"none\">"))
        QoeSchemaValidator.validateVr(xml)
    }

    // a QM10 configuration without VR metrics is a report as before
    @Test
    fun aQm10ConfigurationWithoutVrMetricsIsNotExtended() {
        val xml = report(request(MetricReportingSchemes.THREE_GPP_DASH_METRIC_REPORTING, Metrics.BUFFER_LEVEL))
        assertFalse(xml, xml.contains("vr:"))
        assertFalse(xml, xml.contains("xsi:type"))
        reporter.reset()
        val all = report(request(MetricReportingSchemes.THREE_GPP_DASH_METRIC_REPORTING))
        assertFalse(all, all.contains("vr:"))
    }

    // clause 9.3.4: logged at the start of each session and whenever changed
    @Test
    fun theDeviceInformationIsLoggedAgainWhenTheRefreshRateChanges() {
        val request = request(MetricReportingSchemes.THREE_GPP_VR_METRIC_REPORTING)
        reporter.initialize(request)
        every { adapter.getDisplayRefreshRate() } returns 120f
        val xml = reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")
        assertTrue(xml, xml.contains("<vr:refreshRate>60</vr:refreshRate>"))
        assertTrue(xml, xml.contains("<vr:refreshRate>120</vr:refreshRate>"))
        QoeSchemaValidator.validateVr(xml)
        // after a report an unchanged information is not logged again
        reporter.resetState()
        val next = reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")
        assertFalse(next, next.contains("vr:vrDeviceInformation"))
    }

    // clause 9.4.3: the viewport metrics have a format too, which a VR renderer would fill
    @Test
    fun theViewportMetricsFollowTheSchema() {
        val viewport = ViewportData(11796480, 0, 0, 6553600, 3932160)
        val item = ViewportItem(viewport, arrayListOf(QualityLevelEntry(60.0, 1, 3840, 2160), QualityLevelEntry(40.0, 2, 960, 540)))
        val latency = CompQualLatencyEntry(item, item, item, "2026-11-19T10:00:00.000Z", "PT1.5S", 120, 10, arrayListOf(0, 2))
        val metric = VrMetric(
            compQualLatency = arrayListOf(latency),
            renderedViewports = arrayListOf(RenderedViewportEntry("PT2S", 1500, viewport)),
            vrDeviceInformation = arrayListOf(VrDeviceInformationEntry("2026-11-19T10:00:00.000Z", "PT0S", "brand model 15", 1832, 1920, 100, 100, 90, 90, 90))
        )
        QoeSchemaValidator.validateVr(serialized(arrayListOf(metric), 1))
    }

    // the check is in force: a report that the schema does not allow is rejected
    @Test
    fun theSchemaCheckRejectsAReportWithoutTheSchemaVersion() {
        val metric = VrMetric(vrDeviceInformation = arrayListOf(VrDeviceInformationEntry("2026-11-19T10:00:00.000Z", "PT0S", "d", 0, 0, 0, 0, 0, 0, 60)))
        val rejection = assertThrows(AssertionError::class.java) { QoeSchemaValidator.validateVr(serialized(arrayListOf(metric), null)) }
        assertTrue(rejection.message, rejection.message!!.contains("vrMetricSchemaVersion"))
    }

    @Test
    fun theSchemaCheckRejectsADeviceInformationWithoutTheRefreshRate() {
        val xml = report(request(MetricReportingSchemes.THREE_GPP_VR_METRIC_REPORTING)).replace(Regex("<vr:refreshRate>\\d+</vr:refreshRate>"), "")
        val rejection = assertThrows(AssertionError::class.java) { QoeSchemaValidator.validateVr(xml) }
        assertTrue(rejection.message, rejection.message!!.contains("refreshRate"))
    }

    // table 9.3.2-1 allows an azimuth from -180 * 2^16, the schema types it xs:unsignedInt: a value below 0 cannot be reported (open item O-43)
    @Test
    fun anAngleBelowZeroIsNotValidInTheSchemaThatTheTableAllows() {
        val viewport = ViewportData(-65536, 0, 0, 6553600, 3932160)
        val metric = VrMetric(renderedViewports = arrayListOf(RenderedViewportEntry("PT2S", 1500, viewport)))
        val rejection = assertThrows(AssertionError::class.java) { QoeSchemaValidator.validateVr(serialized(arrayListOf(metric), 1)) }
        assertTrue(rejection.message, rejection.message!!.contains("centreAzimuth"))
    }

    private fun serialized(metrics: ArrayList<VrMetric>, version: Int?): String {
        val qoeReport = QoeReport(periodId = "P1", reportTime = "2026-11-19T10:00:00.000Z", reportPeriod = 5, recordingSessionId = "deadbeef1234")
        qoeReport.initialPlayoutDelay = arrayListOf(InitialPlayoutDelay(100))
        qoeReport.delimiters = arrayListOf(Delimiter(), Delimiter(), Delimiter(), Delimiter())
        qoeReport.xsiType = "vr:VrQoeReportType"
        qoeReport.vrMetric = metrics
        qoeReport.vrMetricSchemaVersion = version
        val reception = ReceptionReport(qoeReport = qoeReport, contentUri = "http://example.com/a.mpd")
        reception.schemaVersion = "TSG105-Rel18"
        reception.xmlns = "urn:3gpp:metadata:2017:HSD:receptionreport"
        reception.xsi = "http://www.w3.org/2001/XMLSchema-instance"
        reception.sv = "urn:3gpp:metadata:2016:PSS:schemaVersion"
        reception.sup = "urn:3gpp:metadata:2016:PSS:SupplementQoEMetric"
        reception.vr = VrMetrics.NAMESPACE
        reception.clientId = "client-1"
        reception.delimiter = Delimiter()
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + XmlMapper().writeValueAsString(reception)
    }
}
