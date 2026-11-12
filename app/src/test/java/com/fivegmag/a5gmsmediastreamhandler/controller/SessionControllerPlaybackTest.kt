package com.fivegmag.a5gmsmediastreamhandler.controller

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionControllerPlaybackTest {
    private val player = mockk<IExoPlayerAdapter>(relaxed = true)
    private val outgoing = mockk<OutgoingMessageHandler>(relaxed = true)
    private val controller = SessionController(mockk<Context>(), player, outgoing)

    @Test
    fun playerReceivesThePlaybackIdSeparatelyFromTheDeliverySessionId() {
        val request = PlaybackRequest(
            arrayListOf(EntryPoint("https://example.test/item.mpd", ContentTypes.DASH, null)),
            ConsumptionRequest(),
            arrayListOf(),
            "550e8400-e29b-41d4-a716-446655440000",
            "playback-one"
        )
        assertEquals(1, request.entryPoints.size)
        assertEquals(ContentTypes.DASH, request.entryPoints.single().contentType)

        controller.handleTriggerPlayback(request)

        verify {
            player.attach(
                "https://example.test/item.mpd",
                ContentTypes.DASH,
                request.mediaDeliverySessionId,
                request.playbackId
            )
        }
    }

    @Test
    fun statusUsesTheOriginalEventsMediaItemInsteadOfTheCurrentPlayer() {
        val originalTimeline = SinglePeriodTimeline(
            1_000_000L, true, false, false, null,
            MediaItem.Builder().setMediaId("original-playback").build()
        )
        val currentTimeline = SinglePeriodTimeline(
            1_000_000L, true, false, false, null,
            MediaItem.Builder().setMediaId("current-playback").build()
        )
        val eventTime = AnalyticsListener.EventTime(
            0L, originalTimeline, 0, null, 0L, currentTimeline, 0, null, 0L, 0L
        )

        controller.onPlaybackStateChangedEvent(PlaybackStateChangedEvent(eventTime, PlayerStates.ENDED))

        verify { outgoing.updatePlaybackState(PlayerStates.ENDED, "original-playback") }
        verify(exactly = 0) { player.getPlayerInstance() }
    }

    @Test
    fun statusWithoutAnOriginalMediaItemIsNotAssignedToTheCurrentPlayback() {
        val eventTime = AnalyticsListener.EventTime(
            0L, Timeline.EMPTY, 0, null, 0L, Timeline.EMPTY, 0, null, 0L, 0L
        )

        controller.onPlaybackStateChangedEvent(PlaybackStateChangedEvent(eventTime, PlayerStates.ENDED))

        verify(exactly = 0) { outgoing.updatePlaybackState(any(), any()) }
    }
}
