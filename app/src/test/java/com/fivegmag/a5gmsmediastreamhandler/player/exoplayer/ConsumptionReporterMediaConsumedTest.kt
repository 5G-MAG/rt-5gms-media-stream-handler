package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.Format
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConsumptionReporterMediaConsumedTest {
    private val bus = EventBus.builder().build()
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true)
    private val reporter: ConsumptionReporterExoplayer

    init {
        every { adapter.eventBus } returns bus
        reporter = ConsumptionReporterExoplayer(adapter)
        reporter.initialize()
    }

    @After
    fun tearDown() = reporter.reset()

    private fun representationSelected(representationId: String?) {
        val format = Format.Builder().setId(representationId).setContainerMimeType("video/mp4").build()
        val loadData = MediaLoadData(1, 2, format, 0, null, 0L, 0L)
        bus.post(DownstreamFormatChangedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), loadData))
    }

    private fun mediaConsumed(): List<String> =
        ObjectMapper().readTree(reporter.getConsumptionReport("client", ConsumptionRequest()))
            .get("consumptionReportingUnits").map { it.get("mediaConsumed").asText() }

    // TS 26.512 V19.3.0 clause 11.3.3.2: content identifier and Representation@id, in that order, with a single "|"
    @Test
    fun theContentIdentifierAndTheRepresentationIdAreJoinedWithAPipe() {
        every { adapter.getContentIdentifier() } returns "movie-42"
        representationSelected("video-1080")
        assertEquals(listOf("movie-42|video-1080"), mediaConsumed())
    }

    @Test
    fun withoutAContentIdentifierOnlyTheRepresentationIdIsGiven() {
        every { adapter.getContentIdentifier() } returns null
        representationSelected("video-1080")
        assertEquals(listOf("video-1080"), mediaConsumed())
    }

    @Test
    fun anEmptyContentIdentifierIsNotAnIdentifier() {
        every { adapter.getContentIdentifier() } returns ""
        representationSelected("video-1080")
        assertEquals(listOf("video-1080"), mediaConsumed())
    }

    @Test
    fun theJoinKeepsTheOrderAndAddsOnePipeOnly() {
        assertEquals("c|r", ConsumptionReporterExoplayer.mediaConsumed("c", "r"))
        assertEquals("r", ConsumptionReporterExoplayer.mediaConsumed(null, "r"))
        assertEquals(null, ConsumptionReporterExoplayer.mediaConsumed("c", null))
    }
}
