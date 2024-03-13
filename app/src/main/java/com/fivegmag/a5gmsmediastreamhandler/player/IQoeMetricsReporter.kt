package com.fivegmag.a5gmsmediastreamhandler.player

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest

interface IQoeMetricsReporter {

    fun initialize(lastQoeMetricsRequest: QoeMetricsRequest)

    fun getQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest, reportingClientId: String) : String

    fun reset()

    fun resetState()

    fun setLastQoeMetricsRequest(lastQoeMetricsRequest: QoeMetricsRequest)
}