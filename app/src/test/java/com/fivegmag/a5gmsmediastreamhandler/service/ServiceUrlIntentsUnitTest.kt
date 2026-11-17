package com.fivegmag.a5gmsmediastreamhandler.service

import android.content.Intent
import android.net.Uri
import android.os.Messenger
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** TS 26.512 V19.3.0 clauses 4.8.3.1 and 4.8.3.4: the request of a 3GPP Service URL by the URL handler, and the HTTP status in its result */
class ServiceUrlIntentsUnitTest {
    private val uri = mockk<Uri>()

    @Before
    fun mockPlatform() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns uri
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<android.os.Parcelable>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().setData(any()) } returns mockk(relaxed = true)
    }

    @After
    fun unmock() = unmockkAll()

    // the intent goes to the Media Session Handler's package and carries the Messenger of the instance
    @Test
    fun theRequestGoesToTheMediaSessionHandlerWithTheMessengerOfTheInstance() {
        val messenger = mockk<Messenger>()
        ServiceUrlIntents.request("https://launch.3gppservices.org/ms/svc-1", messenger)
        verify(exactly = 1) { Uri.parse("https://launch.3gppservices.org/ms/svc-1") }
        verify(exactly = 1) { anyConstructed<Intent>().setPackage("com.fivegmag.a5gmsmediasessionhandler") }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(SessionHandlerBundleKeys.LAUNCH_CLIENT_MESSENGER, messenger as android.os.Parcelable) }
    }

    @Test
    fun anInvokerWithNoMessengerPutsNoMessengerInTheRequest() {
        ServiceUrlIntents.request("https://launch.3gppservices.org/ms/svc-1", null)
        verify(exactly = 0) { anyConstructed<Intent>().putExtra(SessionHandlerBundleKeys.LAUNCH_CLIENT_MESSENGER, any<android.os.Parcelable>()) }
    }

    @Test
    fun theStatusIsTheExtraOfTheResult() {
        val result = mockk<Intent>()
        every { result.hasExtra(SessionHandlerBundleKeys.HTTP_RESPONSE_CODE) } returns true
        every { result.getIntExtra(SessionHandlerBundleKeys.HTTP_RESPONSE_CODE, 0) } returns 404
        assertEquals(404, ServiceUrlIntents.statusOf(result))
    }

    @Test
    fun noStatusWhenTheMediaSessionHandlerGaveNone() {
        val result = mockk<Intent>()
        every { result.hasExtra(SessionHandlerBundleKeys.HTTP_RESPONSE_CODE) } returns false
        assertNull(ServiceUrlIntents.statusOf(result))
        assertNull(ServiceUrlIntents.statusOf(null))
    }
}
