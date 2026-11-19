package com.fivegmag.a5gmsmediastreamhandler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportingClientIdStoreTest {
    private var stored: String? = null
    private var made = 0
    private fun store() = ReportingClientIdStore({ stored }, { stored = it }, { "id-" + (++made) })

    @Test
    fun theIdentifierIsMadeOnceAndKept() {
        assertEquals("id-1", store().get())
        assertEquals("id-1", store().get())
        assertEquals(1, made)
    }

    @Test
    fun aKeptIdentifierIsUsedByANewInstance() {
        stored = "kept"
        assertEquals("kept", store().get())
        assertEquals(0, made)
    }

    @Test
    fun anEmptyKeptValueIsReplaced() {
        stored = ""
        assertEquals("id-1", store().get())
        assertEquals("id-1", stored)
    }

    // Clause 5.3.6.2: the GPSI is used if available; SubscriptionManager.getPhoneNumber is available on Android 13 (API 33) and later
    @Test
    fun theMsisdnIsAvailableFromAndroid13() {
        assertFalse(ReportingClientIdStore.msisdnIsAvailableOn(32))
        assertTrue(ReportingClientIdStore.msisdnIsAvailableOn(33))
        assertTrue(ReportingClientIdStore.msisdnIsAvailableOn(34))
    }
}
