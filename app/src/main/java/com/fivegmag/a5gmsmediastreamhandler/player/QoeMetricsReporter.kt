package com.fivegmag.a5gmsmediastreamhandler.player

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest

interface QoeMetricsReporter {

    fun initialize()

    fun getQoeMetricsReportingScheme() : String

    fun getQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest) : String

    fun reset()
}