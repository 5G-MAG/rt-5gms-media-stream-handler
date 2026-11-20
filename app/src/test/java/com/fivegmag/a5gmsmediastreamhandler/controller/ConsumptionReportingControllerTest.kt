package com.fivegmag.a5gmsmediastreamhandler.controller

import android.os.Bundle
import android.os.Message
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConsumptionReportingControllerTest {
    private val player = mockk<IExoPlayerAdapter>()
    private val outgoing = mockk<OutgoingMessageHandler>(relaxed = true)
    private val reports = mutableListOf<String>()
    private lateinit var controller: ConsumptionReportingController
    private val sessionId = "550e8400-e29b-41d4-a716-446655440000"

    @Before
    fun setUp() {
        val eventBus = EventBus.builder().logNoSubscriberMessages(false).build()
        mockkStatic(EventBus::class)
        every { EventBus.getDefault() } returns eventBus
        every { player.getCurrentManifestUri() } returns "https://example.test/stream.mpd"
        every {
            outgoing.sendMessageByTypeAndBundle(SessionHandlerMessageTypes.CONSUMPTION_REPORT, any())
        } answers {
            reports.add(requireNotNull(secondArg<Bundle>().getString("consumptionReport")))
        }
        controller = ConsumptionReportingController(player, outgoing)
        controller.reportingClientId = "test-client"
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        controller.reset()
        unmockkStatic(EventBus::class)
    }

    @Test
    fun reportContainsTheAssignedSessionId() {
        startPlayback(sessionId, "first")

        controller.triggerConsumptionReport()

        val report = jacksonObjectMapper().readTree(reports.single())
        assertEquals(sessionId, report.get("sessionId").asText())
        assertEquals("test-client", report.get("reportingClientId").asText())
        assertEquals("https://example.test/stream.mpd", report.get("mediaPlayerEntry").asText())
        assertTrue(report.get("consumptionReportingUnits").isArray)
    }

    @Test
    fun playlistContinuationKeepsTheSessionIdInReports() {
        startPlayback(sessionId, "first")
        controller.triggerConsumptionReport()
        startPlayback(sessionId, "second")
        controller.triggerConsumptionReport()

        assertEquals(listOf(sessionId, sessionId), reportedSessionIds())
    }

    @Test
    fun newSessionUsesItsNewIdInReports() {
        val nextId = "550e8400-e29b-41d4-a716-446655440001"
        startPlayback(sessionId, "first")
        controller.triggerConsumptionReport()
        startPlayback(nextId, "second")
        controller.triggerConsumptionReport()

        assertEquals(listOf(sessionId, nextId), reportedSessionIds())
    }

    @Test
    fun reportingRequestBeforePlaybackLogsMissingSessionAndSendsNothing() {
        requestReport()

        assertMissingSessionError()
        verify(exactly = 0) { outgoing.sendMessageByTypeAndBundle(any(), any()) }
    }

    @Test
    fun reportingRequestAfterResetLogsMissingSessionAndSendsNothing() {
        startPlayback(sessionId, "first")
        controller.reset()

        requestReport()

        assertMissingSessionError()
        verify(exactly = 0) { outgoing.sendMessageByTypeAndBundle(any(), any()) }
    }

    @Test
    fun idleControllerDoesNotReportOrLogAnError() {
        controller.triggerConsumptionReport()

        verify(exactly = 0) { outgoing.sendMessageByTypeAndBundle(any(), any()) }
        assertTrue(ShadowLog.getLogsForTag(ConsumptionReportingController.TAG).isEmpty())
    }

    private fun startPlayback(id: String, playbackId: String) {
        controller.handleTriggerPlayback(
            PlaybackRequest(arrayListOf(), ConsumptionRequest(), arrayListOf(), id, playbackId)
        )
    }

    private fun requestReport() {
        controller.handleGetConsumptionReport(
            Message.obtain().apply {
                data = Bundle().apply { putParcelable("consumptionRequest", ConsumptionRequest()) }
            }
        )
    }

    private fun reportedSessionIds() = reports.map { jacksonObjectMapper().readTree(it).get("sessionId").asText() }

    private fun assertMissingSessionError() {
        assertTrue(
            ShadowLog.getLogsForTag(ConsumptionReportingController.TAG).any {
                it.type == Log.ERROR &&
                    it.msg == "Cannot generate a consumption report without a media delivery session ID"
            }
        )
    }
}
