package com.fivegmag.a5gmsmediastreamhandler.consumptionReporting

import androidx.media3.common.util.UnstableApi
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReport
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter
import java.util.Date

@UnstableApi
class ConsumptionReportingExoPlayer(
    private val exoPlayerAdapter: ExoPlayerAdapter
) {
    private val TAG = "ConsumptionReportingExoPlayer"
    private val utils: Utils = Utils()
    private val reportingClientId = utils.generateUUID()

    fun getConsumptionReport(): String {
        val mediaPlayerEntry = exoPlayerAdapter.getCurrentManifestUri()
        val consumptionReportingUnits = exoPlayerAdapter.getConsumptionReportingUnitList()

        // We need to add the duration of the consumption reporting units that are not yet finished
        for (consumptionReportingUnit in consumptionReportingUnits) {
            if (!consumptionReportingUnit.finished) {
                val currentTime = utils.formatDateToOpenAPIFormat(Date())
                consumptionReportingUnit.duration =
                    utils.calculateTimestampDifferenceInSeconds(
                        consumptionReportingUnit.startTime,
                        currentTime
                    ).toInt()
            }
        }

        val consumptionReport = ConsumptionReport(
            mediaPlayerEntry,
            reportingClientId,
            consumptionReportingUnits
        )
        val objectMapper: ObjectMapper = jacksonObjectMapper()

        return objectMapper.writeValueAsString(consumptionReport)
    }
}