package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.annotation.SuppressLint
import android.os.Build
import android.telephony.CellInfo
import androidx.annotation.RequiresApi
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaLoadData
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReport
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReportingUnit
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadStartedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.models.EndpointAddress
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation
import com.fivegmag.a5gmsmediastreamhandler.controller.ConsumptionReportingFilterProvider
import com.fivegmag.a5gmsmediastreamhandler.player.ConsumptionReporter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.Exception
import java.util.Date

class ConsumptionReporterExoplayer(
    private val exoPlayerAdapter: IExoPlayerAdapter
) : ConsumptionReporter() {

    companion object {
        const val TAG = "5GMS-ConsumptionReporterExoplayer"
    }

    private val utils: Utils = Utils()
    private val consumptionReportingUnitList: ArrayList<ConsumptionReportingUnit> = ArrayList()
    private var serverEndpointAddressesPerMediaType = mutableMapOf<String, EndpointAddress>()
    private var activeLocations: ArrayList<TypedLocation> = ArrayList()
    override fun initialize() {
        EventBus.getDefault().register(this)
    }


    @RequiresApi(Build.VERSION_CODES.R)
    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onDownstreamFormatChangedEvent(event: DownstreamFormatChangedEvent) {
        addConsumptionReportingUnit(event.mediaLoadData)
    }

    @SuppressLint("Range")
    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onLoadStartedEvent(event: LoadStartedEvent) {
        try {
            val mimeType = event.mediaLoadData.trackFormat!!.containerMimeType
            if (mimeType != null) {
                val requestUrl = event.loadEventInfo.dataSpec.uri.toString()
                val endpointAddress = utils.getEndpointAddressByRequestUrl(requestUrl)
                if (endpointAddress != null) {
                    serverEndpointAddressesPerMediaType[mimeType] = endpointAddress
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error while creating server endpoint address: $e")
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onCellInfoUpdated(event: CellInfoUpdatedEvent) {
        activeLocations.clear()
        for (cellInfo in event.cellInfoList) {
            if (cellInfo.isRegistered && (cellInfo.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING || cellInfo.cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING)) {
                val location = createTypedLocationByCellInfo(cellInfo)
                if (location != null) {
                    activeLocations.add(location)
                }
            }
        }
    }

    @SuppressLint("Range")
    @RequiresApi(Build.VERSION_CODES.R)
    @UnstableApi
    private fun addConsumptionReportingUnit(mediaLoadData: MediaLoadData) {
        val startTime = utils.formatDateToOpenAPIFormat(Date())
        val mediaConsumed = mediaLoadData.trackFormat?.id
        val mimeType = mediaLoadData.trackFormat!!.containerMimeType
        val duration = 0

        // If we have a previous entry in the list of consumption reporting units with the same media type then we can calculate the duration now
        val existingEntry = consumptionReportingUnitList.find { it.mimeType == mimeType }
        if (existingEntry != null) {
            existingEntry.duration =
                utils.calculateTimestampDifferenceInSeconds(existingEntry.startTime, startTime)
                    .toInt()
            existingEntry.finished = true
        }

        // Add the new entry
        val consumptionReportingUnit =
            mediaConsumed?.let {
                ConsumptionReportingUnit(
                    it,
                    null,
                    null,
                    startTime,
                    duration,
                    null
                )
            }
        if (consumptionReportingUnit != null) {
            consumptionReportingUnit.mimeType = mimeType

            // We add locations and clientEndpointAddress and filter the attributes later in case the corresponding configuration options are set to false
            // If we do not add the information here we can not include it later in case the SAI configuration changes
            consumptionReportingUnit.locations = activeLocations
            consumptionReportingUnit.serverEndpointAddress = getServerEndpointAddress(mimeType)
            consumptionReportingUnit.clientEndpointAddress =
                getClientEndpointAddress(consumptionReportingUnit.serverEndpointAddress)
            consumptionReportingUnitList.add(consumptionReportingUnit)
        }
    }

    @UnstableApi
    override fun getConsumptionReport(
        reportingClientId: String,
        consumptionRequest: ConsumptionRequest
    ): String {
        val mediaPlayerEntry = exoPlayerAdapter.getCurrentManifestUri()
        // We need to add the duration of the consumption reporting units that are not yet finished
        for (consumptionReportingUnit in consumptionReportingUnitList) {
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
            consumptionReportingUnitList
        )
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

        // Filter the values according to the current configuration
        val propertiesToIgnore = mutableListOf<String>()
        if (consumptionRequest.locationReporting == false) {
            propertiesToIgnore.add("locations")
        }
        if (consumptionRequest.accessReporting == false) {
            propertiesToIgnore.add("clientEndpointAddress")
            propertiesToIgnore.add("serverEndpointAddress")
        }

        val filters = ConsumptionReportingFilterProvider.createFilter(propertiesToIgnore)

        return objectMapper.writer(filters).writeValueAsString(consumptionReport)
    }

    private fun getClientEndpointAddress(serverEndpointAddress: EndpointAddress?): EndpointAddress {
        val ipv4Address = utils.getIpAddress(4)
        val ipv6Address = utils.getIpAddress(6)
        var portNumber = 80
        if (serverEndpointAddress != null) {
            portNumber = serverEndpointAddress.portNumber
        }

        return EndpointAddress(null, ipv4Address, ipv6Address, portNumber)
    }

    private fun cleanConsumptionReportingList() {
        consumptionReportingUnitList.removeIf { obj -> obj.finished }
    }

    private fun getServerEndpointAddress(mimeType: String?): EndpointAddress? {
        if (mimeType == null) {
            return null
        }

        return serverEndpointAddressesPerMediaType[mimeType] ?: return null
    }

    override fun resetState() {
        cleanConsumptionReportingList()
        serverEndpointAddressesPerMediaType.clear()
    }

    override fun reset() {
        resetState()
        consumptionReportingUnitList.clear()
    }

}