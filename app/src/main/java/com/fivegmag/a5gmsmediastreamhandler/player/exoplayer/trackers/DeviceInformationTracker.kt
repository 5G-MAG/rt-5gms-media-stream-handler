package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.VideoSizeChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.DeviceInformation
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.DeviceInformationEntry
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Tracks device information metrics per TS 26.247 clause 10.2.10
 * 
 * Handles logging device/display characteristics whenever they change,
 * such as orientation changes or fullscreen toggles.
 */
@UnstableApi
class DeviceInformationTracker(
    private val exoPlayerAdapter: IExoPlayerAdapter,
    private val utils: Utils = Utils()
) {
    private val deviceInformation: DeviceInformation = DeviceInformation(ArrayList())

    companion object {
        const val TAG = "5GMS-DeviceInformationTracker"
        const val UNKNOWN_FIELD_OF_VIEW = 0.0  // Set to 0 when unknown per TS 26.247
    }

    /**
     * Initialize the tracker by registering with EventBus
     */
    fun initialize() {
        EventBus.getDefault().register(this)
        addCurrentEntry()
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
     * Log device information when video size changes (orientation, fullscreen toggle, etc.)
     * Per TS 26.247 clause 10.2.10: "Whenever any device/display characteristic changes"
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onVideoSizeChangedEvent(videoSizeChangedEvent: VideoSizeChangedEvent) {
        Log.d(TAG, "Video size changed - logging device information entry")
        val deviceInfoEntry = createDeviceInformationEntry()
        deviceInformation.entries.add(deviceInfoEntry)
    }

    /**
     * Create a device information entry with current display characteristics
     */
    private fun createDeviceInformationEntry(): DeviceInformationEntry {
        val start = utils.getCurrentXsDateTime()
        val mstart = utils.millisecondsToISO8601(exoPlayerAdapter.getCurrentPosition()) ?: "PT0S"
        val videoWidth = exoPlayerAdapter.getVideoWidth()
        val videoHeight = exoPlayerAdapter.getVideoHeight()
        val screenWidth = exoPlayerAdapter.getScreenWidth()
        val screenHeight = exoPlayerAdapter.getScreenHeight()
        val pixelDensityX = exoPlayerAdapter.getPixelDensityX()
        val pixelDensityY = exoPlayerAdapter.getPixelDensityY()

        // Calculate physical pixel size in mm (25.4mm per inch / dpi)
        val pixelWidth = if (pixelDensityX > 0) 25.4 / pixelDensityX else 0.0
        val pixelHeight = if (pixelDensityY > 0) 25.4 / pixelDensityY else 0.0

        return DeviceInformationEntry(
            start = start,
            mstart = mstart,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pixelWidth = pixelWidth,
            pixelHeight = pixelHeight,
            fieldOfView = UNKNOWN_FIELD_OF_VIEW
        )
    }

    /**
     * Add a current device information entry (called when generating report)
     */
    fun addCurrentEntry() {
        val deviceInfoEntry = createDeviceInformationEntry()
        deviceInformation.entries.add(deviceInfoEntry)
    }

    /**
     * Get the device information for reporting
     */
    fun getDeviceInformation(): DeviceInformation {
        return deviceInformation
    }

    /**
     * Reset the tracker state
     */
    fun reset() {
        deviceInformation.entries.clear()
        addCurrentEntry()
    }
}
