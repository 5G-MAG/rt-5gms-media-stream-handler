package com.fivegmag.a5gmsmediastreamhandler

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.media3.common.util.UnstableApi
import org.robolectric.RuntimeEnvironment
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmsmediastreamhandler.controller.SessionController
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import io.mockk.every
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Config

/** TS 26.510 V19.2.0 clause 5.3.6.1: the location change is detected from the cell information, on Android 10 and 11 by the PhoneStateListener */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@UnstableApi
class CellInfoListenerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val bus = EventBus.builder().build()
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true).also { every { it.eventBus } returns bus }
    private val collector = CellInfoCollector()
    private val received = collector.events
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    init {
        bus.register(collector)
    }

    private fun controller(sdkInt: Int, permission: Boolean = true): SessionController {
        if (permission) shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        return SessionController(context, adapter, mockk<OutgoingMessageHandler>(relaxed = true)).also { it.sdkInt = sdkInt }
    }

    @Test
    fun belowAndroid12ThePhoneStateListenerGivesTheChanges() {
        controller(Build.VERSION_CODES.R).initialize()
        val listener = shadowOf(telephony).listener
        assertNotNull(listener)
        assertEquals(PhoneStateListener::class.java, listener.javaClass.superclass)
        assertNull(shadowOf(telephony).lastTelephonyCallback)
        val before = received.size
        listener.onCellInfoChanged(mutableListOf<CellInfo>())
        assertEquals(before + 1, received.size)
    }

    @Test
    fun fromAndroid12TheTelephonyCallbackGivesTheChanges() {
        controller(Build.VERSION_CODES.S).initialize()
        val callback = shadowOf(telephony).lastTelephonyCallback as TelephonyCallback.CellInfoListener
        val before = received.size
        callback.onCellInfoChanged(mutableListOf<CellInfo>())
        assertEquals(before + 1, received.size)
    }

    @Test
    fun theCellInformationIsAskedForAgainWhileTheSessionRunsAndNotAfterTheReset() {
        shadowOf(telephony).setCallbackCellInfos(mutableListOf<CellInfo>())   // the answer to each request of the platform
        val controller = controller(Build.VERSION_CODES.S)
        controller.initialize()
        ShadowLooper.idleMainLooper()
        val asked = received.size
        ShadowLooper.idleMainLooper(9, TimeUnit.SECONDS)
        assertEquals(asked, received.size)
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertEquals(asked + 1, received.size)
        ShadowLooper.idleMainLooper(10, TimeUnit.SECONDS)
        assertEquals(asked + 2, received.size)
        controller.reset()
        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS)
        assertEquals(asked + 2, received.size)
    }

    @Test
    fun withoutThePermissionNothingIsAskedFor() {
        shadowOf(telephony).setCallbackCellInfos(mutableListOf<CellInfo>())
        controller(Build.VERSION_CODES.S, permission = false).also { it.initialize() }
        val asked = received.size
        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS)
        assertEquals(asked, received.size)
    }

    @Test
    fun withoutThePermissionNothingIsRegistered() {
        controller(Build.VERSION_CODES.R, permission = false).initialize()
        assertNull(shadowOf(telephony).listener)
        assertNull(shadowOf(telephony).lastTelephonyCallback)
    }
}

// EventBus needs a public subscriber
class CellInfoCollector {
    val events = ArrayList<CellInfoUpdatedEvent>()

    @Subscribe
    fun on(event: CellInfoUpdatedEvent) {
        events.add(event)
    }
}
