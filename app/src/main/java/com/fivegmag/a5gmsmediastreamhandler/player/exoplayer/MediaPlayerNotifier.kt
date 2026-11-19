package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.HttpDataSource
import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerErrorReasons
import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerNotificationEvents

/** A notification of the Media Player (TS 26.512 V19.3.0 table 13.2.5-1), disambiguated by the media delivery session identifier; errorReason only for PLAYBACK_ERROR */
data class MediaPlayerNotification(val event: String, val mediaDeliverySessionId: String?, val errorReason: String? = null)

fun interface MediaPlayerNotificationListener {
    fun onNotification(notification: MediaPlayerNotification)
}

/**
 * The notifications of the Media Player (TS 26.512 V19.3.0 clause 13.2.5) that the events of Media3 define unambiguously: PLAYBACK_PLAYING when the media
 * begins to play, PLAYBACK_PAUSED when playback is paused, PLAYBACK_STARTED when it is resumed after a pause, PLAYBACK_ENDED, PLAYBACK_SEEKING and
 * PLAYBACK_SEEKED around a seek, CAN_PLAY when enough data is available, MANIFEST_LOADED when the manifest is loaded, and PLAYBACK_ERROR with a
 * reason of table 13.2.5-2. The buffer events (BUFFER_EMPTY, BUFFER_LOADED, PLAYBACK_STALLED, PLAYBACK_WAITING) are not given: their definitions
 * overlap and the text does not say which Media3 event each is.
 */
class MediaPlayerNotifier(
    private val player: Player,
    private val mediaDeliverySessionId: () -> String?,
    private val emit: (MediaPlayerNotification) -> Unit
) : Player.Listener {
    private var paused = false
    private var seeking = false
    private var canPlaySent = false

    /** The media is replaced or removed: the next one can play again */
    fun mediaReset() {
        paused = false
        seeking = false
        canPlaySent = false
    }

    fun notify(event: String, errorReason: String? = null) {
        emit(MediaPlayerNotification(event, mediaDeliverySessionId(), errorReason))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            notify(MediaPlayerNotificationEvents.PLAYBACK_PLAYING)
            if (paused) {
                paused = false
                notify(MediaPlayerNotificationEvents.PLAYBACK_STARTED)
            }
        } else if (player.playbackState == Player.STATE_READY && !player.playWhenReady) {
            paused = true
            notify(MediaPlayerNotificationEvents.PLAYBACK_PAUSED)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_ENDED -> notify(MediaPlayerNotificationEvents.PLAYBACK_ENDED)
            Player.STATE_READY -> {
                if (!canPlaySent) {
                    canPlaySent = true
                    notify(MediaPlayerNotificationEvents.CAN_PLAY)
                }
                if (seeking) {
                    seeking = false
                    notify(MediaPlayerNotificationEvents.PLAYBACK_SEEKED)
                }
            }
        }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            seeking = true
            notify(MediaPlayerNotificationEvents.PLAYBACK_SEEKING)
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE && player.currentManifest != null) {
            notify(MediaPlayerNotificationEvents.MANIFEST_LOADED)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        notify(MediaPlayerNotificationEvents.PLAYBACK_ERROR, errorReasonOf(error, player.currentManifest != null))
    }

    companion object {
        /**
         * The error reason of table 13.2.5-2 for an error of Media3: a 404 or 410 is ERROR_MEDIA_ENTRY_NOT_FOUND while there is no manifest yet and
         * ERROR_CONTENT_NOT_FOUND afterwards; a manifest that cannot be parsed is ERROR_INVALID_MEDIA_ENTRY, one that uses what the player does not
         * support ERROR_UNSUPPORTED_PROFILE; any other error is one of the media playback platform, ERROR_MEDIA_PLAYBACK.
         */
        fun errorReasonOf(error: PlaybackException, manifestLoaded: Boolean): String {
            val response = generateSequence(error.cause) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
            return when {
                response != null && (response.responseCode == 404 || response.responseCode == 410) ->
                    if (manifestLoaded) MediaPlayerErrorReasons.ERROR_CONTENT_NOT_FOUND else MediaPlayerErrorReasons.ERROR_MEDIA_ENTRY_NOT_FOUND
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> MediaPlayerErrorReasons.ERROR_INVALID_MEDIA_ENTRY
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> MediaPlayerErrorReasons.ERROR_UNSUPPORTED_PROFILE
                else -> MediaPlayerErrorReasons.ERROR_MEDIA_PLAYBACK
            }
        }
    }
}
