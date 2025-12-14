package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.PlayerView
import androidx.media3.common.C
import com.fivegmag.a5gmscommonlibrary.eventbus.BytesTransferredEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.FirstFrameRenderedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.FirstMediaSegmentRequestedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadCompletedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadStartedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackSpeedChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.SeekEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.VideoSizeChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import org.greenrobot.eventbus.EventBus

@UnstableApi
class ExoPlayerListener(
    private val playerInstance: ExoPlayer,
    private val playerView: PlayerView,
) : AnalyticsListener, TransferListener {

    companion object {
        const val TAG = "5GMS-ExoPlayerListener"
    }

    private var firstMediaSegmentRequested: Boolean = false
    private var firstFrameRendered: Boolean = false

    private var lastVideoWidth: Int = 0
    private var lastVideoHeight: Int = 0

    init {
        // Listen for layout changes to detect orientation/size changes for Device Information QoE metric
        playerView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newWidth = right - left
            val newHeight = bottom - top
            val oldWidth = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop

            // Only fire event if size actually changed
            if (newWidth != oldWidth || newHeight != oldHeight) {
                if (newWidth > 0 && newHeight > 0) {
                    lastVideoWidth = newWidth
                    lastVideoHeight = newHeight
                    val displayMetrics = playerView.context.resources.displayMetrics
                    Log.d(TAG, "Video size changed: ${newWidth}x${newHeight}")
                    EventBus.getDefault().post(
                        VideoSizeChangedEvent(
                            videoWidth = newWidth,
                            videoHeight = newHeight,
                            screenWidth = displayMetrics.widthPixels,
                            screenHeight = displayMetrics.heightPixels
                        )
                    )
                }
            }
        }
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

        // Fire FirstMediaSegmentRequestedEvent on first media segment load
        // Per TS 26.247: Initial playout delay starts from fetch of first media segment
        if (!firstMediaSegmentRequested && isMediaSegment(mediaLoadData.dataType)) {
            firstMediaSegmentRequested = true
            Log.d(TAG, "First media segment requested at realtimeMs: ${eventTime.realtimeMs}")
            EventBus.getDefault().post(FirstMediaSegmentRequestedEvent(eventTime.realtimeMs))
        }
    }

    /**
     * Check if the data type is a media segment (video/audio data).
     * Excludes initialization segments (C.DATA_TYPE_MEDIA_INITIALIZATION) to strictly
     * comply with TS 26.247 which requires the "first media Segment (or sub-segment)".
     */
    private fun isMediaSegment(dataType: Int): Boolean {
        return dataType == C.DATA_TYPE_MEDIA
    }
    
    /**
     * Called when the first video frame is rendered.
     * Used for Initial Playout Delay per TS 26.247 clause 10.2.5
     */
    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        if (!firstFrameRendered) {
            firstFrameRendered = true
            Log.d(TAG, "First frame rendered at realtimeMs: ${eventTime.realtimeMs}")
            EventBus.getDefault().post(FirstFrameRenderedEvent(eventTime.realtimeMs))
        }
    }

    /**
     * Reset state for new playback session
     */
    fun newPlaybackSession() {
        firstMediaSegmentRequested = false
        firstFrameRendered = false
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

    /**
     * Called when a position discontinuity occurs (e.g., seek, period transition).
     * Used for PlayList QoE metric per TS 26.247 clause 10.2.6
     */
    override fun onPositionDiscontinuity(
        eventTime: AnalyticsListener.EventTime,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        // Only track user-initiated seeks
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            Log.d(TAG, "Seek detected: ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms")
            EventBus.getDefault().post(
                SeekEvent(
                    eventTime = eventTime,
                    oldPositionMs = oldPosition.positionMs,
                    newPositionMs = newPosition.positionMs
                )
            )
        }
    }

    /**
     * Called when playback speed changes.
     * Used for PlayList QoE metric per TS 26.247 clause 10.2.6
     */
    override fun onPlaybackParametersChanged(
        eventTime: AnalyticsListener.EventTime,
        playbackParameters: androidx.media3.common.PlaybackParameters
    ) {
        Log.d(TAG, "Playback speed changed to: ${playbackParameters.speed}")
        EventBus.getDefault().post(
            PlaybackSpeedChangedEvent(
                eventTime = eventTime,
                playbackSpeed = playbackParameters.speed
            )
        )
    }

    // --- TransferListener implementation for incremental byte counting ---

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        // No-op: activity time is tracked via LoadStartedEvent/LoadCompletedEvent
    }

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        // No-op: activity time is tracked via LoadStartedEvent/LoadCompletedEvent
    }

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (isNetwork) {
            EventBus.getDefault().post(BytesTransferredEvent(bytesTransferred))
        }
    }

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        // No-op: activity time is tracked via LoadStartedEvent/LoadCompletedEvent
    }
}