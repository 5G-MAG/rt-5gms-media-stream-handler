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
import com.fivegmag.a5gmsmediastreamhandler.player.QoeMetricsReporter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.QoeMetricsReporterExoplayer
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.Exception

@UnstableApi
class QoEMetricsReportingController(
    private val exoPlayerAdapter: ExoPlayerAdapter,
    private val outgoingMessageHandler: OutgoingMessageHandler
) : Controller() {

    companion object {
        const val TAG = "5GMS-QoEMetricsReportingController"
    }

    private val activeQoeMetricsReporter = mutableMapOf<String, QoeMetricsReporter>()
    private var lastQoeMetricsRequests = mutableMapOf<String, QoeMetricsRequest>()

    override fun initialize() {
        EventBus.getDefault().register(this)
        setDefaultExoplayerQoeMetricsReporter()
    }

    private fun setDefaultExoplayerQoeMetricsReporter() {
        val qoeMetricsReporterExoplayer = QoeMetricsReporterExoplayer(exoPlayerAdapter)
        qoeMetricsReporterExoplayer.initialize()
        setQoeMetricsReporterForScheme(qoeMetricsReporterExoplayer)
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
            if (lastQoeMetricsRequests.isEmpty()) {
                return
            }
            for (qoeMetricsRequest in lastQoeMetricsRequests.values) {
                triggerSingleQoeMetricsReport(qoeMetricsRequest)
            }
        } catch (e: Exception) {
            Log.d(TAG, e.message.toString())
        }
    }

    private fun triggerSingleQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest) {
        try {
            if (qoeMetricsRequest.metricReportingConfigurationId != null) {
                val qoeMetricsReport = getQoeMetricsReport(qoeMetricsRequest)
                sendQoeMetricsReport(
                    qoeMetricsReport,
                    qoeMetricsRequest.metricReportingConfigurationId!!
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, e.message.toString())
        }
    }

    private fun setLastQoeMetricsRequests(qoeMetricsRequests: ArrayList<QoeMetricsRequest>) {
        for (qoeMetricsRequest in qoeMetricsRequests) {
            if (qoeMetricsRequest.metricReportingConfigurationId != null) {
                lastQoeMetricsRequests[qoeMetricsRequest.metricReportingConfigurationId!!] =
                    qoeMetricsRequest
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent) {
        if (event.playbackState == PlayerStates.ENDED) {
            triggerQoeMetricsReports()
        }
    }


    private fun setQoeMetricsReporterForScheme(qoeMetricsReporter: QoeMetricsReporter) {
        val scheme = qoeMetricsReporter.getQoeMetricsReportingScheme()
        activeQoeMetricsReporter[scheme] = qoeMetricsReporter
    }

    private fun getQoeMetricsReport(qoeMetricsRequest: QoeMetricsRequest?): String {
        if (qoeMetricsRequest == null) {
            throw Exception("qoeMetricsRequest can not be null")
        }

        val qoeMetricsReporterForScheme = activeQoeMetricsReporter[qoeMetricsRequest.scheme]
            ?: throw Exception("No QoE Metrics Reporter for scheme ${qoeMetricsRequest.scheme}")

        val qoeMetricsReport =
            qoeMetricsReporterForScheme.getQoeMetricsReport(qoeMetricsRequest, reportingClientId)
        qoeMetricsReporterForScheme.reset()

        return qoeMetricsReport
    }

    private fun sendQoeMetricsReport(
        qoeMetricsReport: String,
        metricReportingConfigurationId: String
    ) {
        val bundle = Bundle()
        val playbackMetricsResponse = QoeMetricsResponse(
            qoeMetricsReport,
            metricReportingConfigurationId
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
        lastQoeMetricsRequests.clear()
    }

}