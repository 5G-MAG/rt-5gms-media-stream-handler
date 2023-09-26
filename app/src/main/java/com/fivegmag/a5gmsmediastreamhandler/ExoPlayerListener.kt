/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2023 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsmediastreamhandler

import android.util.Log
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
import com.fivegmag.a5gmsmediastreamhandler.helpers.mapStateToConstant
import org.greenrobot.eventbus.EventBus
import java.util.Date

// See https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.Listener.html for possible events
@UnstableApi
class ExoPlayerListener(
    private val mediaSessionHandlerAdapter: MediaSessionHandlerAdapter,
    private val playerInstance: ExoPlayer,
    private val playerView: PlayerView
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

    private fun addConsumptionReportingUnit(mediaLoadData: MediaLoadData) {
        val startTime = utils.formatDateToOpenAPIFormat(Date())
        val mediaConsumed = mediaLoadData.trackFormat?.id
        val duration = 0

        // If we have a previous entry in the list of consumption reporting units then we can calculate the duration now
        val lastEntry = consumptionReportingUnitList.lastOrNull();
        if (lastEntry != null) {
            lastEntry.duration =
                utils.calculateTimestampDifferenceInSeconds(lastEntry.startTime, startTime).toInt()
        }

        // Add the new entry
        val consumptionReportingUnit =
            mediaConsumed?.let { ConsumptionReportingUnit(it, null, startTime, duration, null) }
        if (consumptionReportingUnit != null) {
            consumptionReportingUnitList.add(consumptionReportingUnit)
        }
    }

    fun getConsumptionReportingUnitList(): ArrayList<ConsumptionReportingUnit> {
        return consumptionReportingUnitList
    }

    fun resetConsumptionReport() {
        consumptionReportingUnitList.clear()
    }

}