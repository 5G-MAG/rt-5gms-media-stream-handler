/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2023 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsmediastreamhandler

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.models.*
import com.fivegmag.a5gmsmediastreamhandler.controller.ConsumptionReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.QoEMetricsReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.SessionController
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.IncomingMessageHandler
import com.fivegmag.a5gmsmediastreamhandler.service.MessengerService
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import java.util.*

@UnstableApi
class MediaSessionHandlerAdapter() {
    companion object {
        const val TAG = "5GMS-MediaSessionHandlerAdapter"
    }

    private lateinit var context: Context
    private lateinit var messengerService: MessengerService
    private lateinit var consumptionReportingController: ConsumptionReportingController
    private lateinit var qoeMetricsReportingController: QoEMetricsReportingController
    private lateinit var sessionController: SessionController
    private lateinit var outgoingMessageHandler: OutgoingMessageHandler
    private lateinit var incomingMessageHandler: IncomingMessageHandler
    private val utils = Utils()
    private val exoPlayerAdapter = ExoPlayerAdapter()

    /**
     * API endpoint to initialize Media Session handling. Connects to the Media Session Handler and calls the provided callback function afterwards
     *
     * @param ctxt
     * @param epa
     * @param onConnectionToMediaSessionHandlerEstablished
     */

    fun initialize(
        context: Context,
        onConnectionToMediaSessionHandlerEstablished: () -> (Unit)
    ) {
        this.context = context

        outgoingMessageHandler = OutgoingMessageHandler()
        incomingMessageHandler = IncomingMessageHandler()

        val reportingClientId = generateReportingClientId()

        consumptionReportingController =
            ConsumptionReportingController(exoPlayerAdapter, outgoingMessageHandler)
        consumptionReportingController.reportingClientId = reportingClientId
        consumptionReportingController.initialize()

        qoeMetricsReportingController =
            QoEMetricsReportingController(exoPlayerAdapter, outgoingMessageHandler)
        qoeMetricsReportingController.reportingClientId = reportingClientId
        qoeMetricsReportingController.initialize()

        sessionController = SessionController(
            context,
            exoPlayerAdapter,
            outgoingMessageHandler
        )
        sessionController.initialize()

        incomingMessageHandler.initialize(
            consumptionReportingController,
            qoeMetricsReportingController,
            sessionController
        )

        messengerService = MessengerService(this.context)
        messengerService.initialize(incomingMessageHandler, outgoingMessageHandler)
        messengerService.bind(onConnectionToMediaSessionHandlerEstablished)
    }

    /**
     * The GPSI is either a mobile subscriber ISDN number (MSISDN) or an external identifier
     *
     */
    @SuppressLint("Range")
    fun generateReportingClientId(): String {
        val strGpsi: String
        var strMsisdn = ""
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            strMsisdn = getMsisdn()
        }
        strGpsi = if (strMsisdn != "") {
            strMsisdn
        } else {
            utils.generateUUID()
        }

        return strGpsi
    }

    /**
     * MSISDN = CC + NDC + SN
     *
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getMsisdn(): String {
        var strMsisdn = ""

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val subscriptionManager: SubscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            val subscriptionInfoList: List<SubscriptionInfo> =
                subscriptionManager.activeSubscriptionInfoList
            for (subscriptionInfo in subscriptionInfoList) {
                strMsisdn =
                    subscriptionManager.getPhoneNumber(getActiveSIMIdx(subscriptionInfoList))
            }
        }

        return strMsisdn
    }

    /**
     * In case of multi SIM cards, get the the index of SIM which is used for the traffic
     * If none of them match, use the first as default
     *
     */
    private fun getActiveSIMIdx(subscriptionInfoList: List<SubscriptionInfo>): Int {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simOPName: String = telephonyManager.simOperatorName

        var subscriptionIdx = 1
        for (subscriptionInfo in subscriptionInfoList) {
            val subscriptionId = subscriptionInfo.subscriptionId
            val subscriptionName: String = subscriptionInfo.carrierName as String

            if (subscriptionName == simOPName) {
                subscriptionIdx = subscriptionId
            }
        }

        return subscriptionIdx
    }

    /**
     * API endpoint to close the connection to the MediaSessionHandler
     *
     */
    fun reset() {
        messengerService.reset()
    }

    /**
     * API endpoint for application to set the M5 endpoint in the MediaSessionHandler.
     * An IPC call is send by this function to the MediaSessionHandler to update the M5 URL.
     *
     * @param m5BaseUrl
     */
    fun setM5Endpoint(m5BaseUrl: String) {
        messengerService.setM5Endpoint(m5BaseUrl)
    }

    /**
     * API endpoint to send a ServiceListEntry to the MediaSessionHandler to trigger the playback process.
     *
     * @param serviceListEntry
     */
    fun initializePlaybackByServiceListEntry(serviceListEntry: ServiceListEntry) {
        messengerService.initializePlaybackByServiceListEntry(serviceListEntry)
    }

    /**
     * API endpoint to get the instance of ExoPlayerAdapter
     *
     */
    fun getExoPlayerAdapter(): ExoPlayerAdapter {
        return exoPlayerAdapter
    }
}