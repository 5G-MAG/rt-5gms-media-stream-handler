package com.fivegmag.a5gmsmediastreamhandler.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry

class MessengerService(
    private val context: Context
) {

    companion object {
        const val TAG = "5GMS-MessengerService"
    }

    private var boundToMediaSessionHandler = false
    private lateinit var serviceConnectedCallbackFunction: () -> Unit
    private lateinit var incomingMessageHandler: IncomingMessageHandler
    private lateinit var outgoingMessageHandler: OutgoingMessageHandler
    private val messengerConnection = object : ServiceConnection {

        @UnstableApi
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            boundToMediaSessionHandler = true
            val nativeIncomingMessenger = incomingMessageHandler.getIncomingMessenger()
            outgoingMessageHandler.handleServiceConnected(service, nativeIncomingMessenger)
            serviceConnectedCallbackFunction()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            boundToMediaSessionHandler = false
            outgoingMessageHandler.handleServiceDisconnected()
        }
    }

    fun initialize(
        incomingMessageHandler: IncomingMessageHandler,
        outgoingMessageHandler: OutgoingMessageHandler
    ) {
        this.incomingMessageHandler = incomingMessageHandler
        this.outgoingMessageHandler = outgoingMessageHandler
    }


    fun bind(onConnectionToMediaSessionHandlerEstablished: () -> (Unit)) {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.fivegmag.a5gmsmediasessionhandler",
                "com.fivegmag.a5gmsmediasessionhandler.service.MediaSessionHandlerMessengerService"
            )
            if (context.bindService(intent, messengerConnection, Context.BIND_AUTO_CREATE)) {
                Log.i(
                    TAG,
                    "Binding to MediaSessionHandler service returned true"
                )
            } else {
                Log.i(
                    TAG,
                    "Binding to MediaSessionHandler service returned false"
                )
            }
            serviceConnectedCallbackFunction = onConnectionToMediaSessionHandlerEstablished
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "Can't bind to MediaSessionHandler, check permission in Manifest"
            )
        }
    }

    fun setM5Endpoint(m5BaseUrl: String) {
        outgoingMessageHandler.setM5Endpoint(m5BaseUrl)
    }

    fun initializePlaybackByServiceListEntry(serviceListEntry: ServiceListEntry) {
        outgoingMessageHandler.initializePlaybackByServiceListEntry(serviceListEntry)
    }

    @UnstableApi
    fun reset() {
        if (boundToMediaSessionHandler) {
            context.unbindService(messengerConnection)
            boundToMediaSessionHandler = false
        }
        outgoingMessageHandler.reset()
        incomingMessageHandler.reset()
    }


}