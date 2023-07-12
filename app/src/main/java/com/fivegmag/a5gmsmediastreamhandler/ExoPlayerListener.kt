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
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData

import com.fivegmag.a5gmsmediastreamhandler.helpers.mapStateToConstant
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpListEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpListEntryType
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitch
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitchList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.Trace

// See https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener
const val TAG = "ExoPlayerListener"

@UnstableApi
class ExoPlayerListener(
    private val mediaSessionHandlerAdapter: MediaSessionHandlerAdapter,
    private val playerInstance: ExoPlayer,
    private val playerView: PlayerView
) :
    AnalyticsListener {

    private val representationSwitchList: RepresentationSwitchList = RepresentationSwitchList(
        ArrayList<RepresentationSwitch>()
    )
    private val httpList: HttpList = HttpList(ArrayList<HttpListEntry>())
    private val bufferLevel: BufferLevel = BufferLevel(ArrayList<BufferLevelEntry>())
    private val utils: Utils = Utils()

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        playbackState: Int
    ) {
        val state: String = mapStateToConstant(playbackState)

        if (state == PlayerStates.BUFFERING) {
            addBufferLevelEntry()
        }

        if (state == PlayerStates.ENDED) {
            mediaSessionHandlerAdapter.sendFinalPlaybackMetricsMessage()
        }

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

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        Log.d(TAG, "Error")
    }

    override fun onDownstreamFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        mediaLoadData: MediaLoadData
    ) {
        val t: String = utils.getCurrentXsDateTime()
        val mt: String? = utils.millisecondsToISO8601(playerInstance.currentPosition)
        val to: String? = mediaLoadData.trackFormat?.id
        val representationSwitch = to?.let { RepresentationSwitch(t, mt, it) }
        if (representationSwitch != null) {
            representationSwitchList.entries.add(representationSwitch)
        }
    }

    private fun addBufferLevelEntry() {
        val level: Int = playerInstance.totalBufferedDuration.toInt()
        val time: String = utils.getCurrentXsDateTime()
        val entry = BufferLevelEntry(time, level)
        bufferLevel.entries.add(entry)
    }

    fun getRepresentationSwitchList(): RepresentationSwitchList {
        return representationSwitchList
    }

    fun getHttpList(): HttpList {
        return httpList
    }

    fun getBufferLevel(): BufferLevel {
        return bufferLevel
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        addHttpListEntry(mediaLoadData, loadEventInfo)
        addBufferLevelEntry()
    }

    private fun addHttpListEntry(mediaLoadData: MediaLoadData, loadEventInfo: LoadEventInfo) {
        val tcpId = null
        val type = getRequestType(mediaLoadData)
        val url = loadEventInfo.uri.toString()
        val actualUrl = loadEventInfo.uri.toString()
        val range = ""
        val tRequest =
            utils.convertTimestampToXsDateTime(utils.getCurrentTimestamp() - loadEventInfo.loadDurationMs)
        val tResponse =
            utils.convertTimestampToXsDateTime(utils.getCurrentTimestamp() - loadEventInfo.loadDurationMs)
        val responseCode = 200
        val interval = loadEventInfo.loadDurationMs.toInt()
        val bytes = loadEventInfo.bytesLoaded.toInt()
        val trace = Trace(
            tResponse,
            loadEventInfo.loadDurationMs,
            bytes
        )
        val traceList = ArrayList<Trace>()
        traceList.add(trace)
        val httpListEntry = HttpListEntry(
            tcpId,
            type,
            url,
            actualUrl,
            range,
            tRequest,
            tResponse,
            responseCode,
            interval,
            traceList
        )

        httpList.entries.add(httpListEntry)
    }

    private fun getRequestType(mediaLoadData: MediaLoadData): String {
        return when (mediaLoadData.dataType) {
            androidx.media3.common.C.DATA_TYPE_UNKNOWN -> HttpListEntryType.OTHER.value
            androidx.media3.common.C.DATA_TYPE_MEDIA -> HttpListEntryType.MEDIA_SEGMENT.value
            androidx.media3.common.C.DATA_TYPE_MEDIA_INITIALIZATION -> HttpListEntryType.INITIALIZATION_SEGMENT.value
            androidx.media3.common.C.DATA_TYPE_MANIFEST -> HttpListEntryType.MPD.value
            else -> HttpListEntryType.OTHER.value

        }
    }

    fun reset() {
        Log.d(TAG, "Resetting ExoPlayerListener")
        representationSwitchList.entries.clear()
        httpList.entries.clear()
        bufferLevel.entries.clear()
    }

}