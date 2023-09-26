package com.fivegmag.a5gmsmediastreamhandler.consumptionReporting

import androidx.media3.common.util.UnstableApi
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReport
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReportRequest
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter

@UnstableApi
class ConsumptionReportingExoPlayer(
    private val exoPlayerAdapter: ExoPlayerAdapter
) {
    private val TAG = "ConsumptionReportingExoPlayer"
    private val utils: Utils = Utils()
    private val reportingClientId = utils.generateUUID()

    fun getConsumptionReport(consumptionReportRequest: ConsumptionReportRequest): String {
        val mediaPlayerEntry = exoPlayerAdapter.getCurrentManifestUri()
        val consumptionReportingUnits = exoPlayerAdapter.getConsumptionReportingUnitList()
        val consumptionReport = ConsumptionReport(
            mediaPlayerEntry,
            reportingClientId,
            consumptionReportingUnits
        )
        val objectMapper: ObjectMapper = jacksonObjectMapper()

        return objectMapper.writeValueAsString(consumptionReport)
    }
}