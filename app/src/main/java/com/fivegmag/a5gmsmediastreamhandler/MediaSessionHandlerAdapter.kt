/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2023 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsmediastreamhandler

import android.content.Context
import android.os.*
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.models.*
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.MessengerService
import java.util.*

@UnstableApi
class MediaSessionHandlerAdapter() {
    companion object {
        const val TAG = "5GMS-MediaSessionHandlerAdapter"
    }

    private lateinit var context: Context
    private lateinit var messengerService: MessengerService
    private val exoPlayerAdapter = ExoPlayerAdapter()

    /**
     * API endpoint to initialize Media Session handling. Connects to the Media Session Handler and calls the provided callback function afterwards
     *
     * @param ctxt
     * @param epa
     * @param onConnectionToMediaSessionHandlerEstablished
     */

    fun initialize(
        ctxt: Context,
        onConnectionToMediaSessionHandlerEstablished: () -> (Unit)
    ) {
        context = ctxt
        messengerService = MessengerService(context)
        messengerService.initialize(exoPlayerAdapter)
        messengerService.bind(onConnectionToMediaSessionHandlerEstablished)
    }


    /**
     * API endpoint to close the connection to the MediaSessionHandler
     *
     * @param context
     */
    fun reset() {
        messengerService.reset()
    }

    /**
     * API endpoint for application to set the M5 endpoint in the MediaSessionHandler.
     * An IPC call is send by this function to the MediaSessionHandler to update the M5 URL.
     *
     * @param m5BaseUrl
     */
    fun setM5Endpoint(m5BaseUrl: String) {
        messengerService.setM5Endpoint(m5BaseUrl)
    }

    /**
     * API endpoint to send a ServiceListEntry to the MediaSessionHandler to trigger the playback process.
     *
     * @param serviceListEntry
     */
    fun initializePlaybackByServiceListEntry(serviceListEntry: ServiceListEntry) {
        messengerService.initializePlaybackByServiceListEntry(serviceListEntry)
    }

    /**
     * API endpoint to get the instance of ExoPlayerAdapter
     *
     */
    fun getExoPlayerAdapter(): ExoPlayerAdapter {
        return exoPlayerAdapter
    }
}