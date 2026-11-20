package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExoPlayerAdapterTest {

    private lateinit var adapter: ExoPlayerAdapter

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        adapter = ExoPlayerAdapter()
        adapter.initialize(mockk<PlayerView>(relaxed = true), context)
    }

    private fun attach(url: String) {
        adapter.attach(url, ContentTypes.DASH, "test-session-id", "test-playback-id")
    }

    @After
    fun tearDown() {
        adapter.destroy()
    }

    @Test
    fun seek_movesPlaybackPositionToGivenMilliseconds() {
        attach("https://example.com/manifest.mpd")

        adapter.seek(5000L)

        assertEquals(5000L, adapter.getCurrentPosition())
    }

    @Test
    fun reset_clearsActiveMediaItemAndStopsPlayer() {
        attach("https://example.com/manifest.mpd")
        adapter.preload()
        assertTrue(adapter.hasActiveMediaItem())

        adapter.reset()

        assertFalse(adapter.hasActiveMediaItem())
        assertEquals("", adapter.getCurrentManifestUri())
        assertEquals(0, adapter.getPlayerInstance().mediaItemCount)
        assertEquals(Player.STATE_IDLE, adapter.getPlaybackState())
    }

    @Test
    fun getCurrentManifestUrl_afterReset_returnsEmptyString() {
        attach("https://example.com/manifest.mpd")
        assertEquals("https://example.com/manifest.mpd", adapter.getCurrentManifestUrl())

        adapter.reset()

        assertEquals("", adapter.getCurrentManifestUrl())
    }

    @Test
    fun reset_keepsPlayerUsableForNextMediaItem() {
        attach("https://example.com/first.mpd")
        adapter.reset()

        attach("https://example.com/second.mpd")

        assertTrue(adapter.hasActiveMediaItem())
        assertEquals("https://example.com/second.mpd", adapter.getCurrentManifestUri())
        assertEquals(1, adapter.getPlayerInstance().mediaItemCount)
    }

    @Test
    fun reset_nextMediaItemDoesNotStartPlayingWithoutPlay() {
        attach("https://example.com/first.mpd")
        adapter.preload()
        adapter.play()

        adapter.reset()
        attach("https://example.com/second.mpd")
        adapter.preload()

        assertFalse(adapter.getPlayerInstance().playWhenReady)
    }

    @Test
    fun reset_restoresDefaultPlaybackSpeed() {
        attach("https://example.com/first.mpd")
        adapter.getPlayerInstance().playbackParameters = PlaybackParameters(1.5f)

        adapter.reset()

        assertEquals(PlaybackParameters.DEFAULT, adapter.getPlayerInstance().playbackParameters)
    }

    @Test
    fun reset_beforeAnyMediaItemIsAttached_doesNotThrow() {
        adapter.reset()

        assertFalse(adapter.hasActiveMediaItem())
    }
}
