package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.annotation.SuppressLint
import android.os.Build
import android.telephony.CellInfo
import androidx.annotation.RequiresApi
import androidx.media3.common.C
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
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadCompletedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadStartedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.helpers.ignoreParcelableStability
import com.fivegmag.a5gmscommonlibrary.models.EndpointAddress
import com.fivegmag.a5gmscommonlibrary.models.Snssai
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

        // TS 26.512 V19.3.0 clause 11.3.3.2: the content identifier (if known) and the Representation@id, in that order, delimited by a single "|"
        fun mediaConsumed(contentId: String?, representationId: String?): String? =
            if (representationId == null) null
            else if (contentId.isNullOrEmpty()) representationId
            else "$contentId|$representationId"
    }

    private val utils: Utils = Utils()
    private val consumptionReportingUnitList: ArrayList<ConsumptionReportingUnit> = ArrayList()
    private var serverEndpointAddressesPerMediaType = mutableMapOf<String, EndpointAddress>()

    // the serving cells that were known last; each unit keeps its own list of the locations of its period (TS 26.510 V19.2.0 table 9.6.3.2-1)
    private var knownLocations: List<TypedLocation> = emptyList()

    // the network slice and the Data Network of the media access (table 9.6.3.2-1: sliceInfo and dataNetworkName), if known
    private var sliceInfo: Snssai? = null
    private var dataNetworkName: String? = null

    override fun setAccessNetworkIdentity(sliceInfo: Snssai?, dataNetworkName: String?) {
        this.sliceInfo = sliceInfo
        this.dataNetworkName = dataNetworkName
    }

    // the URL of the manifest after HTTP redirects, from the load that fetched it (LoadEventInfo.uri)
    private var manifestUriAfterRedirects: String? = null
    override fun initialize() {
        exoPlayerAdapter.eventBus.register(this)
    }


    @RequiresApi(Build.VERSION_CODES.R)
    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onDownstreamFormatChangedEvent(event: DownstreamFormatChangedEvent) {
        addConsumptionReportingUnit(event.mediaLoadData)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onLoadCompletedEvent(event: LoadCompletedEvent) {
        if (event.mediaLoadData.dataType == C.DATA_TYPE_MANIFEST) {
            manifestUriAfterRedirects = event.loadEventInfo.uri.toString()
        }
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
        val locations = ArrayList<TypedLocation>()
        for (cellInfo in event.cellInfoList) {
            if (cellInfo.isRegistered && (cellInfo.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING || cellInfo.cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING)) {
                val location = createTypedLocationByCellInfo(cellInfo)
                if (location != null) {
                    locations.add(location)
                }
            }
        }
        onLocationsUpdated(locations)
    }

    /**
     * The serving cells as the platform gives them now. A location that is new is added to the units that are being consumed, after the older
     * ones (clause 9.6.3.2: a time-ordered list), and a location change is reported (clause 5.3.6.1) when a location was known before.
     * A change of signal strength alone is no change of location.
     */
    internal fun onLocationsUpdated(locations: List<TypedLocation>) {
        val added = locations.filter { it !in knownLocations }
        if (locations.isEmpty() || added.isEmpty()) return
        val changed = knownLocations.isNotEmpty()
        knownLocations = locations
        for (unit in consumptionReportingUnitList) {
            if (!unit.finished) unit.locations?.addAll(added)
        }
        if (changed) locationChangeListener?.invoke()
    }

    @SuppressLint("Range")
    @RequiresApi(Build.VERSION_CODES.R)
    @UnstableApi
    private fun addConsumptionReportingUnit(mediaLoadData: MediaLoadData) {
        val startTime = utils.formatDateToOpenAPIFormat(Date())
        val mediaConsumed = mediaConsumed(exoPlayerAdapter.getContentIdentifier(), mediaLoadData.trackFormat?.id)
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
            consumptionReportingUnit.sliceInfo = sliceInfo
            consumptionReportingUnit.dataNetworkName = dataNetworkName

            // We add locations and clientEndpointAddress and filter the attributes later in case the corresponding configuration options are set to false
            // If we do not add the information here we can not include it later in case the SAI configuration changes
            consumptionReportingUnit.locations = ArrayList(knownLocations)
            consumptionReportingUnit.serverEndpointAddress = getServerEndpointAddress(mimeType)
            consumptionReportingUnit.clientEndpointAddress =
                getClientEndpointAddress(consumptionReportingUnit.serverEndpointAddress)
            consumptionReportingUnitList.add(consumptionReportingUnit)
        }
    }

    @UnstableApi
    override fun onAccessNetworkChanged() {
        val now = utils.formatDateToOpenAPIFormat(Date())
        val started = ArrayList<ConsumptionReportingUnit>()
        for (unit in consumptionReportingUnitList) {
            if (unit.finished) continue
            // the unit on the old access network ends and a new one starts on the new one
            unit.duration = utils.calculateTimestampDifferenceInSeconds(unit.startTime, now).toInt()
            unit.finished = true
            val next = ConsumptionReportingUnit(unit.mediaConsumed, null, null, now, 0, null)
            next.mimeType = unit.mimeType
            next.sliceInfo = sliceInfo
            next.dataNetworkName = dataNetworkName
            next.locations = ArrayList(knownLocations)
            next.serverEndpointAddress = getServerEndpointAddress(unit.mimeType)
            next.clientEndpointAddress = getClientEndpointAddress(next.serverEndpointAddress)
            started.add(next)
        }
        consumptionReportingUnitList.addAll(started)
    }

    @UnstableApi
    override fun getConsumptionReport(
        reportingClientId: String,
        consumptionRequest: ConsumptionRequest
    ): String {
        // the Media Player Entry is the URL retrieved at M4d after following any HTTP redirects (TS 26.512 V19.3.0 clause 11.3.3.1)
        val mediaPlayerEntry = manifestUriAfterRedirects ?: exoPlayerAdapter.getCurrentManifestUri()
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
        val objectMapper: ObjectMapper = jacksonObjectMapper().ignoreParcelableStability()
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

    // The port numbers of the client endpoint are those of the server endpoint. Without a known server endpoint there is no port to give (the schema
    // wants at least one, and the property is optional), so the unit has no client endpoint rather than an invented port.
    private fun getClientEndpointAddress(serverEndpointAddress: EndpointAddress?): EndpointAddress? {
        if (serverEndpointAddress == null) return null
        // the table of TS 26.510 7.3.3.11 has no IP address property (SD-019): the textual address goes in domainName
        val address = utils.getIpAddress(4) ?: utils.getIpAddress(6) ?: "unknown"

        return EndpointAddress(address, serverEndpointAddress.portNumbers)
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

    // After a report only the finished units go. The server endpoints stay: they are refreshed at each load, and a unit that starts before the next
    // load of its media type (after a switch of the Representation) would otherwise have none.
    override fun resetState() {
        cleanConsumptionReportingList()
    }

    override fun reset() {
        resetState()
        serverEndpointAddressesPerMediaType.clear()
        consumptionReportingUnitList.clear()
        manifestUriAfterRedirects = null
        locationChangeListener = null
    }

}