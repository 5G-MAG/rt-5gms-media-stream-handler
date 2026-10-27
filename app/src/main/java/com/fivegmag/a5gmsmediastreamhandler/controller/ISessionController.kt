package com.fivegmag.a5gmsmediastreamhandler.controller

import android.telephony.TelephonyManager
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

interface ISessionController : IController {

    /** The Media Session Handler says whether the UE is inside the locationFilter of a CMCD metrics reporting configuration */
    fun handleCmcdLocationUpdate(configurationId: String, located: Boolean)

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent)

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onManifestProcessedEvent(event: ManifestProcessedEvent)
}