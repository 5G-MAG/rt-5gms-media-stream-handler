package com.fivegmag.a5gmsmediastreamhandler.qoeMetrics.threeGPP

import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.AvgThroughput
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.QoeMetricsReport
import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter
import org.simpleframework.xml.core.Persister
import java.io.StringWriter

@UnstableApi
class QoEMetricsExoPlayer(private val exoPlayerAdapter: ExoPlayerAdapter) {

    private fun getAverageThroughput() : AvgThroughput? {
        return null
    }

    private fun getBufferLevel(): BufferLevel {
        val level: Int = exoPlayerAdapter.getBufferLength().toInt()
        val entries = ArrayList<BufferLevelEntry>()
        val time: Long = getCurrentTimestamp()
        val entry = BufferLevelEntry(time, level)
        entries.add(entry)

        return BufferLevel(entries)
    }


    private fun serializeToXml(qoeMetricsReport: QoeMetricsReport) : String {
        val serializer = Persister()
        val stringWriter = StringWriter()

        serializer.write(qoeMetricsReport, stringWriter)

        return stringWriter.toString()
    }

    fun getQoeMetricsReport() : String {
        val metricsList = ArrayList<Any>()
        val bufferLevel = getBufferLevel()
        metricsList.add(bufferLevel)
        val qoeMetricsReport = QoeMetricsReport(metricsList)

        return serializeToXml(qoeMetricsReport)
    }

    private fun getCurrentTimestamp() : Long {
        return System.currentTimeMillis();
    }


}