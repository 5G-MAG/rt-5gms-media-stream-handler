package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import java.util.UUID

class OutgoingMessageHandler {

    private var boundToMediaSessionHandler = false
    private var nativeIncomingHandler: Messenger? = null
    private var outgoingMessengerService: Messenger? = null
    var currentPlaybackId: String? = null
        private set

    companion object {
        const val TAG = "5GMS-OutgoingMessageHandler"
    }

    fun handleServiceConnected(service: IBinder, mHandler: Messenger) {
        nativeIncomingHandler = mHandler
        outgoingMessengerService = Messenger(service)
        try {
            Log.i(TAG, "Connected to Media Session Handler")
            boundToMediaSessionHandler = true
            registerClient()
        } catch (_: RemoteException) {
        }

    }

    fun handleServiceDisconnected() {
        currentPlaybackId = null
        outgoingMessengerService = null
        boundToMediaSessionHandler = false
    }

    private fun registerClient() {
        if (!canSendMessage()) {
            return
        }
        val msg = getMessage(SessionHandlerMessageTypes.REGISTER_CLIENT)
        sendMessage(msg)
    }

    fun setM5Endpoint(m5BaseUrl: String) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putString("m5BaseUrl", m5BaseUrl)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.SET_M5_ENDPOINT, bundle)
    }

    fun initializePlaybackByServiceListEntry(
        serviceListEntry: ServiceListEntry,
        newMediaDeliverySession: Boolean
    ) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        val playbackId = UUID.randomUUID().toString()
        currentPlaybackId = playbackId
        bundle.putParcelable("serviceListEntry", serviceListEntry)
        bundle.putString("playbackId", playbackId)
        bundle.putBoolean("newMediaDeliverySession", newMediaDeliverySession)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.START_PLAYBACK_BY_SERVICE_LIST_ENTRY_MESSAGE, bundle)
    }

    fun endMediaDeliverySession() {
        currentPlaybackId = null
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.END_MEDIA_DELIVERY_SESSION, Bundle())
    }

    fun updatePlaybackState(state: String, playbackId: String) {
        if (!canSendMessage()) {
            return
        }
        if (playbackId != currentPlaybackId) {
            Log.d(TAG, "Ignoring playback state from an outdated playback")
            return
        }
        val bundle = Bundle()
        bundle.putString("playbackState", state)
        bundle.putString("playbackId", playbackId)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.STATUS_MESSAGE, bundle)
    }

    fun sendMessageByTypeAndBundle(messageType: Int, bundle: Bundle) {
        if (!canSendMessage()) {
            return
        }
        val msg = getMessage(messageType)
        msg.data = bundle
        sendMessage(msg)
    }

    private fun canSendMessage(): Boolean {
        return boundToMediaSessionHandler
    }

    private fun getMessage(messageType: Int): Message {
        val msg: Message = Message.obtain(
            null,
            messageType
        )
        msg.replyTo = nativeIncomingHandler

        return msg
    }

    private fun sendMessage(msg: Message) {
        try {
            outgoingMessengerService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun reset() {
        if (canSendMessage()) {
            sendMessage(getMessage(SessionHandlerMessageTypes.UNREGISTER_CLIENT))
        }
        handleServiceDisconnected()
    }
}