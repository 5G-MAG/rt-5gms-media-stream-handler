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
 * Helper class for tracking PlayList QoE metric per TS 26.247 clause 10.2.6
 * 
 * Tracks playback sessions including:
 * - Start types (NEW, RESUME, SEEK)
 * - Trace entries with representation switches, speed changes
 * - Stop reasons (USER_REQUEST, END_OF_CONTENT, REP_SWITCH, etc.)
 */
@UnstableApi
class PlayListTracker(
    private val exoPlayerAdapter: IExoPlayerAdapter
) {
    private val utils: Utils = Utils()
    
    // PlayList tracking state
    private val playList: PlayList = PlayList(ArrayList())
    private var currentPlayListEntry: PlayListEntry? = null
    private var currentTraceEntryStartTime: String? = null
    private var currentTraceEntryStartTimestamp: Long = 0L
    private var currentTraceEntryMediaStartMs: Long = 0L
    private var currentRepresentationId: String? = null
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
        
        // Finalize current trace entry if active
        if (isPlaybackActive && currentPlayListEntry != null) {
            finalizeCurrentTraceEntry(StopReasonType.USER_REQUEST)
        }
        
        // Finalize current playlist entry and start new one with SEEK type
        finalizeCurrentPlayListEntry()
        startNewPlayListEntry(StartType.SEEK, newPositionMs)
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
            finalizeCurrentTraceEntry(StopReasonType.OTHER, "speed_change")
            currentPlaybackSpeed = speed
            startNewTraceEntry()
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
                        startNewPlayListEntry(StartType.NEW)
                    } else if (currentTraceEntryStartTime == null) {
                        // Resuming from pause
                        startNewTraceEntry()
                    }
                }
            }
            PlayerStates.PAUSED -> {
                if (isPlaybackActive) {
                    // User paused playback
                    finalizeCurrentTraceEntry(StopReasonType.USER_REQUEST)
                    isPlaybackActive = false
                }
            }
            PlayerStates.ENDED -> {
                if (isPlaybackActive) {
                    // Playback ended
                    finalizeCurrentTraceEntry(StopReasonType.END_OF_CONTENT)
                    finalizeCurrentPlayListEntry()
                    isPlaybackActive = false
                }
            }
            PlayerStates.IDLE -> {
                if (isPlaybackActive) {
                    // Playback stopped
                    finalizeCurrentTraceEntry(StopReasonType.OTHER, "stopped")
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
        val newRepresentationId = downstreamFormatChangedEvent.mediaLoadData.trackFormat?.id
        if (newRepresentationId == null || newRepresentationId == currentRepresentationId) {
            return
        }
        
        // If we have an active trace entry, finalize it with REP_SWITCH
        if (isPlaybackActive && currentPlayListEntry != null && currentTraceEntryStartTime != null) {
            finalizeCurrentTraceEntry(StopReasonType.REP_SWITCH)
            currentRepresentationId = newRepresentationId
            startNewTraceEntry()
        } else {
            // Just update the current representation ID
            currentRepresentationId = newRepresentationId
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
            
            // If there's an active trace entry, add a snapshot of it
            if (currentTraceEntryStartTime != null && isPlaybackActive) {
                val currentTimestamp = utils.getCurrentTimestamp()
                val duration = currentTimestamp - currentTraceEntryStartTimestamp
                
                if (duration > 0) {
                    val traceEntrySnapshot = PlayListTraceEntry(
                        start = currentTraceEntryStartTime!!,
                        sstart = utils.millisecondsToISO8601(currentTraceEntryMediaStartMs) ?: "PT0S",
                        duration = duration,
                        representationId = currentRepresentationId,
                        playbackSpeed = if (currentPlaybackSpeed != 1.0) currentPlaybackSpeed else null,
                        stopReason = null,  // Still in progress
                        stopReasonOther = null
                    )
                    currentTraceEntries.add(traceEntrySnapshot)
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
        currentTraceEntryStartTime = null
        currentTraceEntryStartTimestamp = 0L
        currentTraceEntryMediaStartMs = 0L
        currentRepresentationId = null
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
        
        // Start first trace entry within this playlist entry
        startNewTraceEntry()
    }

    /**
     * Start a new trace entry within the current PlayListEntry
     */
    private fun startNewTraceEntry() {
        currentTraceEntryStartTime = utils.getCurrentXsDateTime()
        currentTraceEntryStartTimestamp = utils.getCurrentTimestamp()
        currentTraceEntryMediaStartMs = exoPlayerAdapter.getCurrentPosition()
        
        Log.d(TAG, "Started new trace entry: representationId=$currentRepresentationId, speed=$currentPlaybackSpeed")
    }

    /**
     * Finalize the current trace entry with a stop reason
     */
    private fun finalizeCurrentTraceEntry(stopReason: StopReasonType, stopReasonOther: String? = null) {
        if (currentTraceEntryStartTime == null || currentPlayListEntry == null) {
            return
        }
        
        val currentTimestamp = utils.getCurrentTimestamp()
        val duration = currentTimestamp - currentTraceEntryStartTimestamp
        
        // Don't add entries with zero duration
        if (duration <= 0) {
            return
        }
        
        val traceEntry = PlayListTraceEntry(
            start = currentTraceEntryStartTime!!,
            sstart = utils.millisecondsToISO8601(currentTraceEntryMediaStartMs) ?: "PT0S",
            duration = duration,
            representationId = currentRepresentationId,
            playbackSpeed = if (currentPlaybackSpeed != 1.0) currentPlaybackSpeed else null,
            stopReason = stopReason,
            stopReasonOther = if (stopReason == StopReasonType.OTHER) stopReasonOther else null
        )
        
        currentPlayListEntry!!.traceEntries.add(traceEntry)
        Log.d(TAG, "Finalized trace entry: duration=${duration}ms, stopReason=$stopReason")
        
        // Reset trace entry tracking
        currentTraceEntryStartTime = null
        currentTraceEntryStartTimestamp = 0L
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
