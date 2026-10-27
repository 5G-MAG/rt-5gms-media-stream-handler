package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Messenger
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.models.StreamingAccess
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.controller.IConsumptionReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.IQoEMetricsReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.ISessionController

class IncomingMessageHandler() {

    private lateinit var consumptionReportingController: IConsumptionReportingController
    private lateinit var qoeMetricsReportingController: IQoEMetricsReportingController
    private lateinit var sessionController: ISessionController

    var generalSessionListener: GeneralSessionListener? = null
    var dynamicPolicyListener: DynamicPolicyListener? = null
    var networkAssistanceListener: NetworkAssistanceListener? = null

    @UnstableApi
    private val incomingMessenger = Messenger(IncomingHandler())

    @UnstableApi
    inner class IncomingHandler() : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SessionHandlerMessageTypes.TRIGGER_PLAYBACK -> handleTriggerPlayback(msg)

                SessionHandlerMessageTypes.CMCD_LOCATION_UPDATE -> sessionController.handleCmcdLocationUpdate(
                    msg.data.getString(SessionHandlerBundleKeys.METRICS_REPORTING_CONFIGURATION_ID) ?: "",
                    msg.data.getBoolean(SessionHandlerBundleKeys.LOCATED)
                )

                SessionHandlerMessageTypes.GET_CONSUMPTION_REPORT -> handleGetConsumptionReport(msg)

                SessionHandlerMessageTypes.UPDATE_PLAYBACK_CONSUMPTION_REPORTING_CONFIGURATION -> handleUpdatePlaybackConsumptionReportingConfiguration(
                    msg
                )

                SessionHandlerMessageTypes.GET_QOE_METRICS_REPORT -> handleGetQoeMetricsReport(msg)

                SessionHandlerMessageTypes.CREATE_MEDIA_DELIVERY_SESSION_REPLY -> generalSessionListener?.onMediaDeliverySessionCreated(
                    msg.data.getString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER),
                    msg.data.getString(SessionHandlerBundleKeys.SESSION_HANDLING_STATE)
                )

                SessionHandlerMessageTypes.GET_SESSION_STATUS_REPLY -> generalSessionListener?.onSessionStatus(
                    msg.data.getString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER),
                    msg.data.getString(SessionHandlerBundleKeys.CONTENT_IDENTIFIER),
                    msg.data.getString(SessionHandlerBundleKeys.SESSION_HANDLING_STATE)
                )

                SessionHandlerMessageTypes.GET_CONSUMPTION_REPORTING_STATUS_REPLY -> generalSessionListener?.onConsumptionReportingStatus(
                    msg.data.getString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER),
                    msg.data.getString(SessionHandlerBundleKeys.CONSUMPTION_REPORT)
                )

                SessionHandlerMessageTypes.GET_METRICS_REPORTING_STATUS_REPLY -> generalSessionListener?.onMetricsReportingStatus(
                    msg.data.getString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER),
                    msg.data.getString(SessionHandlerBundleKeys.PROVISIONING_SESSION_ID),
                    msg.data.getString(SessionHandlerBundleKeys.METRICS_REPORTING_CONFIGURATION_ID),
                    msg.data.getString(SessionHandlerBundleKeys.SCHEME),
                    msg.data.getString(SessionHandlerBundleKeys.METRICS_REPORT)
                )

                SessionHandlerMessageTypes.GET_SERVICE_OPERATION_POINTS_REPLY -> BundleReader(msg.data).let {
                    dynamicPolicyListener?.onServiceOperationPoints(it.string(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER), DynamicPolicyPayloads.readServiceOperationPoints(it))
                }

                SessionHandlerMessageTypes.GET_BDT_INFO_REPLY -> dynamicPolicyListener?.onBdtInfo(DynamicPolicyPayloads.readBdtInfo(BundleReader(msg.data)))

                SessionHandlerMessageTypes.ACTIVATE_POLICY_REPLY -> dynamicPolicyListener?.onPolicyActivation(DynamicPolicyPayloads.readActivation(BundleReader(msg.data)))

                SessionHandlerMessageTypes.DEACTIVATE_POLICY_REPLY -> BundleReader(msg.data).let {
                    dynamicPolicyListener?.onPolicyDeactivation(
                        it.string(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER),
                        it.bool(SessionHandlerBundleKeys.SUCCEEDED),
                        it.string(SessionHandlerBundleKeys.ERROR_REASON)
                    )
                }

                SessionHandlerMessageTypes.GET_DYNAMIC_POLICY_STATUS_REPLY -> BundleReader(msg.data).let {
                    dynamicPolicyListener?.onDynamicPolicyStatus(it.string(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER), DynamicPolicyPayloads.readStatus(it))
                }

                SessionHandlerMessageTypes.REQUEST_BIT_RATE_RECOMMENDATION_REPLY ->
                    networkAssistanceListener?.onBitRateRecommendation(NetworkAssistancePayloads.readRecommendation(BundleReader(msg.data)))

                SessionHandlerMessageTypes.REQUEST_DELIVERY_BOOST_REPLY ->
                    networkAssistanceListener?.onDeliveryBoost(NetworkAssistancePayloads.readBoost(BundleReader(msg.data)))

                SessionHandlerMessageTypes.NOTIFICATION_EVENT -> {
                    msg.data.classLoader = StreamingAccess::class.java.classLoader
                    val event = msg.data.getString(SessionHandlerBundleKeys.EVENT)
                    if (event != null) generalSessionListener?.onNotification(event, msg.data)
                }

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
        consumptionReportingController: IConsumptionReportingController,
        qoEMetricsReportingController: IQoEMetricsReportingController,
        sessionController: ISessionController
    ) {
        this.consumptionReportingController = consumptionReportingController
        this.qoeMetricsReportingController = qoEMetricsReportingController
        this.sessionController = sessionController
    }

    @UnstableApi
    fun getIncomingMessenger(): Messenger {
        return incomingMessenger
    }


    @UnstableApi
    fun reset() {
        consumptionReportingController.reset()
        sessionController.reset()
        qoeMetricsReportingController.reset()
    }

}