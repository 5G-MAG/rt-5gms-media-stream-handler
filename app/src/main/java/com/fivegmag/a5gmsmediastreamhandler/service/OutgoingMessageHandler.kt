package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry

class OutgoingMessageHandler {

    private var boundToMediaSessionHandler = false
    private var nativeIncomingHandler: Messenger? = null
    private var outgoingMessengerService: Messenger? = null

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

    /** createMediaDeliverySession() of TS 26.510 V19.2.0 table 11.2.2.1-1; the answer arrives at the GeneralSessionListener */
    fun createMediaDeliverySession(
        serviceId: String,
        contentId: String?,
        entryPoint: String?,
        domainNames: List<String>?,
        accessToken: String?
    ) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putString(SessionHandlerBundleKeys.SERVICE_ID, serviceId)
        contentId?.let { bundle.putString(SessionHandlerBundleKeys.CONTENT_ID, it) }
        entryPoint?.let { bundle.putString(SessionHandlerBundleKeys.ENTRY_POINT, it) }
        domainNames?.let { bundle.putStringArrayList(SessionHandlerBundleKeys.DOMAIN_NAMES, ArrayList(it)) }
        accessToken?.let { bundle.putString(SessionHandlerBundleKeys.ACCESS_TOKEN, it) }
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.CREATE_MEDIA_DELIVERY_SESSION, bundle)
    }

    /** destroyMediaDeliverySession() of table 11.2.2.2-1 */
    fun destroyMediaDeliverySession(mediaDeliverySessionIdentifier: String) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER, mediaDeliverySessionIdentifier)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.DESTROY_MEDIA_DELIVERY_SESSION, bundle)
    }

    /** The general status of table 11.2.3-1; the answer arrives at the GeneralSessionListener */
    fun getSessionStatus(mediaDeliverySessionIdentifier: String) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER, mediaDeliverySessionIdentifier)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.GET_SESSION_STATUS, bundle)
    }

    /** The status of Consumption Reporting (table 11.5.2-1); the answer arrives at the GeneralSessionListener */
    fun getConsumptionReportingStatus(mediaDeliverySessionIdentifier: String) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER, mediaDeliverySessionIdentifier)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.GET_CONSUMPTION_REPORTING_STATUS, bundle)
    }

    /** The status of Metrics Reporting (table 11.6.2-1); the answer arrives at the GeneralSessionListener */
    fun getMetricsReportingStatus(mediaDeliverySessionIdentifier: String) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER, mediaDeliverySessionIdentifier)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.GET_METRICS_REPORTING_STATUS, bundle)
    }

    fun initializePlaybackByServiceListEntry(serviceListEntry: ServiceListEntry) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putParcelable("serviceListEntry", serviceListEntry)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.START_PLAYBACK_BY_SERVICE_LIST_ENTRY_MESSAGE, bundle)
    }

    fun updatePlaybackState(state: String) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putString("playbackState", state)
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
        handleServiceDisconnected()
    }
}