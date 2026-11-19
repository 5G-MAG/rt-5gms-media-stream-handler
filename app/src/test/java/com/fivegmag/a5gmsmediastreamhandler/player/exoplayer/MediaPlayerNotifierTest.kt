package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerErrorReasons
import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerNotificationEvents as Events
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The notifications of the Media Player of TS 26.512 V19.3.0 clause 13.2.5 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaPlayerNotifierTest {
    private val player = mockk<Player>(relaxed = true)
    private val notifications = ArrayList<MediaPlayerNotification>()
    private val notifier = MediaPlayerNotifier(player, { "sid-1" }) { notifications.add(it) }

    private fun events() = notifications.map { it.event }

    @Test
    fun everyNotificationCitesTheMediaDeliverySessionIdentifier() {
        notifier.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(listOf(MediaPlayerNotification(Events.PLAYBACK_ENDED, "sid-1")), notifications)
    }

    // Table 13.2.5-1: PLAYBACK_PLAYING when the media begins to play, PLAYBACK_PAUSED when it is paused, PLAYBACK_STARTED when it is resumed after a pause
    @Test
    fun playingPausedAndResumed() {
        notifier.onIsPlayingChanged(true)
        every { player.playbackState } returns Player.STATE_READY
        every { player.playWhenReady } returns false
        notifier.onIsPlayingChanged(false)
        notifier.onIsPlayingChanged(true)
        assertEquals(listOf(Events.PLAYBACK_PLAYING, Events.PLAYBACK_PAUSED, Events.PLAYBACK_PLAYING, Events.PLAYBACK_STARTED), events())
    }

    // a stop of playback that is not a pause (buffering) is not PLAYBACK_PAUSED
    @Test
    fun playbackThatStopsForBufferingIsNotPaused() {
        every { player.playbackState } returns Player.STATE_BUFFERING
        every { player.playWhenReady } returns true
        notifier.onIsPlayingChanged(false)
        assertEquals(emptyList<String>(), events())
    }

    @Test
    fun canPlayIsSentOnceAndSeekingIsFollowedBySeeked() {
        notifier.onPlaybackStateChanged(Player.STATE_READY)
        notifier.onPlaybackStateChanged(Player.STATE_READY)
        val position = Player.PositionInfo(null, 0, null, null, 0, 0L, 0L, -1, -1)
        notifier.onPositionDiscontinuity(position, position, Player.DISCONTINUITY_REASON_SEEK)
        notifier.onPlaybackStateChanged(Player.STATE_BUFFERING)
        notifier.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(listOf(Events.CAN_PLAY, Events.PLAYBACK_SEEKING, Events.PLAYBACK_SEEKED), events())
    }

    @Test
    fun aDiscontinuityThatIsNotASeekIsNotNotified() {
        val position = Player.PositionInfo(null, 0, null, null, 0, 0L, 0L, -1, -1)
        notifier.onPositionDiscontinuity(position, position, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        assertEquals(emptyList<String>(), events())
    }

    @Test
    fun theManifestIsLoadedWhenTheSourceIsUpdatedAndThereIsAManifest() {
        every { player.currentManifest } returns Any()
        notifier.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        assertEquals(emptyList<String>(), events())
        notifier.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        assertEquals(listOf(Events.MANIFEST_LOADED), events())
    }

    private fun httpError(code: Int) = PlaybackException(
        "bad status",
        HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), DataSpec(Uri.parse("http://as.example/a.mpd")), ByteArray(0)),
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    )

    // Table 13.2.5-2: the reasons of PLAYBACK_ERROR
    @Test
    fun anErrorCitesAReasonOfTable13_2_5_2() {
        assertEquals(MediaPlayerErrorReasons.ERROR_MEDIA_ENTRY_NOT_FOUND, MediaPlayerNotifier.errorReasonOf(httpError(404), manifestLoaded = false))
        assertEquals(MediaPlayerErrorReasons.ERROR_CONTENT_NOT_FOUND, MediaPlayerNotifier.errorReasonOf(httpError(404), manifestLoaded = true))
        assertEquals(MediaPlayerErrorReasons.ERROR_MEDIA_PLAYBACK, MediaPlayerNotifier.errorReasonOf(httpError(503), manifestLoaded = true))
        assertEquals(
            MediaPlayerErrorReasons.ERROR_INVALID_MEDIA_ENTRY,
            MediaPlayerNotifier.errorReasonOf(PlaybackException("bad", null, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED), false)
        )
        assertEquals(
            MediaPlayerErrorReasons.ERROR_UNSUPPORTED_PROFILE,
            MediaPlayerNotifier.errorReasonOf(PlaybackException("bad", null, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED), false)
        )
        assertEquals(
            MediaPlayerErrorReasons.ERROR_MEDIA_PLAYBACK,
            MediaPlayerNotifier.errorReasonOf(PlaybackException("bad", null, PlaybackException.ERROR_CODE_DECODING_FAILED), true)
        )
    }

    @Test
    fun aPlayerErrorIsPlaybackErrorWithItsReason() {
        every { player.currentManifest } returns null
        notifier.onPlayerError(httpError(404))
        assertEquals(listOf(MediaPlayerNotification(Events.PLAYBACK_ERROR, "sid-1", MediaPlayerErrorReasons.ERROR_MEDIA_ENTRY_NOT_FOUND)), notifications)
    }

    // Implementation choice, text silent on the mapping, see CR-042 (O-18): PLAYBACK_WAITING is the rebuffering of a player that was playing; the
    // buffering before the first play and the buffering of a seek are not
    @Test
    fun playbackThatStopsForALackOfDataIsWaiting() {
        every { player.playWhenReady } returns true
        notifier.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(emptyList<String>(), events())
        notifier.onIsPlayingChanged(true)
        notifier.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(listOf(Events.PLAYBACK_PLAYING, Events.PLAYBACK_WAITING), events())
    }

    @Test
    fun theBufferingOfASeekIsNotWaiting() {
        every { player.playWhenReady } returns true
        notifier.onIsPlayingChanged(true)
        val position = Player.PositionInfo(null, 0, null, null, 0, 0L, 0L, -1, -1)
        notifier.onPositionDiscontinuity(position, position, Player.DISCONTINUITY_REASON_SEEK)
        notifier.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(listOf(Events.PLAYBACK_PLAYING, Events.PLAYBACK_SEEKING), events())
    }
}
