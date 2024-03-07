package com.fivegmag.a5gmsmediastreamhandler.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Messenger
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.controller.ConsumptionReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.QoEMetricsReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.SessionController
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter

class IncomingMessageHandler(private val context: Context) {

    private val utils = Utils()
    private lateinit var consumptionReportingController: ConsumptionReportingController
    private lateinit var qoeMetricsReportingController: QoEMetricsReportingController
    private lateinit var sessionController: SessionController
    private lateinit var exoPlayerAdapter: ExoPlayerAdapter

    @UnstableApi
    private val incomingMessenger = Messenger(IncomingHandler())

    @UnstableApi
    inner class IncomingHandler() : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SessionHandlerMessageTypes.TRIGGER_PLAYBACK -> handleTriggerPlayback(msg)

                SessionHandlerMessageTypes.GET_CONSUMPTION_REPORT -> handleGetConsumptionReport(msg)

                SessionHandlerMessageTypes.UPDATE_PLAYBACK_CONSUMPTION_REPORTING_CONFIGURATION -> handleUpdatePlaybackConsumptionReportingConfiguration(
                    msg
                )

                SessionHandlerMessageTypes.GET_QOE_METRICS_REPORT -> handleGetQoeMetricsReport(msg)

                else -> super.handleMessage(msg)
            }
        }
    }

    @UnstableApi
    private fun handleTriggerPlayback(msg: Message) {
        val bundle: Bundle = msg.data
        bundle.classLoader = PlaybackRequest::class.java.classLoader
        val playbackRequest: PlaybackRequest? = bundle.getParcelable("playbackRequest")
        if (playbackRequest != null) {
            consumptionReportingController.handleTriggerPlayback(playbackRequest)
            qoeMetricsReportingController.handleTriggerPlayback(playbackRequest)
            sessionController.handleTriggerPlayback(playbackRequest)
        }
    }

    @UnstableApi
    private fun handleGetConsumptionReport(msg: Message) {
        consumptionReportingController.handleGetConsumptionReport(msg)
    }

    @UnstableApi
    private fun handleUpdatePlaybackConsumptionReportingConfiguration(msg: Message) {
        consumptionReportingController.updateLastConsumptionRequest(msg)
    }

    @UnstableApi
    private fun handleGetQoeMetricsReport(msg: Message) {
        qoeMetricsReportingController.handleGetQoeMetricsReport(msg)
    }

    @UnstableApi
    fun initialize(
        exoAdapter: ExoPlayerAdapter,
        outgoingMessageHandler: OutgoingMessageHandler
    ) {
        val reportingClientId = generateReportingClientId()
        exoPlayerAdapter = exoAdapter
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
            exoAdapter,
            outgoingMessageHandler
        )
        sessionController.initialize()
    }

    @UnstableApi
    fun getIncomingMessenger(): Messenger {
        return incomingMessenger
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

    @UnstableApi
    fun reset() {
        consumptionReportingController.reset()
        sessionController.reset()
        qoeMetricsReportingController.reset()
    }

}