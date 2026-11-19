package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
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
}
