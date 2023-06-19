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
import androidx.media3.exoplayer.source.MediaLoadData

import com.fivegmag.a5gmsmediastreamhandler.helpers.mapStateToConstant
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitch
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitchList

// See https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.Listener.html for possible events
@UnstableApi
class ExoPlayerListener(
    private val mediaSessionHandlerAdapter: MediaSessionHandlerAdapter,
    private val playerInstance: ExoPlayer,
    private val playerView: PlayerView,
    private val representationSwitchList: RepresentationSwitchList = RepresentationSwitchList(
        ArrayList<RepresentationSwitch>()
    )
) :
    AnalyticsListener {

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

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        Log.d("ExoPlayer", "Error")
    }

    override fun onDownstreamFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        mediaLoadData: MediaLoadData
    ) {
        val t: Long = utils.getCurrentTimestamp()
        val mt: Long = playerInstance.contentPosition
        val to: String? = mediaLoadData.trackFormat?.id
        val representationSwitch = to?.let { RepresentationSwitch(t, mt, it) }
        if (representationSwitch != null) {
            representationSwitchList.entries.add(representationSwitch)
        }
    }

    fun getRepresentationSwitchList(): RepresentationSwitchList {
        return representationSwitchList
    }

    fun reset() {
        representationSwitchList.entries.clear()
    }

}