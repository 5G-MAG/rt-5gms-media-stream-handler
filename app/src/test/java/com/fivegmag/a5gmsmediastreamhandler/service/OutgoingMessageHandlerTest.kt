package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class OutgoingMessageHandlerTest {
    private val messages = mutableListOf<Message>()
    private val outgoing = OutgoingMessageHandler()

    @Before
    fun setUp() {
        val receiver = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                messages.add(Message.obtain(msg))
            }
        })
        outgoing.handleServiceConnected(receiver.binder, Messenger(Handler(Looper.getMainLooper())))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SessionHandlerMessageTypes.REGISTER_CLIENT, messages.single().what)
        messages.clear()
    }

    @Test
    fun playbackRequestTransmitsApplicationChoiceOfSessionLifetime() {
        val entry = ServiceListEntry("service", "Service", arrayListOf())
        outgoing.initializePlaybackByServiceListEntry(entry, newMediaDeliverySession = true)
        outgoing.initializePlaybackByServiceListEntry(entry, newMediaDeliverySession = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, messages.size)
        assertTrue(messages.all { it.what == SessionHandlerMessageTypes.START_PLAYBACK_BY_SERVICE_LIST_ENTRY_MESSAGE })
        assertTrue(messages[0].data.getBoolean("newMediaDeliverySession"))
        assertFalse(messages[1].data.getBoolean("newMediaDeliverySession"))
        assertNotEquals(messages[0].data.getString("playbackId"), messages[1].data.getString("playbackId"))
        assertEquals(outgoing.currentPlaybackId, messages[1].data.getString("playbackId"))
    }

    @Test
    fun oldPlaybackStatusIsNotRelabelledWithTheCurrentPlaybackId() {
        val entry = ServiceListEntry("service", "Service", arrayListOf())
        outgoing.initializePlaybackByServiceListEntry(entry, newMediaDeliverySession = true)
        val oldId = requireNotNull(outgoing.currentPlaybackId)
        outgoing.initializePlaybackByServiceListEntry(entry, newMediaDeliverySession = false)
        val currentId = requireNotNull(outgoing.currentPlaybackId)
        shadowOf(Looper.getMainLooper()).idle()
        messages.clear()

        outgoing.updatePlaybackState(PlayerStates.ENDED, oldId)
        outgoing.updatePlaybackState(PlayerStates.READY, currentId)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(SessionHandlerMessageTypes.STATUS_MESSAGE, messages.single().what)
        assertEquals(currentId, messages.single().data.getString("playbackId"))
        assertEquals(PlayerStates.READY, messages.single().data.getString("playbackState"))
    }

    @Test
    fun explicitEndAndDisconnectTransmitSeparateLifecycleMessages() {
        outgoing.endMediaDeliverySession()
        outgoing.reset()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            listOf(SessionHandlerMessageTypes.END_MEDIA_DELIVERY_SESSION, SessionHandlerMessageTypes.UNREGISTER_CLIENT),
            messages.map { it.what }
        )
    }
}
