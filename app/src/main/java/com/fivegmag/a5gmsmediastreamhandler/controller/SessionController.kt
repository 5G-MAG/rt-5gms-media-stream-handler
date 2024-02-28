package com.fivegmag.a5gmsmediastreamhandler.controller

import android.os.Message
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmscommonlibrary.models.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SessionController(
    private val exoPlayerAdapter: ExoPlayerAdapter,
    private val outgoingMessageHandler: OutgoingMessageHandler
) {

    fun initialize() {
        EventBus.getDefault().register(this)
    }

    @UnstableApi
    fun triggerPlayback(playbackRequest: PlaybackRequest) {
        if (playbackRequest.entryPoints.size > 0) {
            val dashEntryPoints: List<EntryPoint> =
                playbackRequest.entryPoints.filter { entryPoint -> entryPoint.contentType == ContentTypes.DASH }

            if (dashEntryPoints.isNotEmpty()) {
                val mpdUrl = dashEntryPoints[0].locator
                exoPlayerAdapter.attach(mpdUrl, ContentTypes.DASH)
                exoPlayerAdapter.preload()
                exoPlayerAdapter.play()
            }
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent) {
        outgoingMessageHandler.updatePlaybackState(event.playbackState)
    }

    fun reset() {
    }

}