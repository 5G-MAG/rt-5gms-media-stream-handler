package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Bundle
import android.os.Message
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.controller.IConsumptionReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.IQoEMetricsReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.ISessionController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IncomingMessageHandlerTest {
    private val consumption = mockk<IConsumptionReportingController>(relaxed = true)
    private val qoe = mockk<IQoEMetricsReportingController>(relaxed = true)
    private val session = mockk<ISessionController>(relaxed = true)
    private val outgoing = mockk<OutgoingMessageHandler>()
    private val incoming = IncomingMessageHandler()

    @Before
    fun setUp() {
        incoming.initialize(consumption, qoe, session, outgoing)
    }

    @Test
    fun queuedResponseFromPreviousPlaybackCannotRestartIt() {
        every { outgoing.currentPlaybackId } returns "new-playback"

        receivePlayback("old-playback")

        verify(exactly = 0) { consumption.handleTriggerPlayback(any()) }
        verify(exactly = 0) { qoe.handleTriggerPlayback(any()) }
        verify(exactly = 0) { session.handleTriggerPlayback(any()) }
    }

    @Test
    fun responseAfterExplicitEndCannotRestartPlayback() {
        every { outgoing.currentPlaybackId } returns null

        receivePlayback("ended-playback")

        verify(exactly = 0) { session.handleTriggerPlayback(any()) }
    }

    @Test
    fun matchingPlaybackResponseInitializesAllControllers() {
        every { outgoing.currentPlaybackId } returns "current-playback"

        receivePlayback("current-playback")

        verify { consumption.handleTriggerPlayback(match { it.playbackId == "current-playback" }) }
        verify { qoe.handleTriggerPlayback(match { it.playbackId == "current-playback" }) }
        verify { session.handleTriggerPlayback(match { it.playbackId == "current-playback" }) }
    }

    private fun receivePlayback(playbackId: String) {
        val request = PlaybackRequest(
            arrayListOf(),
            ConsumptionRequest(),
            arrayListOf(),
            "550e8400-e29b-41d4-a716-446655440000",
            playbackId
        )
        incoming.IncomingHandler().handleMessage(Message.obtain().apply {
            what = SessionHandlerMessageTypes.TRIGGER_PLAYBACK
            data = Bundle().apply { putParcelable("playbackRequest", request) }
        })
    }
}
