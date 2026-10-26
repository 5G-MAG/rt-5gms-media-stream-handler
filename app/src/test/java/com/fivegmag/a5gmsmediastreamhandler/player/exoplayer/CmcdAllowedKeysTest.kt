package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CmcdAllowedKeysTest {

    @Test
    fun noScheme_withSessionIdentifier_allowsOnlySid() {
        assertEquals(setOf("sid"), ExoPlayerAdapter.getCmcdAllowedKeys(emptyList(), "delivery-1"))
    }

    @Test
    fun noScheme_withoutSessionIdentifier_allowsNothing() {
        assertTrue(ExoPlayerAdapter.getCmcdAllowedKeys(emptyList(), null).isEmpty())
    }

    @Test
    fun unknownScheme_withSessionIdentifier_allowsOnlySid() {
        assertEquals(setOf("sid"), ExoPlayerAdapter.getCmcdAllowedKeys(listOf("urn:example:other"), "delivery-1"))
    }

    @Test
    fun cmcdScheme_allowsItsKeysAndSid() {
        val scheme = CmcdSchemes.KEYS.keys.first()
        val keys = ExoPlayerAdapter.getCmcdAllowedKeys(listOf(scheme), "delivery-1")
        assertTrue(keys.containsAll(CmcdSchemes.KEYS[scheme]!!))
        assertTrue(keys.contains("sid"))
    }
}
