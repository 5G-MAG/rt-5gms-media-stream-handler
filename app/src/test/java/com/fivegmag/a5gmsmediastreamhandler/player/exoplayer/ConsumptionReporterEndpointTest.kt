package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadStartedEvent
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
 * The server and client endpoints of a consumption reporting unit (TS 26.510 table 9.6.3.2-1, both 0..1). A unit that starts after a switch of the
 * Representation and after a report has the server endpoint that the player last used for the media type, and a unit with no known server endpoint
 * has no client endpoint instead of an invented port (the schema of the endpoint wants at least one port number).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConsumptionReporterEndpointTest {
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

    private fun format(id: String) = Format.Builder().setId(id).setContainerMimeType("video/mp4").build()

    private fun load(url: String) {
        val uri = Uri.parse(url)
        val info = LoadEventInfo(0L, DataSpec(uri), uri, emptyMap(), 0L, 0L, 0L)
        bus.post(LoadStartedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), info, MediaLoadData(1, 2, format("v"), 0, null, 0L, 0L)))
    }

    private fun representationSelected(id: String) =
        bus.post(DownstreamFormatChangedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), MediaLoadData(1, 2, format(id), 0, null, 0L, 0L)))

    private fun units(): List<JsonNode> =
        ObjectMapper().readTree(reporter.getConsumptionReport("client", ConsumptionRequest(accessReporting = true))).get("consumptionReportingUnits").toList()

    private fun unit(mediaConsumed: String) = units().first { it.get("mediaConsumed").asText() == mediaConsumed }

    @Test
    fun aUnitHasTheServerEndpointOfTheLastLoadAndTheClientEndpointWithTheSamePort() {
        load("http://10.0.0.1:8080/video/seg-1.m4s")
        representationSelected("1")
        val u = unit("movie|1")
        assertEquals("10.0.0.1", u.get("serverEndpointAddress").get("domainName").asText())
        assertEquals(listOf(8080), u.get("serverEndpointAddress").get("portNumbers").map { it.asInt() })
        assertEquals(listOf(8080), u.get("clientEndpointAddress").get("portNumbers").map { it.asInt() })
    }

    @Test
    fun aUnitWithoutAKnownServerEndpointHasNoEndpointsAndNoInventedPort() {
        representationSelected("1")
        val u = unit("movie|1")
        assertFalse(u.has("serverEndpointAddress"))
        assertFalse(u.has("clientEndpointAddress"))
    }

    @Test
    fun aUnitThatStartsAfterAReportKeepsTheServerEndpointOfItsMediaType() {
        load("http://10.0.0.1:8080/video/seg-1.m4s")
        representationSelected("1")
        reporter.getConsumptionReport("client", ConsumptionRequest(accessReporting = true))
        reporter.resetState()   // what the controller does after a report was sent
        representationSelected("2")
        val u = unit("movie|2")
        assertEquals(listOf(8080), u.get("serverEndpointAddress").get("portNumbers").map { it.asInt() })
        assertTrue(u.has("clientEndpointAddress"))
    }

    @Test
    fun theEndpointsAreForgottenWhenThePlaybackSessionIsReset() {
        load("http://10.0.0.1:8080/video/seg-1.m4s")
        reporter.reset()
        representationSelected("1")
        assertFalse(unit("movie|1").has("serverEndpointAddress"))
    }

    @Test
    fun theEndpointsFollowTheLastLoad() {
        load("http://10.0.0.1:8080/video/seg-1.m4s")
        load("http://10.0.0.2:8081/video/seg-2.m4s")
        representationSelected("1")
        val u = unit("movie|1")
        assertEquals("10.0.0.2", u.get("serverEndpointAddress").get("domainName").asText())
        assertEquals(listOf(8081), u.get("clientEndpointAddress").get("portNumbers").map { it.asInt() })
    }
}
