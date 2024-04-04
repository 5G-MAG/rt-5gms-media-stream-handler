package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Messenger
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.controller.IConsumptionReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.IQoEMetricsReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.ISessionController

class IncomingMessageHandler() {

    private lateinit var consumptionReportingController: IConsumptionReportingController
    private lateinit var qoeMetricsReportingController: IQoEMetricsReportingController
    private lateinit var sessionController: ISessionController

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