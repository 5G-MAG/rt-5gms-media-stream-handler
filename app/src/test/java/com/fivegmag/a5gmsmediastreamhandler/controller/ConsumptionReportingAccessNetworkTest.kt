package com.fivegmag.a5gmsmediastreamhandler.controller

import android.os.Bundle
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.models.Snssai
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TS 26.510 V19.2.0 clause 5.3.6.1 and table 9.6.3.2-1: a change of network slice or Data Network starts a new unit that carries them, if access is reported */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@UnstableApi
class ConsumptionReportingAccessNetworkTest {
    private val bus = EventBus.builder().build()
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true).also {
        every { it.eventBus } returns bus
        every { it.getContentIdentifier() } returns null
    }
    private val outgoing = mockk<OutgoingMessageHandler>(relaxed = true)
    private val reports = ArrayList<JsonNode>()
    private val controller = ConsumptionReportingController(adapter, outgoing)

    init {
        every { outgoing.sendMessageByTypeAndBundle(SessionHandlerMessageTypes.CONSUMPTION_REPORT, any()) } answers {
            reports.add(ObjectMapper().readTree(secondArg<Bundle>().getString("consumptionReport")))
        }
        controller.reportingClientId = "client"
        controller.initialize()
    }

    @After
    fun tearDown() = controller.reset()

    private fun start(accessReporting: Boolean) {
        controller.handleTriggerPlayback(PlaybackRequest(ArrayList(), ConsumptionRequest(accessReporting = accessReporting), ArrayList(), "s"))
        val format = Format.Builder().setId("v1").setContainerMimeType("video/mp4").build()
        bus.post(DownstreamFormatChangedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), MediaLoadData(1, 2, format, 0, null, 0L, 0L)))
    }

    private fun units(report: JsonNode): List<JsonNode> = report.get("consumptionReportingUnits").toList()

    @Test
    fun aNewSliceStartsANewUnitThatCarriesIt() {
        start(accessReporting = true)
        bus.post(AccessNetworkIdentityEvent(Snssai(1, "00007B"), "internet"))
        assertEquals(1, reports.size)
        val units = units(reports[0])
        assertEquals(2, units.size)
        assertFalse(units[0].has("sliceInfo"))
        assertFalse(units[0].has("dataNetworkName"))
        assertEquals(1, units[1].get("sliceInfo").get("sst").asInt())
        assertEquals("00007B", units[1].get("sliceInfo").get("sd").asText())
        assertEquals("internet", units[1].get("dataNetworkName").asText())
    }

    @Test
    fun aChangeOfTheDataNetworkAloneStartsANewUnit() {
        start(accessReporting = true)
        bus.post(AccessNetworkIdentityEvent(null, "internet"))
        bus.post(AccessNetworkIdentityEvent(null, "ims"))
        assertEquals(2, reports.size)
        // the unit that ended in the second report is the one of "internet"; the one that is current carries "ims"
        val last = units(reports[1])
        assertEquals("internet", last[0].get("dataNetworkName").asText())
        assertEquals("ims", last.last().get("dataNetworkName").asText())
    }

    @Test
    fun theSameValuesAgainStartNothing() {
        start(accessReporting = true)
        bus.post(AccessNetworkIdentityEvent(Snssai(1), "internet"))
        bus.post(AccessNetworkIdentityEvent(Snssai(1), "internet"))
        assertEquals(1, reports.size)
    }

    @Test
    fun withoutAccessReportingNoUnitStartsAndNoReportIsSent() {
        start(accessReporting = false)
        bus.post(AccessNetworkIdentityEvent(Snssai(1), "internet"))
        verify(exactly = 0) { outgoing.sendMessageByTypeAndBundle(SessionHandlerMessageTypes.CONSUMPTION_REPORT, any()) }
    }

    // the values that are known when a playback starts are in its units
    @Test
    fun aPlaybackThatStartsLaterCarriesTheKnownValues() {
        bus.post(AccessNetworkIdentityEvent(Snssai(2, "000001"), "internet"))
        start(accessReporting = true)
        controller.triggerConsumptionReport()
        val unit = units(reports[0]).single()
        assertEquals(2, unit.get("sliceInfo").get("sst").asInt())
        assertEquals("internet", unit.get("dataNetworkName").asText())
    }
}
