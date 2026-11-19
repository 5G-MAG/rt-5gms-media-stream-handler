package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerErrorReasons
import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerNotificationEvents
import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerStates
import io.mockk.verify
import com.fivegmag.a5gmscommonlibrary.helpers.StatusInformation
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Media Player methods seek() and reset() of TS 26.512 V19.3.0 clauses 13.2.3.7 and 13.2.3.8, and its status information of clause 13.2.6 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@UnstableApi
class ExoPlayerAdapterMethodsTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val adapter = ExoPlayerAdapter().also { set(it, "playerInstance", player) }

    private fun set(target: Any, name: String, value: Any?) = target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.set(target, value)

    @Test
    fun seekGoesToTheMediaTimeAndPlays() {
        every { player.currentTimeline } returns Timeline.EMPTY
        adapter.seek(5_000L)
        verifyOrder {
            player.seekTo(5_000L)
            player.play()
        }
    }

    @Test
    fun resetEndsPlaybackAndRemovesTheMedia() {
        set(adapter, "activeMediaItem", MediaItem.fromUri("http://example.com/a.mpd"))
        assertTrue(adapter.hasActiveMediaItem())
        adapter.reset()
        verifyOrder {
            player.stop()
            player.clearMediaItems()
        }
        assertFalse(adapter.hasActiveMediaItem())
    }

    // Table 13.2.6-1: bufferLength and liveLatency are in seconds (Media3 gives milliseconds)
    @Test
    fun bufferLengthAndLiveLatencyAreInSeconds() {
        every { player.currentTracks } returns Tracks(listOf(mockk(relaxed = true)))
        every { player.totalBufferedDuration } returns 12_500L
        every { player.currentLiveOffset } returns 3_250L
        assertEquals(12.5, adapter.getStatusInformation(StatusInformation.BUFFER_LENGTH) as Double, 0.0)
        assertEquals(3.25, adapter.getStatusInformation(StatusInformation.LIVE_LATENCY) as Double, 0.0)
    }

    // Table 13.2.6-1: NaN is returned for the buffer length when the presentation has no adaptation sets
    @Test
    fun bufferLengthWithoutAnyTracksIsNaN() {
        every { player.currentTracks } returns Tracks.EMPTY
        every { player.totalBufferedDuration } returns 0L
        assertTrue((adapter.getStatusInformation(StatusInformation.BUFFER_LENGTH) as Double).isNaN())
    }

    @Test
    fun liveLatencyWithoutAMeasurementIsNotAValue() {
        every { player.currentLiveOffset } returns C.TIME_UNSET
        assertNull(adapter.getStatusInformation(StatusInformation.LIVE_LATENCY))
    }

    private val notifications = ArrayList<MediaPlayerNotification>()
    private fun withNotifier() = set(adapter, "notifier", MediaPlayerNotifier(player, { "sid" }) { notifications.add(it) })

    private fun timelineOf(durationMs: Long): Timeline {
        val timeline = mockk<Timeline>()
        every { timeline.isEmpty } returns false
        every { timeline.getWindow(any(), any()) } answers {
            secondArg<Timeline.Window>().also { it.durationUs = durationMs * 1000 }
        }
        return timeline
    }

    // Clause 13.2.3.7: a media time that is not accessible is an error (table 13.2.5-2) and the following actions are not carried out
    @Test
    fun seekToAMediaTimeThatIsNotAccessibleIsAnErrorAndDoesNothing() {
        withNotifier()
        every { player.currentTimeline } returns timelineOf(60_000L)
        adapter.seek(61_000L)
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        verify(exactly = 0) { player.play() }
        assertEquals(
            listOf(MediaPlayerNotification(MediaPlayerNotificationEvents.PLAYBACK_ERROR, "sid", MediaPlayerErrorReasons.ERROR_INACCESSIBLE_MEDIA_TIME)),
            notifications
        )
        notifications.clear()
        adapter.seek(60_000L)
        verify { player.seekTo(60_000L) }
        assertTrue(notifications.isEmpty())
    }

    // Table 13.2.3.1-1: the state after the success of each method
    @Test
    fun theMethodsMoveTheMediaPlayerThroughTheStatesOfTable13_2_2_1() {
        every { player.currentTimeline } returns Timeline.EMPTY
        set(adapter, "playerListener", mockk<ExoPlayerListener>(relaxed = true))
        assertEquals(MediaPlayerStates.IDLE, adapter.getMediaPlayerState())
        adapter.attach("http://example.com/a.mpd", "application/dash+xml")
        assertEquals(MediaPlayerStates.READY, adapter.getMediaPlayerState())
        adapter.preload()
        assertEquals(MediaPlayerStates.PRELOADED, adapter.getMediaPlayerState())
        adapter.play()
        assertEquals(MediaPlayerStates.PLAYING, adapter.getMediaPlayerState())
        adapter.pause()
        assertEquals(MediaPlayerStates.PAUSED, adapter.getMediaPlayerState())
        adapter.seek(1_000L)
        assertEquals(MediaPlayerStates.PLAYING, adapter.getMediaPlayerState())
        adapter.reset()
        assertEquals(MediaPlayerStates.INITIALIZED, adapter.getMediaPlayerState())
        adapter.destroy()
        assertEquals(MediaPlayerStates.IDLE, adapter.getMediaPlayerState())
        assertEquals(MediaPlayerStates.IDLE, adapter.getStatusInformation(StatusInformation.STATE))
    }

    // Table 13.2.6-1: mediaTime is in seconds, playbackRate is 2.0 for a double playback speed
    @Test
    fun mediaTimeIsInSecondsAndPlaybackRateIsTheSpeed() {
        every { player.currentPosition } returns 7_500L
        every { player.playbackParameters } returns PlaybackParameters(2.0f)
        assertEquals(7.5, adapter.getStatusInformation(StatusInformation.MEDIA_TIME) as Double, 0.0)
        assertEquals(2.0, adapter.getStatusInformation(StatusInformation.PLAYBACK_RATE) as Double, 0.0)
    }
}
