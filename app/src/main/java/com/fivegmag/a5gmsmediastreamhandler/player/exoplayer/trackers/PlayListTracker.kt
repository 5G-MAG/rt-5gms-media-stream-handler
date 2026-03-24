package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackSpeedChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.SeekEvent
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.PlayList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.PlayListEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.PlayListTraceEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.StartType
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.StopReasonType
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Helper class for tracking PlayList QoE metric per TS 26.247 clause 10.2.7
 * 
 * Tracks playback sessions including:
 * - Start types (NewPlayoutRequest, Resume, OtherUserRequest, StartOfMetricsCollectionPeriod)
 * - Trace entries with representation switches, speed changes, rebuffering
 * - Stop reasons (UserRequest, EndOfContent, RepresentationSwitch, Rebuffering, etc.)
 */
@UnstableApi
class PlayListTracker(
    private val exoPlayerAdapter: IExoPlayerAdapter
) {
    private val utils: Utils = Utils()
    
    // PlayList tracking state
    private val playList: PlayList = PlayList(ArrayList())
    private var currentPlayListEntry: PlayListEntry? = null
    // Active tracks state (maps ExoPlayer trackType to its state)
    private data class TrackTraceState(
        var representationId: String? = null,
        var startTime: String? = null,
        var startTimestamp: Long = 0L,
        var mediaStartMs: Long = 0L
    )
    private val activeTracks = mutableMapOf<Int, TrackTraceState>()
    private var currentPlaybackSpeed: Double = 1.0
    private var isPlaybackActive: Boolean = false

    companion object {
        const val TAG = "5GMS-PlayListTracker"
    }

    /**
     * Initialize event subscriptions
     */
    fun initialize() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    /**
     * Unregister from EventBus
     */
    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    /**
     * Handle seek events for PlayList metric
     * Per TS 26.247 clause 10.2.6: Creates new PlayListEntry with SEEK start type
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSeekEvent(seekEvent: SeekEvent) {
        val oldPositionMs = seekEvent.oldPositionMs
        val newPositionMs = seekEvent.newPositionMs
        Log.d(TAG, "Seek event: ${oldPositionMs}ms -> ${newPositionMs}ms")
        
        // Finalize current trace entries if active
        if (isPlaybackActive && currentPlayListEntry != null) {
            finalizeAllTraceEntries(StopReasonType.UserRequest)
        }
        
        // Finalize current playlist entry and start new one with SEEK type
        finalizeCurrentPlayListEntry()
        startNewPlayListEntry(StartType.NewPlayoutRequest, newPositionMs)
    }

    /**
     * Handle playback speed changes for PlayList metric
     * Per TS 26.247 clause 10.2.6: Records playbackSpeed in trace entries
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackSpeedChangedEvent(playbackSpeedChangedEvent: PlaybackSpeedChangedEvent) {
        val speed = playbackSpeedChangedEvent.playbackSpeed.toDouble()
        Log.d(TAG, "Playback speed changed: $currentPlaybackSpeed -> $speed")
        
        // If speed actually changed and we have an active trace, finalize it
        if (speed != currentPlaybackSpeed && isPlaybackActive && currentPlayListEntry != null) {
            finalizeAllTraceEntries(StopReasonType.UserRequest)
            finalizeCurrentPlayListEntry()
            currentPlaybackSpeed = speed
            startNewPlayListEntry(StartType.OtherUserRequest)
        } else {
            currentPlaybackSpeed = speed
        }
    }

    /**
     * Handle playback state changes for PlayList tracking
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackStateChangedEvent(playbackStateChangedEvent: PlaybackStateChangedEvent) {
        val newState = playbackStateChangedEvent.playbackState
        when (newState) {
            PlayerStates.PLAYING -> {
                if (!isPlaybackActive) {
                    // Playback starting or resuming
                    isPlaybackActive = true
                    if (currentPlayListEntry == null) {
                        // New playback session
                        startNewPlayListEntry(StartType.NewPlayoutRequest)
                    } else {
                        // Resuming from pause or rebuffering - start new trace entry for all tracks
                        startAllTraceEntries()
                    }
                }
            }
            PlayerStates.BUFFERING -> {
                if (isPlaybackActive) {
                    // Rebuffering/stalling occurred during playback
                    // Per TS 26.247 clause 10.2.7: finalize trace entry with REBUFFERING
                    finalizeAllTraceEntries(StopReasonType.Rebuffering)
                    // Note: isPlaybackActive remains true - we're still in a playback session
                    // A new trace entry will be started when PLAYING state resumes
                }
            }
            PlayerStates.PAUSED -> {
                if (isPlaybackActive) {
                    // User paused playback
                    finalizeAllTraceEntries(StopReasonType.UserRequest)
                    isPlaybackActive = false
                }
            }
            PlayerStates.ENDED -> {
                if (isPlaybackActive) {
                    // Playback ended
                    finalizeAllTraceEntries(StopReasonType.EndOfContent)
                    finalizeCurrentPlayListEntry()
                    isPlaybackActive = false
                }
            }
            PlayerStates.IDLE -> {
                if (isPlaybackActive) {
                    // Playback stopped
                    finalizeAllTraceEntries(StopReasonType.Other, "stopped")
                    finalizeCurrentPlayListEntry()
                    isPlaybackActive = false
                }
            }
        }
    }

    /**
     * Handle representation switch for PlayList tracking
     * Creates a new trace entry when the representation changes
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownstreamFormatChangedEvent(downstreamFormatChangedEvent: DownstreamFormatChangedEvent) {
        val trackType = downstreamFormatChangedEvent.mediaLoadData.trackType
        val newRepresentationId = downstreamFormatChangedEvent.mediaLoadData.trackFormat?.id
        
        if (newRepresentationId == null) return
        
        val trackState = activeTracks.getOrPut(trackType) { TrackTraceState() }
        
        if (trackState.representationId == newRepresentationId) {
            return
        }
        
        // If we have an active trace entry for this track, finalize it with REP_SWITCH
        if (isPlaybackActive && currentPlayListEntry != null && trackState.startTime != null) {
            finalizeTraceEntryForTrack(trackType, trackState, StopReasonType.RepresentationSwitch)
            trackState.representationId = newRepresentationId
            startTraceEntryForTrack(trackState)
        } else {
            // Just update the current representation ID
            trackState.representationId = newRepresentationId
            
            // If playback is active but track wasn't active, start tracking it now
            if (isPlaybackActive && currentPlayListEntry != null && trackState.startTime == null) {
                startTraceEntryForTrack(trackState)
            }
        }
    }

    /**
     * Create a snapshot of the current PlayList for reporting
     * Includes any in-progress entries without modifying the tracking state
     */
    fun createSnapshot(): PlayList? {
        val snapshotEntries = ArrayList<PlayListEntry>()
        
        // Add all completed entries
        snapshotEntries.addAll(playList.entries)
        
        // If there's a current entry in progress, create a snapshot of it
        if (currentPlayListEntry != null) {
            val currentTraceEntries = ArrayList<PlayListTraceEntry>()
            currentTraceEntries.addAll(currentPlayListEntry!!.traceEntries)

            // Add snapshots of all active track traces
            if (isPlaybackActive) {
                val currentTimestamp = utils.getCurrentTimestamp()
                
                for ((_, trackState) in activeTracks) {
                    if (trackState.startTime != null) {
                        val duration = currentTimestamp - trackState.startTimestamp
                        
                        if (duration > 0) {
                            val traceEntrySnapshot = PlayListTraceEntry(
                                start = trackState.startTime!!,
                                sstart = utils.millisecondsToISO8601(trackState.mediaStartMs) ?: "PT0S",
                                duration = duration,
                                representationId = trackState.representationId,
                                playbackSpeed = if (currentPlaybackSpeed != 1.0) currentPlaybackSpeed else null,
                                stopReason = StopReasonType.EndOfMetricsCollectionPeriod,  // Forced cut off by end of interval
                                stopReasonOther = null
                            )
                            currentTraceEntries.add(traceEntrySnapshot)
                        }
                    }
                }
            }
            
            if (currentTraceEntries.isNotEmpty()) {
                val entrySnapshot = PlayListEntry(
                    start = currentPlayListEntry!!.start,
                    mstart = currentPlayListEntry!!.mstart,
                    startType = currentPlayListEntry!!.startType,
                    traceEntries = currentTraceEntries
                )
                snapshotEntries.add(entrySnapshot)
            }
        }
        
        return if (snapshotEntries.isNotEmpty()) PlayList(snapshotEntries) else null
    }

    /**
     * Reset PlayList tracking state (clears data but keeps listening for events)
     */
    fun reset() {
        playList.entries.clear()
        currentPlayListEntry = null
        activeTracks.clear()
        currentPlaybackSpeed = 1.0
        isPlaybackActive = false
    }

    // ==================== Private Helper Methods ====================

    /**
     * Start a new PlayListEntry with the given start type
     */
    private fun startNewPlayListEntry(startType: StartType, mediaPositionMs: Long = exoPlayerAdapter.getCurrentPosition()) {
        val start = utils.getCurrentXsDateTime()
        val mstart = utils.millisecondsToISO8601(mediaPositionMs) ?: "PT0S"
        
        currentPlayListEntry = PlayListEntry(
            start = start,
            mstart = mstart,
            startType = startType,
            traceEntries = ArrayList()
        )
        
        Log.d(TAG, "Started new PlayListEntry: startType=$startType, mstart=$mstart")
        
        // Start first trace entries within this playlist entry for all active tracks
        startAllTraceEntries()
    }

    private fun startAllTraceEntries() {
        activeTracks.values.forEach { startTraceEntryForTrack(it) }
    }

    /**
     * Start a new trace entry within the current PlayListEntry for a specific track
     */
    private fun startTraceEntryForTrack(trackState: TrackTraceState) {
        if (trackState.representationId == null) return // Ignore tracks without a specific representation ID

        trackState.startTime = utils.getCurrentXsDateTime()
        trackState.startTimestamp = utils.getCurrentTimestamp()
        trackState.mediaStartMs = exoPlayerAdapter.getCurrentPosition()
        
        Log.d(TAG, "Started new trace entry: representationId=${trackState.representationId}, speed=$currentPlaybackSpeed")
    }

    private fun finalizeAllTraceEntries(stopReason: StopReasonType, stopReasonOther: String? = null) {
        for ((trackType, trackState) in activeTracks) {
            finalizeTraceEntryForTrack(trackType, trackState, stopReason, stopReasonOther)
        }
    }

    /**
     * Finalize the trace entry for a specific track with a stop reason
     */
    private fun finalizeTraceEntryForTrack(trackType: Int, trackState: TrackTraceState, stopReason: StopReasonType, stopReasonOther: String? = null) {
        if (trackState.startTime == null || currentPlayListEntry == null) {
            return
        }
        
        val currentTimestamp = utils.getCurrentTimestamp()
        val duration = currentTimestamp - trackState.startTimestamp
        
        // Don't add entries with zero duration
        if (duration <= 0) {
            trackState.startTime = null
            trackState.startTimestamp = 0L
            return
        }
        
        val traceEntry = PlayListTraceEntry(
            start = trackState.startTime!!,
            sstart = utils.millisecondsToISO8601(trackState.mediaStartMs) ?: "PT0S",
            duration = duration,
            representationId = trackState.representationId,
            playbackSpeed = if (currentPlaybackSpeed != 1.0) currentPlaybackSpeed else null,
            stopReason = stopReason,
            stopReasonOther = if (stopReason == StopReasonType.Other) stopReasonOther else null
        )
        
        currentPlayListEntry!!.traceEntries.add(traceEntry)
        Log.d(TAG, "Finalized trace entry for repId=${trackState.representationId}: duration=${duration}ms, stopReason=$stopReason")
        
        // Reset trace entry tracking
        trackState.startTime = null
        trackState.startTimestamp = 0L
    }

    /**
     * Finalize the current PlayListEntry and add it to the playlist
     */
    private fun finalizeCurrentPlayListEntry() {
        if (currentPlayListEntry != null && currentPlayListEntry!!.traceEntries.isNotEmpty()) {
            playList.entries.add(currentPlayListEntry!!)
            Log.d(TAG, "Finalized PlayListEntry with ${currentPlayListEntry!!.traceEntries.size} trace entries")
        }
        currentPlayListEntry = null
    }
}
