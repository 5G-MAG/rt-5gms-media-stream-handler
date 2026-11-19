package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import android.os.Build
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.VideoSizeChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.VrDeviceInformationEntry
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.roundToLong

/**
 * Tracks the VrDeviceInformation metric of TS 26.118 V19.0.0 clause 9.3.4: an entry at the start of the session and whenever the information
 * changes, with 0 or the empty string for a value that cannot be logged. The platform gives the device identifier and the refresh rate; the
 * resolution and the field of view per eye and the rendered field of view are those of a VR device and a VR renderer (observation point 5), which
 * the library has no source for, so they are 0.
 */
@UnstableApi
class VrDeviceInformationTracker(
    private val exoPlayerAdapter: IExoPlayerAdapter,
    private val utils: Utils = Utils(),
    // "The brand, model and version of the device": the version is the Android release, as in the User-Agent of the library
    private val deviceIdentifier: () -> String = { "${Build.BRAND} ${Build.MODEL} ${Build.VERSION.RELEASE}" }
) {
    /** The event bus of the Media Stream Handler instance that owns the player (set by the reporter before use) */
    var eventBus: EventBus = EventBus.getDefault()

    private val entries = ArrayList<VrDeviceInformationEntry>()
    private var lastLogged: VrDeviceInformationEntry? = null

    companion object {
        const val TAG = "5GMS-VrDeviceInformationTracker"
        const val UNKNOWN = 0L
    }

    /** The start of the session: the information is logged */
    fun initialize() {
        if (!eventBus.isRegistered(this)) eventBus.register(this)
        lastLogged = null
        logIfChanged()
    }

    fun unregister() {
        if (eventBus.isRegistered(this)) eventBus.unregister(this)
    }

    // the display may have changed with the size of the video (orientation, window)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onVideoSizeChangedEvent(videoSizeChangedEvent: VideoSizeChangedEvent) {
        logIfChanged()
    }

    /** An entry is logged when the information differs from the one logged last; the first one is always logged */
    @Synchronized
    fun logIfChanged() {
        val entry = VrDeviceInformationEntry(
            start = utils.getCurrentXsDateTime(),
            mstart = utils.millisecondsToISO8601(exoPlayerAdapter.getCurrentPosition()),
            deviceIdentifier = deviceIdentifier(),
            horizontalResolution = UNKNOWN,
            verticalResolution = UNKNOWN,
            horizontalFoV = UNKNOWN,
            verticalFoV = UNKNOWN,
            renderedHorizontalFoV = UNKNOWN,
            renderedVerticalFoV = UNKNOWN,
            refreshRate = exoPlayerAdapter.getDisplayRefreshRate().roundToLong().coerceAtLeast(0L)
        )
        val last = lastLogged
        if (last != null && last.copy(start = entry.start, mstart = entry.mstart) == entry) return
        lastLogged = entry
        entries.add(entry)
    }

    /** The entries logged since the last report, after a check whether the information has changed since the last entry */
    @Synchronized
    fun getEntries(): ArrayList<VrDeviceInformationEntry> {
        logIfChanged()
        return ArrayList(entries)
    }

    /** After a report the entries are reported; an unchanged information is not logged again */
    @Synchronized
    fun reset() {
        entries.clear()
    }
}
