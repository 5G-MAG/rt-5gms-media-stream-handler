package com.fivegmag.a5gmsmediastreamhandler.controller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStartTriggerEvent
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

    companion object {
        // TS 26.512 V19.3.0 clause 4.7.2.1: one of the Media Entry Points is selected by its MIME media type; DASH is preferred, HLS is the
        // other type that the player handles. The Media Session Handler has already left out the entry points of unsupported profiles.
        fun selectEntryPoint(entryPoints: List<EntryPoint>): EntryPoint? =
            entryPoints.firstOrNull { it.contentType == ContentTypes.DASH }
                ?: entryPoints.firstOrNull { it.contentType == ContentTypes.HLS }
    }

    private val cellInfoCallback = object : TelephonyManager.CellInfoCallback() {
        override fun onCellInfo(cellInfoList: MutableList<CellInfo>) {
            exoPlayerAdapter.eventBus.post(CellInfoUpdatedEvent(cellInfoList))
        }
    }

    override fun initialize() {
        exoPlayerAdapter.eventBus.register(this)
        requestCellInfoUpdates()
    }

    @UnstableApi
    override fun handleTriggerPlayback(playbackRequest: PlaybackRequest) {
        // Fire PlaybackStartTriggerEvent for Playout Delay for Media Start-up metric (TS 26.247 clause 10.2.9)
        // This marks the instant when the DASH player receives the playback-start trigger
        exoPlayerAdapter.eventBus.post(PlaybackStartTriggerEvent(android.os.SystemClock.elapsedRealtime()))

        // CMCD is reported only for the classes that the provisioned metrics schemes name
        exoPlayerAdapter.configureCmcd(
            playbackRequest.qoeMetricsRequests.mapNotNull { it.scheme },
            playbackRequest.mediaDeliverySessionIdentifier,
            playbackRequest.contentIdentifier
        )

        val entryPoint = selectEntryPoint(playbackRequest.entryPoints)
        if (entryPoint != null) {
            exoPlayerAdapter.attach(entryPoint.locator, entryPoint.contentType!!)
            exoPlayerAdapter.preload()
            exoPlayerAdapter.play()
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    override fun onManifestProcessedEvent(event: ManifestProcessedEvent) {
        outgoingMessageHandler.notifyManifest(event)
    }

    override fun resetState() {

    }

    override fun reset() {
        resetState()
    }

}