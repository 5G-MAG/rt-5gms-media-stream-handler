package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import androidx.media3.common.util.Log
import com.fivegmag.a5gmscommonlibrary.eventbus.FirstFrameRenderedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.FirstMediaSegmentRequestedEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Tracks Initial Playout Delay per TS 26.247 clause 10.2.5
 * 
 * The initial playout delay is measured as the time in milliseconds from the fetch 
 * of the first media Segment (or sub-segment) and the time at which media is 
 * retrieved from the client buffer (first frame rendered).
 */
class InitialPlayoutDelayTracker {

    companion object {
        const val TAG = "5GMS-InitialPlayoutDelayTracker"
    }

    /** Elapsed real-time when the first media segment fetch started */
    private var firstMediaSegmentRequestTimeMs: Long? = null

    /** Calculated initial playout delay in milliseconds */
    private var initialPlayoutDelay: Long? = null

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
     * Handle the first media segment request event
     * This marks the start time for initial playout delay calculation
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFirstMediaSegmentRequested(event: FirstMediaSegmentRequestedEvent) {
        // Only record the first occurrence per session
        if (firstMediaSegmentRequestTimeMs == null) {
            firstMediaSegmentRequestTimeMs = event.realtimeMs
            Log.d(TAG, "First media segment requested at realtimeMs: ${event.realtimeMs}")
        }
    }

    /**
     * Handle the first frame rendered event
     * This marks the end time for initial playout delay calculation
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFirstFrameRendered(event: FirstFrameRenderedEvent) {
        // Only calculate if we have a start time and haven't calculated yet
        if (firstMediaSegmentRequestTimeMs != null && initialPlayoutDelay == null) {
            initialPlayoutDelay = event.realtimeMs - firstMediaSegmentRequestTimeMs!!
            Log.d(TAG, "Initial playout delay calculated: ${initialPlayoutDelay}ms")
        }
    }

    /**
     * Get the calculated initial playout delay
     * @return Initial playout delay in milliseconds, or null if not yet calculated
     */
    fun getInitialPlayoutDelay(): Long? {
        return initialPlayoutDelay
    }

    /**
     * Reset all state for a new playback session
     */
    fun reset() {
        firstMediaSegmentRequestTimeMs = null
        initialPlayoutDelay = null
    }
}
