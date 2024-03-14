package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadCompletedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadStartedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import org.greenrobot.eventbus.EventBus

@UnstableApi
class ExoPlayerListener(
    private val playerInstance: ExoPlayer,
    private val playerView: PlayerView,
) : AnalyticsListener {

    companion object {
        const val TAG = "5GMS-ExoPlayerListener"
    }

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        playbackState: Int
    ) {
        val state: String = mapStateToConstant(playbackState)

        playerView.keepScreenOn = !(state == PlayerStates.IDLE || state == PlayerStates.ENDED)
        Log.d(TAG, "Playback state changed to $state")
        EventBus.getDefault().post(PlaybackStateChangedEvent(eventTime, state))
    }

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        var state: String? = null
        if (isPlaying) {
            state = PlayerStates.PLAYING
        } else if (playerInstance.playbackState == Player.STATE_READY && !playerInstance.playWhenReady) {
            state = PlayerStates.PAUSED
        }
        if (state != null) {
            EventBus.getDefault().post(PlaybackStateChangedEvent(eventTime, state))
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onDownstreamFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        mediaLoadData: MediaLoadData
    ) {
        EventBus.getDefault().post(DownstreamFormatChangedEvent(eventTime, mediaLoadData))
    }

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        EventBus.getDefault().post(LoadStartedEvent(eventTime, loadEventInfo, mediaLoadData))
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        EventBus.getDefault().post(LoadCompletedEvent(eventTime, loadEventInfo, mediaLoadData))
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        Log.d("ExoPlayer", "Error")
    }

}