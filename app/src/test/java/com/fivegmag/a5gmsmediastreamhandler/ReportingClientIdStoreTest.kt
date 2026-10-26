package com.fivegmag.a5gmsmediastreamhandler

import org.junit.Assert.assertEquals
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
}
