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
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes

import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.models.*


class MediaSessionHandlerAdapter() {

    private val TAG: String = "MediaSessionHandlerAdapter"

    /** Messenger for communicating with the service.  */
    private var mService: Messenger? = null

    /** Flag indicating whether we have called bind on the service.  */
    private var bound: Boolean = false

    private lateinit var exoPlayerAdapter: ExoPlayerAdapter

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
                SessionHandlerMessageTypes.GET_PLAYBACK_METRIC_CAPABILITIES -> handlePlaybackMetricsCapabilitiesMessage(
                    msg
                )
                else -> super.handleMessage(msg)
            }
        }

        private fun handleSessionHandlerTriggersPlaybackMessage(msg: Message) {
            val bundle: Bundle = msg.data
            bundle.classLoader = EntryPoint::class.java.classLoader
            val entryPoints: ArrayList<EntryPoint>? = bundle.getParcelableArrayList("entryPoints")

            if (entryPoints != null && entryPoints.size > 0) {
                val dashEntryPoints: List<EntryPoint> =
                    entryPoints.filter { entryPoint -> entryPoint.contentType == ContentTypes.DASH }

                if (dashEntryPoints.isNotEmpty()) {
                    startPlayback(dashEntryPoints[0].locator)
                }
            }
        }

        private fun handlePlaybackMetricsCapabilitiesMessage(msg: Message) {
            val bundle: Bundle = msg.data
            val schemes: ArrayList<String>? = bundle.getStringArrayList("metricsSchemes")
            val results: ArrayList<SchemeSupport> = ArrayList()

            if (schemes != null) {
                for (scheme in schemes) {
                    Log.d(TAG, "Scheme $scheme")
                    val supported = exoPlayerAdapter.isMetricsSchemeSupported(scheme)
                    Log.d(TAG, "$scheme is supported:")
                    results.add(SchemeSupport(scheme, supported))
                }
            }

            val responseMessenger: Messenger = msg.replyTo
            val msgResponse: Message = Message.obtain(
                null,
                SessionHandlerMessageTypes.REPORT_PLAYBACK_METRICS_CAPABILITIES
            )
            bundle.putParcelableArrayList("schemeSupport", results)
            msgResponse.data = bundle
            responseMessenger.send(msgResponse)
        }

        private fun startPlayback(url: String) {
            exoPlayerAdapter.attach(url)
            exoPlayerAdapter.preload()
            exoPlayerAdapter.play()
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
                msg.replyTo = mMessenger;
                mService!!.send(msg);
                bound = true
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

    fun initialize(
        context: Context,
        epa: ExoPlayerAdapter,
        onConnectionToMediaSessionHandlerEstablished: () -> (Unit)
    ) {
        exoPlayerAdapter = epa

        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.fivegmag.a5gmsmediasessionhandler",
                "com.fivegmag.a5gmsmediasessionhandler.MediaSessionHandlerMessengerService"
            )
            if (context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
                Log.i(TAG, "Binding to MediaSessionHandler service returned true");
                Toast.makeText(
                    context,
                    "Successfully bound to Media Session Handler",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Log.d(TAG, "Binding to MediaSessionHandler service returned false");
            }
            serviceConnectedCallbackFunction = onConnectionToMediaSessionHandlerEstablished
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "Can't bind to ModemWatcherService, check permission in Manifest"
            );
        }
    }

    fun reset(context: Context) {
        if (bound) {
            context.unbindService(mConnection)
            bound = false
        }
    }

    fun updateLookupTable(m8Data: M8Model) {
        val msg: Message = Message.obtain(
            null,
            SessionHandlerMessageTypes.UPDATE_LOOKUP_TABLE
        )
        val bundle = Bundle()
        bundle.putParcelable("m8Data", m8Data)
        msg.data = bundle
        msg.replyTo = mMessenger;
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
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
        msg.replyTo = mMessenger;
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun updatePlaybackState(state: String) {
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

    fun reportMetrics() {
        if (!bound) return
        // Create and send a message to the service, using a supported 'what' value
        val msg: Message = Message.obtain(null, SessionHandlerMessageTypes.METRIC_REPORTING_MESSAGE)
        val bundle = Bundle()
        bundle.putString("metricData", "")
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
        msg.replyTo = mMessenger;
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }


}