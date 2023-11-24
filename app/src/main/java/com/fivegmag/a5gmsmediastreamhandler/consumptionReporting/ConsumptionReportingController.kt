package com.fivegmag.a5gmsmediastreamhandler.consumptionReporting

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaLoadData
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.PropertyFilter
import com.fasterxml.jackson.databind.ser.PropertyWriter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReport
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReportingUnit
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.models.CellIdentifierType
import com.fivegmag.a5gmscommonlibrary.models.EndpointAddress
import com.fivegmag.a5gmscommonlibrary.models.PlaybackConsumptionReportingConfiguration
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.Exception
import java.util.Date

@UnstableApi
class ConsumptionReportingController(
    private val context: Context
) {
    private val TAG = "ConsumptionReportingController"
    private val utils: Utils = Utils()

    private val reportingClientId = genReportingClientId()

    private val consumptionReportingUnitList: ArrayList<ConsumptionReportingUnit> = ArrayList()
    private var playbackConsumptionReportingConfiguration: PlaybackConsumptionReportingConfiguration? =
        null
    private var activeLocations: ArrayList<TypedLocation> = ArrayList()
    private val cellInfoCallback = object : TelephonyManager.CellInfoCallback() {
        @SuppressLint("Range")
        override fun onCellInfo(cellInfoList: MutableList<CellInfo>) {
            // Process the received cell info
            activeLocations.clear()
            for (cellInfo in cellInfoList) {
                if (cellInfo.isRegistered && (cellInfo.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING || cellInfo.cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING)) {
                    val location = createTypedLocationByCellInfo(cellInfo)
                    if (location != null) {
                        activeLocations.add(location)
                    }
                }
            }

            EventBus.getDefault().post(CellInfoUpdatedEvent(cellInfoList))
        }
    }

    /**
     * MSISDN = CC + NDC + SN
     *
     */
    @SuppressLint("Range")
    private fun getMSISDN(): String {
        var strMSISDN: String = ""

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val subscriptionManager: SubscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            val subscriptionInfoList: List<SubscriptionInfo> = subscriptionManager.getActiveSubscriptionInfoList()
            if (subscriptionInfoList != null) {
                for (subscriptionInfo in subscriptionInfoList) {
                    strMSISDN = subscriptionManager.getPhoneNumber(getActiveSIMIdx(subscriptionInfoList))
                }
            }
        }

        return strMSISDN
    }

    /**
     * The GPSI is either a mobile subscriber ISDN number (MSISDN) or an external identifier
     *
     */
    @SuppressLint("Range")
    private fun genReportingClientId(): String
    {
        var strGPSI: String = ""
        val strMSISDN: String = getMSISDN()
        if (strMSISDN != "")
        {
            strGPSI = strMSISDN
        }
        else
        {
            strGPSI = utils.generateUUID()
        }

        Log.d(TAG, "ConsumptionReporting: GPSI = $strGPSI")
        return strGPSI
    }

    /**
     * In case of multi SIM cards, get the the index of SIM which is used for the traffic
     * If none of them match, use the first as default
     *
     */
    private fun getActiveSIMIdx(subscriptionInfoList: List<SubscriptionInfo>): Int {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var simOPName: String = telephonyManager.getSimOperatorName()

        var subscriptionIdx: Int = 1
        for (subscriptionInfo in subscriptionInfoList) {
            var subscriptionId = subscriptionInfo.getSubscriptionId()
            var subscriptionName: String = subscriptionInfo.getCarrierName() as String

            if (subscriptionName == simOPName)
            {
                subscriptionIdx = subscriptionId
            }
        }

        return subscriptionIdx
    }

    fun getPlaybackConsumptionReportingConfiguration(): PlaybackConsumptionReportingConfiguration? {
        return playbackConsumptionReportingConfiguration
    }

    private fun createTypedLocationByCellInfo(cellInfo: CellInfo): TypedLocation? {
        try {
            val typedLocation: TypedLocation?
            when (cellInfo) {
                // CGI = MCC + MNC + LAC + CI
                is CellInfoGsm -> {
                    val cellIdentity = cellInfo.cellIdentity as CellIdentityGsm
                    val mcc = cellIdentity.mccString
                    val mnc = cellIdentity.mncString
                    val lac = cellIdentity.lac
                    val ci = cellIdentity.cid
                    typedLocation = TypedLocation(CellIdentifierType.CGI, "$mcc$mnc$lac$ci")
                }
                // ECGI = MCC + MNC + ECI
                is CellInfoLte -> {
                    val cellIdentity = cellInfo.cellIdentity as CellIdentityLte
                    val mcc = cellIdentity.mccString
                    val mnc = cellIdentity.mncString
                    val eci = cellIdentity.ci
                    typedLocation = TypedLocation(CellIdentifierType.ECGI, "$mcc$mnc$eci")
                }
                // NCGI = MCC + MNC + NCI
                is CellInfoNr -> {
                    val cellIdentity = cellInfo.cellIdentity as CellIdentityNr
                    val mcc = cellIdentity.mccString
                    val mnc = cellIdentity.mncString
                    val nci = cellIdentity.nci
                    typedLocation = TypedLocation(CellIdentifierType.NCGI, "$mcc$mnc$nci")
                }

                else -> {
                    return null
                }
            }

            return typedLocation
        } catch (e: Exception) {
            return null
        }
    }

    fun initialize() {
        EventBus.getDefault().register(this)
        requestCellInfoUpdates()
    }

    fun resetState() {
        playbackConsumptionReportingConfiguration = null
        consumptionReportingUnitList.clear()
    }

    fun setCurrentConsumptionReportingConfiguration(consumptionReportingConfiguration: PlaybackConsumptionReportingConfiguration) {
        playbackConsumptionReportingConfiguration = consumptionReportingConfiguration
    }

    /**
     * Callback function that is triggered via the event bus
     *
     * @param {DownstreamFormatChangedEvent} event
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownstreamFormatChangedEvent(event: DownstreamFormatChangedEvent) {
        addConsumptionReportingUnit(event.mediaLoadData)
    }

    @SuppressLint("Range")
    @RequiresApi(Build.VERSION_CODES.R)
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

        Log.d(TAG, playbackConsumptionReportingConfiguration.toString())

        // Add the new entry
        val consumptionReportingUnit =
            mediaConsumed?.let { ConsumptionReportingUnit(it, null, startTime, duration, null) }
        if (consumptionReportingUnit != null) {
            consumptionReportingUnit.mimeType = mimeType

            // We add locations and mediaEndpointAddress and filter the attributes later in case the corresponding configuration options are set to false
            // If we do not add the information here we can not include it later in case the SAI configuration changes
            consumptionReportingUnit.locations = activeLocations
            consumptionReportingUnit.mediaEndpointAddress = getMediaEndpointAddress()
            consumptionReportingUnitList.add(consumptionReportingUnit)
        }
    }

    private fun getMediaEndpointAddress(): EndpointAddress {
        val ipv4Address = utils.getIpAddress(4)
        val ipv6Address = utils.getIpAddress(6)
        return EndpointAddress(null, ipv4Address, ipv6Address, 80u)
    }

    fun getConsumptionReport(mediaPlayerEntry: String): String {
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
        if (playbackConsumptionReportingConfiguration?.locationReporting == false) {
            propertiesToIgnore.add("locations")
        }
        if (playbackConsumptionReportingConfiguration?.accessReporting == false) {
            propertiesToIgnore.add("mediaEndpointAddress")
        }

        val filters = ConsumptionReportingFilterProvider.createFilter(propertiesToIgnore)

        return objectMapper.writer(filters).writeValueAsString(consumptionReport)
    }

    fun requestCellInfoUpdates() {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Register the cell info callback
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            telephonyManager.requestCellInfoUpdate(context.mainExecutor, cellInfoCallback)
        }
    }

    /**
     * Removes all entries in the consumption report that are finished
     *
     */
    fun cleanConsumptionReportingList() {
        consumptionReportingUnitList.removeIf { obj -> obj.finished }
    }
}

class ConsumptionReportingUnitFilter(private val propertiesToIgnore: List<String>) :
    PropertyFilter {
    override fun serializeAsField(
        pojo: Any?,
        gen: JsonGenerator?,
        prov: SerializerProvider?,
        writer: PropertyWriter?
    ) {
        if (include(writer)) {
            writer?.serializeAsField(pojo, gen, prov)
        } else if (!gen?.canOmitFields()!!) {
            writer?.serializeAsOmittedField(pojo, gen, prov)
        }
    }

    override fun serializeAsElement(
        elementValue: Any?,
        gen: JsonGenerator?,
        prov: SerializerProvider?,
        writer: PropertyWriter?
    ) {
        if (include(writer)) {
            writer?.serializeAsElement(elementValue, gen, prov)
        } else if (!gen?.canOmitFields()!!) {
            writer?.serializeAsOmittedField(elementValue, gen, prov)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun depositSchemaProperty(
        writer: PropertyWriter?,
        propertiesNode: ObjectNode?,
        provider: SerializerProvider?
    ) {
    }

    override fun depositSchemaProperty(
        writer: PropertyWriter?,
        objectVisitor: JsonObjectFormatVisitor?,
        provider: SerializerProvider?
    ) {
    }

    private fun include(writer: PropertyWriter?): Boolean {
        return writer?.name !in propertiesToIgnore
    }
}

object ConsumptionReportingFilterProvider {
    fun createFilter(propertiesToIgnore: List<String>): SimpleFilterProvider {
        return SimpleFilterProvider().addFilter(
            "consumptionReportingUnitFilter",
            ConsumptionReportingUnitFilter(propertiesToIgnore)
        )
    }
}
