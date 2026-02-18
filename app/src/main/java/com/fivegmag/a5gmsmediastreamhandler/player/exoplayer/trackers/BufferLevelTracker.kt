package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import android.os.Handler
import android.os.Looper
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadCompletedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class BufferLevelTracker(
    private val exoPlayerAdapter: IExoPlayerAdapter,
    private val utils: Utils = Utils()
) {
    private val bufferLevel: BufferLevel = BufferLevel(ArrayList())
    private val handler = Handler(Looper.getMainLooper())
    private var samplingRunnable: Runnable? = null

    fun initialize() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    fun configure(qoeMetricsRequest: QoeMetricsRequest?) {
        stopSampling()

        // Setup sampling timer if samplingPeriod provided
        val samplingPeriod = qoeMetricsRequest?.samplingPeriod?.times(1000)?.toLong()
        if (samplingPeriod != null && samplingPeriod > 0) {
            samplingRunnable = object : Runnable {
                override fun run() {
                    addBufferLevelEntry()
                    handler.postDelayed(this, samplingPeriod)
                }
            }
            handler.post(samplingRunnable!!)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackStateChangedEvent(playbackStateChangedEvent: PlaybackStateChangedEvent) {
        if (playbackStateChangedEvent.playbackState == PlayerStates.BUFFERING) {
            addBufferLevelEntry()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLoadCompletedEvent(loadCompletedEvent: LoadCompletedEvent) {
        addBufferLevelEntry()
    }

    private fun addBufferLevelEntry() {
        val level: Int = exoPlayerAdapter.getBufferLength().toInt()
        val time: String = utils.getCurrentXsDateTime()
        val entry = BufferLevelEntry(time, level)
        bufferLevel.entries.add(entry)
    }

    fun addCurrentEntry() {
        addBufferLevelEntry()
    }

    fun getBufferLevel(): BufferLevel {
        return bufferLevel
    }

    fun reset() {
        bufferLevel.entries.clear()
        stopSampling()
    }

    fun unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        stopSampling()
    }

    private fun stopSampling() {
        samplingRunnable?.let {
            handler.removeCallbacks(it)
            samplingRunnable = null
        }
    }
}
