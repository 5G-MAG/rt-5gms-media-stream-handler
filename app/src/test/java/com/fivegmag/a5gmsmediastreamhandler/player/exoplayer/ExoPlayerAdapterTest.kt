package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

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

    @After
    fun tearDown() {
        adapter.destroy()
    }

    @Test
    fun seek_movesPlaybackPositionToGivenMilliseconds() {
        adapter.attach("https://example.com/manifest.mpd", ContentTypes.DASH)

        adapter.seek(5000L)

        assertEquals(5000L, adapter.getCurrentPosition())
    }

    @Test
    fun reset_clearsActiveMediaItemAndStopsPlayer() {
        adapter.attach("https://example.com/manifest.mpd", ContentTypes.DASH)
        adapter.preload()
        assertTrue(adapter.hasActiveMediaItem())

        adapter.reset()

        assertFalse(adapter.hasActiveMediaItem())
        assertEquals("", adapter.getCurrentManifestUri())
        assertEquals(0, adapter.getPlayerInstance().mediaItemCount)
        assertEquals(Player.STATE_IDLE, adapter.getPlaybackState())
    }

    @Test
    fun reset_keepsPlayerUsableForNextMediaItem() {
        adapter.attach("https://example.com/first.mpd", ContentTypes.DASH)
        adapter.reset()

        adapter.attach("https://example.com/second.mpd", ContentTypes.DASH)

        assertTrue(adapter.hasActiveMediaItem())
        assertEquals("https://example.com/second.mpd", adapter.getCurrentManifestUri())
        assertEquals(1, adapter.getPlayerInstance().mediaItemCount)
    }

    @Test
    fun reset_beforeAnyMediaItemIsAttached_doesNotThrow() {
        adapter.reset()

        assertFalse(adapter.hasActiveMediaItem())
    }
}
