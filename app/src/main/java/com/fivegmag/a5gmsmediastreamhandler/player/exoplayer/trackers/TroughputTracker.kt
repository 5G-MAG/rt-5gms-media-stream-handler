package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import com.fivegmag.a5gmscommonlibrary.eventbus.BytesTransferredEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadCompletedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadStartedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.AvgThroughput

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.InactivityType
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Tracks average throughput metrics per TS 26.247 clause 10.2.4
 * 
 * Handles measurement interval tracking, activity time calculation,
 * and inactivity type determination for QoE metrics reporting.
 */
class ThroughputTracker(
    private val utils: Utils = Utils()
) {
    // Average throughput list for reporting
    private val avgThroughputList: ArrayList<AvgThroughput> = ArrayList()

    // Average throughput tracking per TS 26.247 clause 10.2.4
    private var measurementIntervalStartTime: String? = null
    private var measurementIntervalStartTimestamp: Long = 0L
    private var totalBytesInInterval: Long = 0L
    private var totalActivityTimeInInterval: Long = 0L
    private var activeRequestCount: Int = 0
    private var lastRequestStartTimestamp: Long = 0L
    private val inactivityTypes: ArrayList<InactivityType> = ArrayList()
    private var lastPlayerState: String = PlayerStates.IDLE

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
     * Handle playback state changes for inactivity type determination
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackStateChangedEvent(playbackStateChangedEvent: PlaybackStateChangedEvent) {
        lastPlayerState = playbackStateChangedEvent.playbackState
    }

    /**
     * Track when a load request starts for activity time calculation
     * Per TS 26.247 clause 10.2.4: activity time is when at least one GET request is still not completed
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLoadStartedEvent(loadStartedEvent: LoadStartedEvent) {
        onLoadStarted()
    }

    /**
     * Update activity time tracking when a load completes
     * Per TS 26.247 clause 10.2.4
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLoadCompletedEvent(loadCompletedEvent: LoadCompletedEvent) {
        onLoadCompleted()
    }

    /**
     * Incrementally count bytes as they arrive over the network
     * Per TS 26.247 clause 10.2.4: numBytes is the total bytes downloaded in the interval
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBytesTransferredEvent(bytesTransferredEvent: BytesTransferredEvent) {
        totalBytesInInterval += bytesTransferredEvent.bytesTransferred
    }

    /**
     * Track when a load request starts for activity time calculation
     * Per TS 26.247 clause 10.2.4: activity time is when at least one GET request is still not completed
     */
    private fun onLoadStarted() {
        // Initialize measurement interval on first request
        if (measurementIntervalStartTime == null) {
            measurementIntervalStartTime = utils.getCurrentXsDateTime()
            measurementIntervalStartTimestamp = utils.getCurrentTimestamp()
        }

        // Track active requests for activity time calculation
        if (activeRequestCount == 0) {
            lastRequestStartTimestamp = utils.getCurrentTimestamp()
        }
        activeRequestCount++
    }

    /**
     * Update activity time when a load completes
     * Per TS 26.247 clause 10.2.4: activity time is accumulated when the last active request completes
     * Byte counting is handled separately by onBytesTransferredEvent
     */
    private fun onLoadCompleted() {
        if (activeRequestCount <= 0) {
            activeRequestCount = 0
            return
        }

        // Update activity time when last active request completes
        activeRequestCount--
        if (activeRequestCount == 0) {
            val currentTimestamp = utils.getCurrentTimestamp()
            totalActivityTimeInInterval += (currentTimestamp - lastRequestStartTimestamp)

            // Record inactivity type when entering inactive state
            recordInactivityType()
        }
    }

    /**
     * Record the reason for entering an inactive state (no pending requests)
     * Per TS 26.247 clause 10.2.4: inactivityType indicates why there was no activity
     */
    private fun recordInactivityType() {
        val inactivityType = when (lastPlayerState) {
            PlayerStates.PAUSED -> InactivityType.USER_REQUEST
            PlayerStates.READY -> InactivityType.CLIENT_MEASURE  // Buffer is sufficient
            PlayerStates.BUFFERING -> InactivityType.ERROR       // Buffering but no requests = error
            PlayerStates.ENDED -> InactivityType.USER_REQUEST    // Playback ended
            else -> null
        }
        if (inactivityType != null) {
            inactivityTypes.add(inactivityType)
        }
    }

    /**
     * Get consistent inactivity type if all recorded inactivities have the same type
     * Per TS 26.247: only report if "known and consistent throughout the reporting period"
     */
    private fun getConsistentInactivityType(): InactivityType? {
        if (inactivityTypes.isEmpty()) {
            return null
        }
        val firstType = inactivityTypes.first()
        return if (inactivityTypes.all { it == firstType }) firstType else null
    }

    /**
     * Add a current throughput entry to the list (called when generating report)
     */
    fun addCurrentEntry() {
        val entry = createAvgThroughputEntry()
        if (entry != null) {
            avgThroughputList.add(entry)
        }
    }

    /**
     * Get the average throughput list for reporting
     */
    fun getAvgThroughputList(): ArrayList<AvgThroughput> {
        return avgThroughputList
    }

    /**
     * Create an AvgThroughput entry for the current measurement interval
     * Per TS 26.247 clause 10.2.4
     */
    private fun createAvgThroughputEntry(): AvgThroughput? {
        // Only create entry if we have data from the measurement interval
        if (measurementIntervalStartTime == null || totalBytesInInterval == 0L) {
            return null
        }

        val currentTimestamp = utils.getCurrentTimestamp()
        val duration = currentTimestamp - measurementIntervalStartTimestamp

        // If there are still active requests, add their current activity time
        var activityTime = totalActivityTimeInInterval
        if (activeRequestCount > 0) {
            activityTime += (currentTimestamp - lastRequestStartTimestamp)
        }

        val avgThroughput = AvgThroughput(
            t = measurementIntervalStartTime!!,
            numBytes = totalBytesInInterval,
            activityTime = activityTime,
            duration = duration,
            accessBearer = null,  // Access bearer info not available from ExoPlayer
            inactivityType = getConsistentInactivityType()
        )

        // Reset interval tracking for next interval after creating the entry.
        // We preserve active requests so they continue being tracked in the next interval.
        resetIntervalTracking(clearActiveRequests = false)
        
        // Start new measurement interval immediately
        measurementIntervalStartTime = utils.getCurrentXsDateTime()
        measurementIntervalStartTimestamp = currentTimestamp

        return avgThroughput
    }

    /**
     * Reset all state including the throughput list
     */
    fun reset() {
        avgThroughputList.clear()
        resetIntervalTracking()
    }

    /**
     * Reset interval tracking variables for next measurement interval
     * @param clearActiveRequests Whether to clear the active request tracking. True for full resets.
     */
    private fun resetIntervalTracking(clearActiveRequests: Boolean = true) {
        measurementIntervalStartTime = null
        measurementIntervalStartTimestamp = 0L
        totalBytesInInterval = 0L
        totalActivityTimeInInterval = 0L
        inactivityTypes.clear()
        
        if (clearActiveRequests) {
            activeRequestCount = 0
            lastRequestStartTimestamp = 0L
        } else if (activeRequestCount > 0) {
            // Keep tracking active requests, but reset their start time to now 
            // for the new interval so we don't double-count activity time
            lastRequestStartTimestamp = utils.getCurrentTimestamp()
        }
    }
}
