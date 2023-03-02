package com.example.a5gms_mediastreamhandler

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.example.a5gms_mediastreamhandler.helpers.SessionHandlerMessageTypes
import com.example.a5gms_mediastreamhandler.models.ServiceAccessInformation


class MediaSessionHandlerAdapter() {

    /** Messenger for communicating with the service.  */
    private var mService: Messenger? = null

    /** Flag indicating whether we have called bind on the service.  */
    private var bound: Boolean = false

    private lateinit var exoPlayerAdapter: ExoPlayerAdapter

    private lateinit var currentServiceAccessInformation: ServiceAccessInformation

    private lateinit var serviceConnectedCallbackFunction: () -> Unit

    /**
     * Handler of incoming messages from clients.
     */
    inner class IncomingHandler() : Handler() {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SessionHandlerMessageTypes.SERVICE_ACCESS_INFORMATION_MESSAGE -> handleServiceAccessInformationMessage(
                    msg
                )
                SessionHandlerMessageTypes.SESSION_HANDLER_TRIGGERS_PLAYBACK -> handleSessionHandlerTriggersPlaybackMessage(
                    msg
                )
                else -> super.handleMessage(msg)
            }
        }

        private fun handleServiceAccessInformationMessage(msg: Message) {
            currentServiceAccessInformation = msg.obj as ServiceAccessInformation
            currentServiceAccessInformation.streamingAccess?.let { startPlayback(it.mediaPlayerEntry) }
        }

        private fun handleSessionHandlerTriggersPlaybackMessage(msg: Message) {
            startPlayback(msg.obj as String)
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

    fun initialize(context: Context, epa: ExoPlayerAdapter, onConnectionToMediaSessionHandlerEstablished: () -> (Unit)) {
        exoPlayerAdapter = epa
        Intent(context, MediaSessionHandlerMessengerService::class.java).also { intent ->
            context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
        serviceConnectedCallbackFunction = onConnectionToMediaSessionHandlerEstablished
    }

    fun reset(context: Context) {
        if (bound) {
            context.unbindService(mConnection)
            bound = false
        }
    }

    fun updateLookupTable(m8Data: MutableList<ServiceAccessInformation>) {
        val msg: Message = Message.obtain(
            null,
            SessionHandlerMessageTypes.UPDATE_LOOKUP_TABLE
        )
        msg.obj = m8Data
        msg.replyTo = mMessenger;
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun setM5Endpoint(m5Url : String) {
        val msg: Message = Message.obtain(
            null,
            SessionHandlerMessageTypes.SET_M5_ENDPOINT
        )
        msg.obj = m5Url
        msg.replyTo = mMessenger;
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun updatePlaybackState(state: String) {
        if (!bound) return
        // Create and send a message to the service, using a supported 'what' value
        val msg: Message = Message.obtain(null, SessionHandlerMessageTypes.STATUS_MESSAGE)
        msg.obj = state
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
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun initializePlaybackByProvisioningSessionId(provisioningSessionId: String) {
        if (!bound) return
        // Create and send a message to the service, using a supported 'what' value
        val msg: Message = Message.obtain(
            null,
            SessionHandlerMessageTypes.START_PLAYBACK_BY_PROVISIONING_ID_MESSAGE
        )
        msg.obj = provisioningSessionId
        msg.replyTo = mMessenger;
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun initializePlaybackByMediaPlayerEntry(mediaPlayerEntry: String) {
        if (!bound) return
        // Create and send a message to the service, using a supported 'what' value
        val msg: Message = Message.obtain(
            null,
            SessionHandlerMessageTypes.START_PLAYBACK_BY_MEDIA_PLAYER_ENTRY_MESSAGE
        )
        msg.obj = mediaPlayerEntry
        msg.replyTo = mMessenger;
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

}