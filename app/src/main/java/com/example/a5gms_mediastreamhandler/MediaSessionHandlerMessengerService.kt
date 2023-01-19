package com.example.a5gms_mediastreamhandler

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.widget.Toast
import com.example.a5gms_mediastreamhandler.helpers.M5Interface
import com.example.a5gms_mediastreamhandler.helpers.SessionHandlerMessageTypes
import com.example.a5gms_mediastreamhandler.network.ServiceAccessInformation
import com.example.a5gms_mediastreamhandler.network.ServiceAccessInformationApi
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


/**
 * Create a bound service when you want to interact with the service from activities and other components in your application
 * or to expose some of your application's functionality to other applications through interprocess communication (IPC).
 */
class MediaSessionHandlerMessengerService() : Service() {

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private lateinit var mMessenger: Messenger
    private lateinit var serviceAccessInformationApi: ServiceAccessInformationApi

    /** Keeps track of all current registered clients.  */
    var mClients = ArrayList<Messenger>()

    /**
     * Handler of incoming messages from clients.
     */
    inner class IncomingHandler(
        context: Context,
        private val applicationContext: Context = context.applicationContext
    ) : Handler() {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SessionHandlerMessageTypes.REGISTER_CLIENT -> registerClient(msg)
                SessionHandlerMessageTypes.UNREGISTER_CLIENT -> registerClient(msg)
                SessionHandlerMessageTypes.STATUS_MESSAGE -> handleStatusMessage(msg)
                SessionHandlerMessageTypes.START_PLAYBACK_MESSAGE -> handleStartPlaybackMessage(msg)
                else -> super.handleMessage(msg)
            }
        }

        private fun registerClient(msg: Message) {
            mClients.add(msg.replyTo)
        }

        private fun unregisterClient(msg: Message) {
            mClients.remove(msg.replyTo)
        }

        private fun handleStatusMessage(msg: Message) {
            if (msg.obj != null) {
                val state: String = msg.obj as String
                Toast.makeText(
                    applicationContext,
                    "Media Session Handler Service received state message: $state",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun handleStartPlaybackMessage(msg: Message) {
            if (msg.obj != null) {
                val provisioningSessionId: String = msg.obj as String
                val responseMessenger : Messenger = msg.replyTo
                val call: Call<ServiceAccessInformation>? =
                    serviceAccessInformationApi.fetchServiceAccessInformation(provisioningSessionId)
                call?.enqueue(object : retrofit2.Callback<ServiceAccessInformation?> {
                    override fun onResponse(
                        call: Call<ServiceAccessInformation?>,
                        response: Response<ServiceAccessInformation?>
                    ) {
                        val resource : ServiceAccessInformation? = response.body()
                        val msgResponse: Message = Message.obtain(null, SessionHandlerMessageTypes.SERVICE_ACCESS_INFORMATION_MESSAGE)
                        msgResponse.obj = resource
                        responseMessenger.send(msgResponse)
                    }

                    override fun onFailure(call: Call<ServiceAccessInformation?>, t: Throwable) {
                        call.cancel()
                    }
                })
            }
        }

        private fun triggerEvent() {

        }

    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service. To create a bound service, you must define the interface that specifies
     * how a client can communicate with the service. This interface between the service and a client must be an implementation of
     * IBinder and is what your service must return from the onBind() callback method.
     */
    override fun onBind(intent: Intent): IBinder? {
        initializeRetrofitForServiceAccessInformation()
        return initializeMessenger()
    }

    private fun initializeMessenger(): IBinder? {
        mMessenger = Messenger(IncomingHandler(this))
        return mMessenger.binder
    }

    private fun initializeRetrofitForServiceAccessInformation() {
        val retrofitServiceAccessInformation: Retrofit = Retrofit.Builder()
            .baseUrl(M5Interface.ENDPOINT)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        serviceAccessInformationApi =
            retrofitServiceAccessInformation.create(ServiceAccessInformationApi::class.java)
    }

}