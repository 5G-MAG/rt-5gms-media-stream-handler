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
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReportingUnit
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.models.CellIdentifierType
import com.fivegmag.a5gmscommonlibrary.models.EndpointAddress
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation
import com.fivegmag.a5gmsmediastreamhandler.helpers.mapStateToConstant
import org.greenrobot.eventbus.EventBus
import java.util.Date


const val TAG = "ExoPlayerListener"

// See https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.Listener.html for possible events
@UnstableApi
class ExoPlayerListener(
    private val mediaSessionHandlerAdapter: MediaSessionHandlerAdapter,
    private val playerInstance: ExoPlayer,
    private val playerView: PlayerView,
    private val context: Context
) :
    AnalyticsListener {

    private val consumptionReportingUnitList: ArrayList<ConsumptionReportingUnit> = ArrayList()
    private val utils: Utils = Utils()

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        playbackState: Int
    ) {
        val state: String = mapStateToConstant(playbackState)

        playerView.keepScreenOn = !(state == PlayerStates.IDLE || state == PlayerStates.ENDED)

        if (state == PlayerStates.ENDED) {
            mediaSessionHandlerAdapter.sendConsumptionReport()
        }

        mediaSessionHandlerAdapter.updatePlaybackState(state)
    }

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        var state: String? = null
        if (isPlaying) {
            state = PlayerStates.PLAYING
        } else if (playerInstance.playbackState == Player.STATE_READY && !playerInstance.playWhenReady) {
            state = PlayerStates.PAUSED
        }
        if (state != null) {
            mediaSessionHandlerAdapter.updatePlaybackState(state)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onDownstreamFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        mediaLoadData: MediaLoadData
    ) {
        addConsumptionReportingUnit(mediaLoadData)
        EventBus.getDefault().post(DownstreamFormatChangedEvent(mediaLoadData))
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        Log.d("ExoPlayer", "Error")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun addConsumptionReportingUnit(mediaLoadData: MediaLoadData) {
        val startTime = utils.formatDateToOpenAPIFormat(Date())
        val mediaConsumed = mediaLoadData.trackFormat?.id
        val mimeType = mediaLoadData.trackFormat!!.containerMimeType
        val duration = 0

        // If we have a previous entry in the list of consumption reporting units with the same media type then we can calculate the duration now
        val existingEntry = consumptionReportingUnitList.find { it.mimeType == mimeType }
        if (existingEntry != null) {
            existingEntry.duration =
                utils.calculateTimestampDifferenceInSeconds(existingEntry.startTime, startTime)
                    .toInt()
            existingEntry.finished = true
        }

        // Add the new entry
        val consumptionReportingUnit =
            mediaConsumed?.let { ConsumptionReportingUnit(it, null, startTime, duration, null) }
        if (consumptionReportingUnit != null) {
            consumptionReportingUnit.mimeType = mimeType
            consumptionReportingUnit.locations = getLocations()
            consumptionReportingUnit.mediaEndpointAddress = getMediaEndpointAddress()
            consumptionReportingUnitList.add(consumptionReportingUnit)
        }
    }

    /**
     * getCellIdentity requires API level 30
     *
     * @return
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun getLocations(): ArrayList<TypedLocation> {
        val locations = ArrayList<TypedLocation>()
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            val cellInfoList: List<CellInfo> = telephonyManager.allCellInfo

            for (cellInfo in cellInfoList) {
                if (cellInfo.isRegistered) {
                    val cellIdentity = cellInfo.cellIdentity
                    val location = cellIdentity.operatorAlphaLong.toString()
                    locations.add(TypedLocation(CellIdentifierType.CGI, location))
                }
            }
        } catch (e: SecurityException) {
            return ArrayList()
        }

        return locations
    }

    private fun getMediaEndpointAddress(): EndpointAddress {
        val ipv4Address = utils.getIpAddress(4)
        val ipv6Address = utils.getIpAddress(6)
        return EndpointAddress(null, ipv4Address, ipv6Address, 80u)
    }

    fun getConsumptionReportingUnitList(): ArrayList<ConsumptionReportingUnit> {
        return consumptionReportingUnitList
    }

    /**
     * Removes all entries from the consumption reporting list
     *
     */
    private fun resetConsumptionReportingList() {
        consumptionReportingUnitList.clear()
    }

    /**
     * Removes all entries in the consumption report that are finished
     *
     */
    fun cleanConsumptionReportingList() {
        consumptionReportingUnitList.removeIf { obj -> obj.finished }
    }

    fun reset() {
        Log.d(TAG, "Resetting ExoPlayerListener")
        resetConsumptionReportingList()
    }

}