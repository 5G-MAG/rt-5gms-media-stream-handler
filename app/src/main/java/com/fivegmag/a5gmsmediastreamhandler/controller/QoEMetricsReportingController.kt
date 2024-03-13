package com.fivegmag.a5gmsmediastreamhandler.controller

import android.os.Bundle
import android.os.Message
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsResponse
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.MediaSessionHandlerAdapter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.QoeMetricsReporterExoplayer
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.Exception
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

@UnstableApi
class QoEMetricsReportingController(
    private val exoPlayerAdapter: ExoPlayerAdapter,
    private val outgoingMessageHandler: OutgoingMessageHandler
) : Controller() {

    companion object {
        const val TAG = "5GMS-QoEMetricsReportingController"
    }

    private val availableQoeMetricsReporterExoplayerByScheme =
        mutableMapOf<String, KClass<QoeMetricsReporterExoplayer>>()
    private val activeQoeMetricsReporterById = mutableMapOf<String, QoeMetricsReporterExoplayer>()
    private var lastQoeMetricsRequestsById = mutableMapOf<String, QoeMetricsRequest>()

    override fun initialize() {
        EventBus.getDefault().register(this)
        registerExoplayerQoeMetricsReporter()
    }

    private fun registerExoplayerQoeMetricsReporter() {
        val scheme = QoeMetricsReporterExoplayer.SCHEME
        availableQoeMetricsReporterExoplayerByScheme[scheme] = QoeMetricsReporterExoplayer::class
    }

    override fun handleTriggerPlayback(playbackRequest: PlaybackRequest) {
        if (exoPlayerAdapter.hasActiveMediaItem()) {
            triggerQoeMetricsReports()
        }
        setLastQoeMetricsRequests(
            playbackRequest.qoeMetricsRequests
        )
    }

    private fun triggerQoeMetricsReports() {
        try {
            if (lastQoeMetricsRequestsById.isEmpty()) {
                return
            }
            for (qoeMetricsRequest in lastQoeMetricsRequestsById.values) {
                triggerSingleQoeMetricsReport(qoeMetricsRequest)
            }
        } catch (e: Exception) {
            Log.d(TAG, e.message.toString())
        }
    }

    private fun triggerSingleQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest) {
        try {
            val qoeMetricsReport = getQoeMetricsReport(qoeMetricsRequest)
            sendQoeMetricsReport(
                qoeMetricsReport,
                qoeMetricsRequest.metricsReportingConfigurationId
            )
        } catch (e: Exception) {
            Log.d(TAG, e.message.toString())
        }
    }

    private fun setLastQoeMetricsRequests(qoeMetricsRequests: ArrayList<QoeMetricsRequest>) {
        for (qoeMetricsRequest in qoeMetricsRequests) {
            lastQoeMetricsRequestsById[qoeMetricsRequest.metricsReportingConfigurationId] =
                qoeMetricsRequest
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent) {
        if (event.playbackState == PlayerStates.ENDED) {
            triggerQoeMetricsReports()
        }
    }

    private fun getQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest?): String {
        if (qoeMetricsRequest == null) {
            throw Exception("qoeMetricsRequest can not be null")
        }


        val qoeMetricsReporterForConfigurationId =
            initializeQoeMetricsReporterForConfigurationId(qoeMetricsRequest)
                ?: throw Exception("No available QoeMetricsReporter for scheme ${qoeMetricsRequest.scheme} and id ${qoeMetricsRequest.metricsReportingConfigurationId}")


        val qoeMetricsReport =
            qoeMetricsReporterForConfigurationId.getQoeMetricsReport(
                qoeMetricsRequest,
                reportingClientId
            )
        qoeMetricsReporterForConfigurationId.reset()

        return qoeMetricsReport
    }

    private fun initializeQoeMetricsReporterForConfigurationId(qoeMetricsRequest: QoeMetricsRequest): QoeMetricsReporterExoplayer? {
        val qoeMetricsReporterForConfigurationId =
            activeQoeMetricsReporterById[qoeMetricsRequest.metricsReportingConfigurationId]

        if (qoeMetricsReporterForConfigurationId != null) {
            return qoeMetricsReporterForConfigurationId
        }

        val qoeMetricsReporterExoplayerForScheme =
            availableQoeMetricsReporterExoplayerByScheme[qoeMetricsRequest.scheme] ?: return null
        val instance =
            qoeMetricsReporterExoplayerForScheme.primaryConstructor?.call(exoPlayerAdapter)
        instance?.initialize()

        if (instance != null) {
            activeQoeMetricsReporterById[qoeMetricsRequest.metricsReportingConfigurationId] =
                instance
        }

        return instance
    }

    private fun sendQoeMetricsReport(
        qoeMetricsReport: String,
        metricsReportingConfigurationId: String
    ) {
        val bundle = Bundle()
        val playbackMetricsResponse = QoeMetricsResponse(
            qoeMetricsReport,
            metricsReportingConfigurationId
        )
        bundle.putParcelable("qoeMetricsResponse", playbackMetricsResponse)

        outgoingMessageHandler.sendMessageByTypeAndBundle(
            SessionHandlerMessageTypes.REPORT_QOE_METRICS,
            bundle
        )
    }

    fun handleGetQoeMetricsReport(msg: Message) {
        val bundle: Bundle = msg.data
        bundle.classLoader = QoeMetricsRequest::class.java.classLoader
        val qoeMetricsRequest: QoeMetricsRequest? = bundle.getParcelable("qoeMetricsRequest")
        val scheme = qoeMetricsRequest?.scheme

        Log.d(
            MediaSessionHandlerAdapter.TAG,
            "Media Session Handler requested QoE metrics for scheme $scheme"
        )

        if (qoeMetricsRequest != null) {
            triggerSingleQoeMetricsReport(qoeMetricsRequest)
        }
    }

    override fun reset() {
        resetState()
    }

    override fun resetState() {
        lastQoeMetricsRequestsById.clear()
    }

}