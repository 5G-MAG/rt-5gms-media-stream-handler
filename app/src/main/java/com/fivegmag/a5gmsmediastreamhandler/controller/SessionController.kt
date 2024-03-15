package com.fivegmag.a5gmsmediastreamhandler.controller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SessionController(
    private val context: Context,
    private val exoPlayerAdapter: IExoPlayerAdapter,
    private val outgoingMessageHandler: OutgoingMessageHandler
) : ISessionController {

    private val cellInfoCallback = object : TelephonyManager.CellInfoCallback() {
        override fun onCellInfo(cellInfoList: MutableList<CellInfo>) {
            EventBus.getDefault().post(CellInfoUpdatedEvent(cellInfoList))
        }
    }

    override fun initialize() {
        EventBus.getDefault().register(this)
        requestCellInfoUpdates()
    }

    @UnstableApi
    override fun handleTriggerPlayback(playbackRequest: PlaybackRequest) {
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

    private fun requestCellInfoUpdates() {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Register the cell info callback
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            telephonyManager.requestCellInfoUpdate(context.mainExecutor, cellInfoCallback)
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    override fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent) {
        outgoingMessageHandler.updatePlaybackState(event.playbackState)
    }

    override fun resetState() {

    }

    override fun reset() {
        resetState()
    }

}