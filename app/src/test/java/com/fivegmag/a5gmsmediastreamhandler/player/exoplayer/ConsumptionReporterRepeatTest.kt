package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.Format
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import io.mockk.every
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TS 26.510 V19.2.0 clause 5.3.6.1: a consumption reporting unit is included in exactly one report, except the one that describes the media being
 * consumed, which "shall be repeated as the first (and possibly only) consumption reporting unit in the next report with the same start time"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConsumptionReporterRepeatTest {
    private val bus = EventBus.builder().build()
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true)
    private val reporter: ConsumptionReporterExoplayer

    init {
        every { adapter.eventBus } returns bus
        every { adapter.getContentIdentifier() } returns null
        reporter = ConsumptionReporterExoplayer(adapter)
        reporter.initialize()
    }

    @After
    fun tearDown() = reporter.reset()

    private fun selected(representationId: String) {
        val format = Format.Builder().setId(representationId).setContainerMimeType("video/mp4").build()
        bus.post(DownstreamFormatChangedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), MediaLoadData(1, 2, format, 0, null, 0L, 0L)))
    }

    // what the controller does when it sends a report: the report, then resetState()
    private fun sendReport(): List<JsonNode> {
        val units = ObjectMapper().readTree(reporter.getConsumptionReport("client", ConsumptionRequest())).get("consumptionReportingUnits").toList()
        reporter.resetState()
        return units
    }

    @Test
    fun theUnitBeingConsumedIsRepeatedWithTheSameStartTime() {
        selected("v1")
        val first = sendReport()
        val second = sendReport()
        assertEquals(listOf("v1"), first.map { it.get("mediaConsumed").asText() })
        assertEquals(listOf("v1"), second.map { it.get("mediaConsumed").asText() })
        assertEquals(first[0].get("startTime").asText(), second[0].get("startTime").asText())
    }

    @Test
    fun aUnitThatEndedIsReportedOnceAndTheNewOneContinues() {
        selected("v1")
        sendReport()
        selected("v2")
        assertEquals(listOf("v1", "v2"), sendReport().map { it.get("mediaConsumed").asText() })
        assertEquals(listOf("v2"), sendReport().map { it.get("mediaConsumed").asText() })
    }

    // Table 9.6.3.2-1: "Media consumed for less than 1 second should be reported with duration = 0". The text wins over the exclusiveMinimum of the YAML.
    @Test
    fun mediaConsumedForLessThanASecondIsReportedWithDurationZero() {
        selected("v1")
        assertEquals(0, sendReport()[0].get("duration").asInt())
    }
}
