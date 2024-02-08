package com.fivegmag.a5gmsmediastreamhandler.qoeMetricsReporting

import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter
import java.lang.Exception

@UnstableApi
class QoEMetricsReportingController {

    private val activeQoeMetricsReporter = mutableMapOf<String, QoeMetricsReporter>()

    fun setDefaultExoplayerQoeMetricsReporter(exoPlayerAdapter: ExoPlayerAdapter) {
        val qoeMetricsReporterExoplayer = QoeMetricsReporterExoplayer(exoPlayerAdapter)
        qoeMetricsReporterExoplayer.initialize()
        setQoeMetricsReporterForScheme(qoeMetricsReporterExoplayer)
    }

    fun setQoeMetricsReporterForScheme(qoeMetricsReporter: QoeMetricsReporter) {
        val scheme = qoeMetricsReporter.getQoeMetricsReportingScheme()
        activeQoeMetricsReporter[scheme] = qoeMetricsReporter
    }

    fun getQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest?): String {
        if (qoeMetricsRequest == null) {
            throw Exception("qoeMetricsRequest can not be null")
        }

        val qoeMetricsReporterForScheme = activeQoeMetricsReporter[qoeMetricsRequest.scheme]
            ?: throw Exception("No QoeMetricsReporter for scheme $qoeMetricsRequest.scheme")

        val qoeMetricsReport = qoeMetricsReporterForScheme.getQoeMetricsReport(qoeMetricsRequest)
        qoeMetricsReporterForScheme.reset()

        return qoeMetricsReport
    }

    fun isMetricsSchemeSupported(scheme: String): Boolean {
        val qoeMetricsReporterForScheme = activeQoeMetricsReporter[scheme]

        return qoeMetricsReporterForScheme != null
    }

}