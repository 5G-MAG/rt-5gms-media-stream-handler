package com.fivegmag.a5gmsmediastreamhandler.controller

import android.telephony.TelephonyManager
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

interface ISessionController : IController {

    /** The Media Session Handler says whether the UE is inside the locationFilter of a CMCD metrics reporting configuration */
    fun handleCmcdLocationUpdate(configurationId: String, located: Boolean)

    /** A client metrics reporting configuration changed during the session: the CMCD configurations in force now (clause 5.3.5.1 of TS 26.510) */
    fun handleCmcdConfigurationUpdate(requests: List<QoeMetricsRequest>)

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent)

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onManifestProcessedEvent(event: ManifestProcessedEvent)
}