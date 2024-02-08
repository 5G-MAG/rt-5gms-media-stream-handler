/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2023 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsmediastreamhandler

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.models.*
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsResponse
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.SchemeSupport
import com.fivegmag.a5gmsmediastreamhandler.consumptionReporting.ConsumptionReportingController
import com.fivegmag.a5gmsmediastreamhandler.qoeMetricsReporting.QoEMetricsReportingController
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.Exception
import java.util.*

class MediaSessionHandlerAdapter() {
    companion object {
        const val TAG = "5GMS-MediaSessionHandlerAdapter"
    }
    private var mService: Messenger? = null
    private var bound: Boolean = false
    private lateinit var exoPlayerAdapter: ExoPlayerAdapter
    private lateinit var consumptionReportingController: ConsumptionReportingController
    private lateinit var qoeMetricsReportingController: QoEMetricsReportingController
    private lateinit var serviceConnectedCallbackFunction: () -> Unit

    /**
     * Handler of incoming messages from clients.
     */
    @UnstableApi
    inner class IncomingHandler() : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SessionHandlerMessageTypes.SESSION_HANDLER_TRIGGERS_PLAYBACK -> handleSessionHandlerTriggersPlaybackMessage(
                    msg
                )

                SessionHandlerMessageTypes.GET_CONSUMPTION_REPORT -> handleGetConsumptionReportMessage(
                    msg
                )

                SessionHandlerMessageTypes.UPDATE_PLAYBACK_CONSUMPTION_REPORTING_CONFIGURATION -> handleUpdatePlaybackConsumptionReportingConfiguration(
                    msg
                )

                SessionHandlerMessageTypes.GET_QOE_METRICS_CAPABILITIES -> handleGetQoeMetricsCapabilitiesMessage(
                    msg
                )
                SessionHandlerMessageTypes.GET_QOE_METRICS_REPORT -> handleGetQoeMetricsReport(
                    msg
                )

                else -> super.handleMessage(msg)
            }
        }

        private fun handleSessionHandlerTriggersPlaybackMessage(msg: Message) {
            val bundle: Bundle = msg.data
            bundle.classLoader = PlaybackRequest::class.java.classLoader
            val playbackRequest: PlaybackRequest? = bundle.getParcelable("playbackRequest")

            if (playbackRequest != null && playbackRequest.entryPoints.size > 0) {
                val dashEntryPoints: List<EntryPoint> =
                    playbackRequest.entryPoints.filter { entryPoint -> entryPoint.contentType == ContentTypes.DASH }

                if (dashEntryPoints.isNotEmpty()) {
                    val mpdUrl = dashEntryPoints[0].locator
                    exoPlayerAdapter.handleSourceChange()
                    consumptionReportingController.setCurrentConsumptionReportingConfiguration(
                        playbackRequest.playbackConsumptionReportingConfiguration
                    )
                    exoPlayerAdapter.attach(mpdUrl, ContentTypes.DASH)
                    exoPlayerAdapter.preload()
                    exoPlayerAdapter.play()
                }
            }
        }

        private fun handleUpdatePlaybackConsumptionReportingConfiguration(msg: Message) {
            val bundle: Bundle = msg.data
            bundle.classLoader = PlaybackConsumptionReportingConfiguration::class.java.classLoader
            val playbackConsumptionReportingConfiguration: PlaybackConsumptionReportingConfiguration? =
                bundle.getParcelable("playbackConsumptionReportingConfiguration")

            if (playbackConsumptionReportingConfiguration != null) {
                consumptionReportingController.setCurrentConsumptionReportingConfiguration(
                    playbackConsumptionReportingConfiguration
                )
            }
        }

        private fun handleGetConsumptionReportMessage(msg: Message) {
            val bundle: Bundle = msg.data
            bundle.classLoader = PlaybackConsumptionReportingConfiguration::class.java.classLoader
            val playbackConsumptionReportingConfiguration: PlaybackConsumptionReportingConfiguration? =
                bundle.getParcelable("playbackConsumptionReportingConfiguration")

            if (playbackConsumptionReportingConfiguration != null) {
                consumptionReportingController.setCurrentConsumptionReportingConfiguration(
                    playbackConsumptionReportingConfiguration
                )
            }
            sendConsumptionReport()
            consumptionReportingController.cleanConsumptionReportingList()
        }

        private fun handleGetQoeMetricsCapabilitiesMessage(msg: Message) {
            val bundle: Bundle = msg.data
            bundle.classLoader = QoeMetricsRequest::class.java.classLoader
            val playbackMetricsRequests: ArrayList<QoeMetricsRequest>? =
                bundle.getParcelableArrayList("qoeMetricsRequest")
            val results: ArrayList<SchemeSupport> = ArrayList()

            if (playbackMetricsRequests != null) {
                for (playbackMetricsRequest in playbackMetricsRequests) {
                    val supported =
                        qoeMetricsReportingController.isMetricsSchemeSupported(playbackMetricsRequest.scheme)
                    Log.d(TAG, "${playbackMetricsRequest.scheme} is supported:")
                    results.add(SchemeSupport(playbackMetricsRequest.scheme, supported))
                }
            }

            val responseMessenger: Messenger = msg.replyTo
            val msgResponse: Message = Message.obtain(
                null,
                SessionHandlerMessageTypes.REPORT_QOE_METRICS_CAPABILITIES
            )
            val responseBundle = Bundle()
            responseBundle.putParcelableArrayList("schemeSupport", results)
            msgResponse.data = responseBundle
            responseMessenger.send(msgResponse)
        }

        private fun handleGetQoeMetricsReport(msg: Message) {
            val bundle: Bundle = msg.data
            bundle.classLoader = QoeMetricsRequest::class.java.classLoader
            val qoeMetricsRequest: QoeMetricsRequest? = bundle.getParcelable("qoeMetricsRequest")
            val scheme = qoeMetricsRequest?.scheme

            val qoeMetricsReport = getQoeMetrics(qoeMetricsRequest)
            Log.d(TAG, "Media Session Handler requested playback stats for scheme $scheme")

            val responseMessenger: Messenger = msg.replyTo
            val msgResponse: Message = Message.obtain(
                null,
                SessionHandlerMessageTypes.REPORT_QOE_METRICS
            )

            val responseBundle = Bundle()
            val playbackMetricsResponse = QoeMetricsResponse(
                qoeMetricsReport,
                qoeMetricsRequest?.metricReportingConfigurationId
            )
            responseBundle.putParcelable("qoeMetricsResponse", playbackMetricsResponse)
            msgResponse.data = responseBundle
            responseMessenger.send(msgResponse)
        }

        @UnstableApi
        private fun getQoeMetrics(
            qoeMetricsRequest: QoeMetricsRequest?
        ): String {
            return try {
                qoeMetricsReportingController.getQoeMetricsReport(qoeMetricsRequest)

            } catch (e: Exception) {
                e.message?.let { Log.i(TAG, it) }
                return ""
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private val mMessenger: Messenger = Messenger(IncomingHandler())

    /**
     * Class for interacting with the main interface of the service.
     */
    private val mConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mService = Messenger(service)
            try {
                val msg: Message = Message.obtain(
                    null,
                    SessionHandlerMessageTypes.REGISTER_CLIENT
                )
                msg.replyTo = mMessenger
                mService!!.send(msg)
                bound = true
                Log.i(TAG, "Service connected")
                serviceConnectedCallbackFunction()
            } catch (e: RemoteException) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null
            bound = false
        }
    }

    @UnstableApi
    fun initialize(
        context: Context,
        epa: ExoPlayerAdapter,
        onConnectionToMediaSessionHandlerEstablished: () -> (Unit)
    ) {
        exoPlayerAdapter = epa
        consumptionReportingController = ConsumptionReportingController(context)
        consumptionReportingController.initialize()

        qoeMetricsReportingController = QoEMetricsReportingController()
        qoeMetricsReportingController.setDefaultExoplayerQoeMetricsReporter(exoPlayerAdapter)

        EventBus.getDefault().register(this)

        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.fivegmag.a5gmsmediasessionhandler",
                "com.fivegmag.a5gmsmediasessionhandler.MediaSessionHandlerMessengerService"
            )
            if (context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
                Log.i(TAG, "Binding to MediaSessionHandler service returned true")
            } else {
                Log.i(TAG, "Binding to MediaSessionHandler service returned false")
            }
            serviceConnectedCallbackFunction = onConnectionToMediaSessionHandlerEstablished
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "Can't bind to MediaSessionHandler, check permission in Manifest"
            )
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent) {
        if (event.playbackState == PlayerStates.ENDED) {
            sendConsumptionReport()
        }

        updatePlaybackState(event.playbackState)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onCellInfoUpdatedEvent(event: CellInfoUpdatedEvent) {
        val playbackConsumptionReportingConfiguration =
            consumptionReportingController.getPlaybackConsumptionReportingConfiguration()
        if (playbackConsumptionReportingConfiguration != null && playbackConsumptionReportingConfiguration.locationReporting == true) {
            sendConsumptionReport()
        }
    }

    fun reset(context: Context) {
        if (bound) {
            context.unbindService(mConnection)
            bound = false
        }
    }

    fun setM5Endpoint(m5BaseUrl: String) {
        val msg: Message = Message.obtain(
            null,
            SessionHandlerMessageTypes.SET_M5_ENDPOINT
        )
        val bundle = Bundle()
        bundle.putString("m5BaseUrl", m5BaseUrl)
        msg.data = bundle
        msg.replyTo = mMessenger
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun updatePlaybackState(state: String) {
        if (!bound) return
        val msg: Message = Message.obtain(null, SessionHandlerMessageTypes.STATUS_MESSAGE)
        val bundle = Bundle()
        bundle.putString("playbackState", state)
        msg.data = bundle
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun initializePlaybackByServiceListEntry(serviceListEntry: ServiceListEntry) {
        if (!bound) return
        // Create and send a message to the service, using a supported 'what' value
        val msg: Message = Message.obtain(
            null,
            SessionHandlerMessageTypes.START_PLAYBACK_BY_SERVICE_LIST_ENTRY_MESSAGE
        )
        val bundle = Bundle()
        bundle.putParcelable("serviceListEntry", serviceListEntry)
        msg.data = bundle
        msg.replyTo = mMessenger
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    @UnstableApi
    fun sendConsumptionReport() {
        val mediaPlayerEntry = exoPlayerAdapter.getCurrentManifestUri()
        val consumptionReport =
            consumptionReportingController.getConsumptionReport(mediaPlayerEntry)
        val msg: Message = Message.obtain(
            null,
            SessionHandlerMessageTypes.CONSUMPTION_REPORT
        )

        val bundle = Bundle()
        bundle.putString("consumptionReport", consumptionReport)
        msg.data = bundle
        msg.replyTo = mMessenger
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    @UnstableApi
    fun resetState() {
        consumptionReportingController.resetState()
    }

}