package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import androidx.media3.common.util.Log
import com.fivegmag.a5gmscommonlibrary.eventbus.FirstFrameRenderedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStartTriggerEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Tracks Playout Delay for Media Start-up per TS 26.247 clause 10.2.9
 * 
 * The playout delay for media start-up is measured as the time in milliseconds from 
 * the time instant of DASH player receives playback-start trigger to the instant of 
 * media playout (first frame rendered).
 * 
 * This metric is only logged at the time point when the media start-up happens.
 */
class PlayoutDelayForMediaStartupTracker {

    companion object {
        const val TAG = "5GMS-PlayoutDelayForMediaStartupTracker"
    }

    /** Elapsed real-time when the playback start was triggered */
    private var playbackStartTriggerTimeMs: Long? = null

    /** Calculated playout delay for media start-up in milliseconds */
    private var playoutDelayForMediaStartup: Long? = null

    /**
     * Initialize the tracker by registering with EventBus
     */
    fun initialize() {
        EventBus.getDefault().register(this)
    }

    /**
     * Unregister from EventBus when no longer needed
     */
    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    /**
     * Handle the playback start trigger event
     * This marks the start time for playout delay for media start-up calculation
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackStartTrigger(event: PlaybackStartTriggerEvent) {
        // Only record the first occurrence per session
        if (playbackStartTriggerTimeMs == null) {
            playbackStartTriggerTimeMs = event.realtimeMs
            Log.d(TAG, "Playback start triggered at realtimeMs: ${event.realtimeMs}")
        }
    }

    /**
     * Handle the first frame rendered event
     * This marks the end time for playout delay for media start-up calculation
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFirstFrameRendered(event: FirstFrameRenderedEvent) {
        // Only calculate if we have a start time and haven't calculated yet
        if (playbackStartTriggerTimeMs != null && playoutDelayForMediaStartup == null) {
            playoutDelayForMediaStartup = event.realtimeMs - playbackStartTriggerTimeMs!!
            Log.d(TAG, "Playout delay for media start-up calculated: ${playoutDelayForMediaStartup}ms")
        }
    }

    /**
     * Get the calculated playout delay for media start-up
     * @return Playout delay for media start-up in milliseconds, or null if not yet calculated
     */
    fun getPlayoutDelayForMediaStartup(): Long? {
        return playoutDelayForMediaStartup
    }

    /**
     * Reset all state for a new playback session
     */
    fun reset() {
        playbackStartTriggerTimeMs = null
        playoutDelayForMediaStartup = null
    }
}
