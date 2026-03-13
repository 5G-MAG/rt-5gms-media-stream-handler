package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers

import android.os.Handler
import android.os.Looper
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter

class BufferLevelTracker(
    private val exoPlayerAdapter: IExoPlayerAdapter,
    private val utils: Utils = Utils()
) {
    private val bufferLevel: BufferLevel = BufferLevel(ArrayList())
    private val handler = Handler(Looper.getMainLooper())
    private var samplingRunnable: Runnable? = null

    fun initialize() {
        // No EventBus registration needed as this tracker is strictly periodic 
        // per TS 26.247 / ISO/IEC 23009-1 (BufferLevel(n))
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
        // stopSampling()
    }

    fun unregister() {
        stopSampling()
    }

    private fun stopSampling() {
        samplingRunnable?.let {
            handler.removeCallbacks(it)
            samplingRunnable = null
        }
    }
}
