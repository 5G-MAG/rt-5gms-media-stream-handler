package com.example.a5gms_mediastreamhandler

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.widget.Toast
import com.example.a5gms_mediastreamhandler.MediaSessionHandlerMessengerService.IncomingHandler
import com.example.a5gms_mediastreamhandler.helpers.SessionHandlerMessageTypes
import com.example.a5gms_mediastreamhandler.network.ServiceAccessInformation
import retrofit2.Call
import retrofit2.Response


class MediaSessionHandlerAdapter ()  {

    /** Messenger for communicating with the service.  */
    private var mService: Messenger? = null


    /**
     * Handler of incoming messages from clients.
     */
    inner class IncomingHandler(
        context: Context,
        private val applicationContext: Context = context.applicationContext
    ) : Handler() {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SessionHandlerMessageTypes.SERVICE_ACCESS_INFORMATION_MESSAGE -> handleServiceAccessInformationMessage(msg)
                else -> super.handleMessage(msg)
            }
        }

        fun handleServiceAccessInformationMessage() {

        }


    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private val mMessenger: Messenger = Messenger(IncomingHandler())

    /** Flag indicating whether we have called bind on the service.  */
    private var bound: Boolean = false

    private lateinit var exoPlayerAdapter : ExoPlayerAdapter

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
            msg.replyTo = mMessenger;
            mService.send(msg);
            bound = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null
            bound = false
        }
    }

    fun initialize(context: Context, epa: ExoPlayerAdapter) {
        exoPlayerAdapter = epa
        Intent(context, MediaSessionHandlerMessengerService::class.java).also { intent ->
            context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun reset(context: Context) {
        if (bound) {
            context.unbindService(mConnection)
            bound = false
        }
    }

    fun updatePlaybackState(state : String) {
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

    fun initializePlaybackByProvisioningSessionId(provisioningSessionId: String) {
        if (!bound) return
        // Create and send a message to the service, using a supported 'what' value
        val msg: Message = Message.obtain(null, SessionHandlerMessageTypes.START_PLAYBACK_MESSAGE)
        msg.obj = provisioningSessionId
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }


}