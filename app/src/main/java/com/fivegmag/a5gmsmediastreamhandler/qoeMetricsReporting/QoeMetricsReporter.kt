package com.fivegmag.a5gmsmediastreamhandler.qoeMetricsReporting

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest

interface QoeMetricsReporter {

    fun initialize()

    fun getQoeMetricsReportingScheme() : String

    fun getQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest) : String

    fun reset()
}