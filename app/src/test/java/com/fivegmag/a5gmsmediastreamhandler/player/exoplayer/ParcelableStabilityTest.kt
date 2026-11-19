package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fivegmag.a5gmsmediastreamhandler.controller.ConsumptionReportingFilterProvider
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReport
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReportingUnit
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadStartedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.ignoreParcelableStability
import com.fivegmag.a5gmscommonlibrary.models.CellIdentifierType
import com.fivegmag.a5gmscommonlibrary.models.EndpointAddress
import com.fivegmag.a5gmscommonlibrary.models.Snssai
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import io.mockk.every
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * android.os.Parcelable has a public getStability() since API 29 (value 0). Jackson took it for a bean property of every Parcelable data class, so the
 * Consumption Report carried "stability":0 in the report, in each unit and in each endpoint address, none of which the TS 26.510 V19.2.0 tables 9.6.3.1-1,
 * 9.6.3.2-1 and 7.3.3.11-1 define. The clients write the JSON and the XML with a mapper that leaves the property out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ParcelableStabilityTest {
    private val bus = EventBus.builder().build()
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true)
    private val reporter: ConsumptionReporterExoplayer

    init {
        every { adapter.eventBus } returns bus
        every { adapter.getContentIdentifier() } returns "movie"
        reporter = ConsumptionReporterExoplayer(adapter)
        reporter.initialize()
    }

    @After
    fun tearDown() = reporter.reset()

    private fun names(node: JsonNode): Set<String> =
        node.fieldNames().asSequence().toSet() + node.flatMap { names(it) }

    private fun report(): JsonNode {
        val format = Format.Builder().setId("v").setContainerMimeType("video/mp4").build()
        val uri = Uri.parse("http://10.0.0.1:8080/video/seg-1.m4s")
        val loadInfo = LoadEventInfo(0L, DataSpec(uri), uri, emptyMap(), 0L, 0L, 0L)
        bus.post(LoadStartedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), loadInfo, MediaLoadData(1, 2, format, 0, null, 0L, 0L)))
        bus.post(DownstreamFormatChangedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), MediaLoadData(1, 2, format, 0, null, 0L, 0L)))
        return ObjectMapper().readTree(reporter.getConsumptionReport("client", ConsumptionRequest(accessReporting = true)))
    }

    @Test
    fun theConsumptionReportHasNoStabilityProperty() {
        val node = report()
        assertEquals(setOf("mediaPlayerEntry", "reportingClientId", "consumptionReportingUnits"), node.fieldNames().asSequence().toSet())
        assertTrue(node.get("consumptionReportingUnits").get(0).has("serverEndpointAddress"))
        assertFalse(names(node).contains("stability"))
    }

    private val ignoring = jacksonObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL).ignoreParcelableStability()
    private val plain = jacksonObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)

    private fun reportWithLocationsAndSlice(): ConsumptionReport {
        val endpoint = EndpointAddress("10.0.0.1", listOf(8080))
        val unit = ConsumptionReportingUnit(
            "movie|v", endpoint, endpoint, "2026-11-19T17:00:00Z", 3, arrayListOf(TypedLocation(CellIdentifierType.NCGI, "123")),
            sliceInfo = Snssai(1, "00007B"), dataNetworkName = "internet"
        )
        return ConsumptionReport("http://10.0.0.1/m.mpd", "client", arrayListOf(unit))
    }

    @Test
    fun noParcelableOfTheReportCarriesTheProperty() {
        val node = ObjectMapper().readTree(ignoring.writer(ConsumptionReportingFilterProvider.createFilter(emptyList())).writeValueAsString(reportWithLocationsAndSlice()))
        assertTrue(names(node).containsAll(setOf("locations", "sliceInfo", "dataNetworkName", "locationIdentifierType")))
        assertFalse(names(node).contains("stability"))
    }

    // The cause: without the mixin the property is the getStability() of android.os.Parcelable (API 29)
    @Test
    fun withoutTheMixInTheParcelableMethodIsWrittenAsStability() {
        val node = ObjectMapper().readTree(plain.writer(ConsumptionReportingFilterProvider.createFilter(emptyList())).writeValueAsString(reportWithLocationsAndSlice()))
        assertEquals(0, node.get("stability").asInt())
        assertEquals(0, node.get("consumptionReportingUnits").get(0).get("serverEndpointAddress").get("stability").asInt())
        assertEquals(0, node.get("consumptionReportingUnits").get(0).get("locations").get(0).get("stability").asInt())
        assertEquals(0, node.get("consumptionReportingUnits").get(0).get("sliceInfo").get("stability").asInt())
    }

    @Test
    fun theQoeReceptionReportHasNoStabilityElement() {
        val qoe = QoeMetricsReporterExoplayer(adapter)
        try {
            val request = mockk<QoeMetricsRequest>(relaxed = true)
            every { request.metrics } returns ArrayList()
            every { request.reportingInterval } returns 5000L
            qoe.initialize(request)
            val xml = qoe.getQoeMetricsReport(request, "client-1", "deadbeef1234")
            assertTrue(xml.contains("ReceptionReport"))
            assertFalse(xml.contains("stability", ignoreCase = true))
        } finally {
            qoe.reset()
        }
    }
}
