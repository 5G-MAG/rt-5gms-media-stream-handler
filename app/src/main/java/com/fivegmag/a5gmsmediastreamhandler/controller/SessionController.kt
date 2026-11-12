package com.fivegmag.a5gmsmediastreamhandler.controller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStartTriggerEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
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

        // the metrics terms of each scheme; a configuration without terms selects the whole class, so the scheme then has no restriction
        fun cmcdTermsByScheme(playbackRequest: PlaybackRequest): Map<String, List<String>> = cmcdTermsByScheme(playbackRequest.qoeMetricsRequests)

        fun cmcdTermsByScheme(requests: List<QoeMetricsRequest>): Map<String, List<String>> =
            requests.filter { it.scheme != null }.groupBy { it.scheme!! }.mapValues { (_, ofScheme) ->
                if (ofScheme.any { it.metrics.isNullOrEmpty() }) emptyList() else ofScheme.flatMap { it.metrics ?: emptyList() }
            }
    }

    // metricsConfiguration[] of the settings API (TS 26.512 V19.3.0 clause 13.2.4), CMCD schemes only: reported together with the configurations of
    // the playback request (clause 10.5.1: the union)
    @Volatile private var applicationMetricsConfigurations: List<QoeMetricsRequest> = emptyList()

    fun setApplicationMetricsConfigurations(requests: List<QoeMetricsRequest>) {
        applicationMetricsConfigurations = requests.filter { CmcdSchemes.KEYS.containsKey(it.scheme) }
    }

    private val cellInfoCallback = object : TelephonyManager.CellInfoCallback() {
        override fun onCellInfo(cellInfoList: MutableList<CellInfo>) {
            exoPlayerAdapter.eventBus.post(CellInfoUpdatedEvent(cellInfoList))
        }
    }

    private var cellInfoListener: Any? = null

    // the platform version that decides which listener is used (a test sets another one)
    internal var sdkInt: Int = Build.VERSION.SDK_INT

    override fun initialize() {
        exoPlayerAdapter.eventBus.register(this)
        requestCellInfoUpdates()
        listenToCellInfoChanges()
    }

    override fun handleCmcdLocationUpdate(configurationId: String, located: Boolean) {
        exoPlayerAdapter.setCmcdLocated(configurationId, located)
    }

    override fun handleCmcdConfigurationUpdate(requests: List<QoeMetricsRequest>) {
        // the session identifier, the content identifier and the start of the session stay: only the configurations change
        val cmcdRequests = requests + applicationMetricsConfigurations
        exoPlayerAdapter.updateCmcdConfiguration(cmcdRequests.mapNotNull { it.scheme }, cmcdTermsByScheme(cmcdRequests), cmcdRequests)
    }

    @UnstableApi
    override fun handleTriggerPlayback(playbackRequest: PlaybackRequest) {
        // the controllers that report the location are registered by now: ask again, the answer of initialize() may have come before them
        requestCellInfoUpdates()
        // Fire PlaybackStartTriggerEvent for Playout Delay for Media Start-up metric (TS 26.247 clause 10.2.9)
        // This marks the instant when the DASH player receives the playback-start trigger
        exoPlayerAdapter.eventBus.post(PlaybackStartTriggerEvent(android.os.SystemClock.elapsedRealtime()))

        // the mode of the 3GPP Service URL (table 12.4-1) and the client data reporting of the Media Player Entry (clause 10.5.1); the mode
        // is a setting of this playback, so a later playback without one does not keep it
        exoPlayerAdapter.configureCmcdMediaPlayerEntry(playbackRequest.clientDataReporting.orEmpty(), playbackRequest.clientDataReportingMode, playbackRequest.mediaPlayerEntryContext)
        // CMCD is reported only for the classes that the provisioned metrics schemes name
        val cmcdRequests = playbackRequest.qoeMetricsRequests + applicationMetricsConfigurations
        exoPlayerAdapter.configureCmcd(
            cmcdRequests.mapNotNull { it.scheme },
            playbackRequest.mediaDeliverySessionIdentifier,
            playbackRequest.contentIdentifier,
            cmcdTermsByScheme(cmcdRequests)
        )
        exoPlayerAdapter.configureCmcdReportingWindows(cmcdRequests)

        val entryPoint = selectEntryPoint(playbackRequest.entryPoints)
        if (entryPoint != null) {
            exoPlayerAdapter.attach(entryPoint.locator, entryPoint.contentType!!)
            exoPlayerAdapter.preload()
            exoPlayerAdapter.play()
        }
    }

    /**
     * The cell information changes during a media delivery session (a location change, clause 5.3.6.1 of TS 26.510): one event per change.
     * TelephonyCallback from Android 12; below it the PhoneStateListener that it replaces (Android 10 and 11, minSdk 29).
     */
    private fun listenToCellInfoChanges() {
        if (cellInfoListener != null) return
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (sdkInt < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener(context.mainExecutor) {
                @Deprecated("Deprecated in Java")
                override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                    if (cellInfo != null) exoPlayerAdapter.eventBus.post(CellInfoUpdatedEvent(cellInfo))
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CELL_INFO)
            cellInfoListener = listener
            return
        }
        val callback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                exoPlayerAdapter.eventBus.post(CellInfoUpdatedEvent(cellInfo))
            }
        }
        telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
        cellInfoListener = callback
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