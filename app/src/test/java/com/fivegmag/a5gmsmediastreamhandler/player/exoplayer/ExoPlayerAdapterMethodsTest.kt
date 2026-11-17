package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Media Player methods seek() and reset() of TS 26.512 V19.3.0 clauses 13.2.3.7 and 13.2.3.8 */
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
}
