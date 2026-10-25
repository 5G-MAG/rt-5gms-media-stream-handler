package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.fivegmag.a5gmsmediastreamhandler.controller.ManifestProcessedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry

class OutgoingMessageHandler {

    /** The identifier of this instance: the Media Session Handler keeps one media delivery session context per application and instance */
    private val instanceId: String = java.util.UUID.randomUUID().toString()

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

    fun notifyManifest(event: ManifestProcessedEvent) {
        if (!canSendMessage()) {
            return
        }
        val bundle = Bundle()
        bundle.putBoolean(SessionHandlerBundleKeys.MPD_DYNAMIC, event.dynamic)
        // absent properties of the MPD (a live presentation has no duration) are not put in the bundle
        event.durationMs?.let { bundle.putLong(SessionHandlerBundleKeys.MPD_DURATION_MS, it) }
        event.minBufferTimeMs?.let { bundle.putLong(SessionHandlerBundleKeys.MPD_MIN_BUFFER_TIME_MS, it) }
        bundle.putInt(SessionHandlerBundleKeys.MPD_PERIOD_COUNT, event.periodCount)
        sendMessageByTypeAndBundle(SessionHandlerMessageTypes.MANIFEST_NOTIFICATION, bundle)
    }

    fun sendMessageByTypeAndBundle(messageType: Int, bundle: Bundle) {
        if (!canSendMessage()) {
            return
        }
        val msg = getMessage(messageType)
        bundle.putString(SessionHandlerBundleKeys.CLIENT_INSTANCE_ID, instanceId)
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
        val bundle = Bundle()
        bundle.putString(SessionHandlerBundleKeys.CLIENT_INSTANCE_ID, instanceId)
        msg.data = bundle

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