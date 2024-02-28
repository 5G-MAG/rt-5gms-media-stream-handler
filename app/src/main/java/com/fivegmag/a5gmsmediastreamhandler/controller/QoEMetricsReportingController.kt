package com.fivegmag.a5gmsmediastreamhandler.controller

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsResponse
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.SchemeSupport
import com.fivegmag.a5gmsmediastreamhandler.MediaSessionHandlerAdapter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.player.QoeMetricsReporter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.QoeMetricsReporterExoplayer
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import java.lang.Exception
import java.util.ArrayList

@UnstableApi
class QoEMetricsReportingController(
    private val exoPlayerAdapter: ExoPlayerAdapter,
    private val outgoingMessageHandler: OutgoingMessageHandler
) {

    private lateinit var reportingClientId: String
    private val activeQoeMetricsReporter = mutableMapOf<String, QoeMetricsReporter>()

    fun initialize(repClientId: String) {
        reportingClientId = repClientId
        setDefaultExoplayerQoeMetricsReporter(exoPlayerAdapter)
    }

    private fun setDefaultExoplayerQoeMetricsReporter(exoPlayerAdapter: ExoPlayerAdapter) {
        val qoeMetricsReporterExoplayer = QoeMetricsReporterExoplayer(exoPlayerAdapter)
        qoeMetricsReporterExoplayer.initialize()
        setQoeMetricsReporterForScheme(qoeMetricsReporterExoplayer)
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
            ?: throw Exception("No QoeMetricsReporter for scheme $qoeMetricsRequest.scheme")

        val qoeMetricsReport = qoeMetricsReporterForScheme.getQoeMetricsReport(qoeMetricsRequest)
        qoeMetricsReporterForScheme.reset()

        return qoeMetricsReport
    }

    private fun isMetricsSchemeSupported(scheme: String): Boolean {
        val qoeMetricsReporterForScheme = activeQoeMetricsReporter[scheme]

        return qoeMetricsReporterForScheme != null
    }

    fun handleGetQoeMetricsCapabilitiesMessage(msg: Message) {
        val bundle: Bundle = msg.data
        bundle.classLoader = QoeMetricsRequest::class.java.classLoader
        val playbackMetricsRequests: ArrayList<QoeMetricsRequest>? =
            bundle.getParcelableArrayList("qoeMetricsRequest")
        val results: ArrayList<SchemeSupport> = ArrayList()

        if (playbackMetricsRequests != null) {
            for (playbackMetricsRequest in playbackMetricsRequests) {
                val supported =
                    isMetricsSchemeSupported(playbackMetricsRequest.scheme)
                Log.d(
                    MediaSessionHandlerAdapter.TAG,
                    "${playbackMetricsRequest.scheme} is supported: $supported"
                )
                results.add(SchemeSupport(playbackMetricsRequest.scheme, supported))
            }
        }
        val responseBundle = Bundle()
        responseBundle.putParcelableArrayList("schemeSupport", results)
        outgoingMessageHandler.sendMessageByTypeAndBundle(
            SessionHandlerMessageTypes.REPORT_QOE_METRICS_CAPABILITIES,
            responseBundle
        )
    }

    fun handleGetQoeMetricsReport(msg: Message) {
        val bundle: Bundle = msg.data
        bundle.classLoader = QoeMetricsRequest::class.java.classLoader
        val qoeMetricsRequest: QoeMetricsRequest? = bundle.getParcelable("qoeMetricsRequest")
        val scheme = qoeMetricsRequest?.scheme

        val qoeMetricsReport = getQoeMetricsReport(qoeMetricsRequest)
        Log.d(
            MediaSessionHandlerAdapter.TAG,
            "Media Session Handler requested QoE metrics for scheme $scheme"
        )

        val responseBundle = Bundle()
        val playbackMetricsResponse = QoeMetricsResponse(
            qoeMetricsReport,
            qoeMetricsRequest?.metricReportingConfigurationId
        )
        responseBundle.putParcelable("qoeMetricsResponse", playbackMetricsResponse)

        outgoingMessageHandler.sendMessageByTypeAndBundle(
            SessionHandlerMessageTypes.REPORT_QOE_METRICS,
            responseBundle
        )
    }

    fun reset() {

    }

}