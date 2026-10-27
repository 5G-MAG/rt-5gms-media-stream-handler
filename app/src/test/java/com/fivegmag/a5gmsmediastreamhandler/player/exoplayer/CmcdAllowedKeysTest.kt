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

    @Test
    fun theTransmissionModeIsHeadersByDefaultAndTheQueryWhenAsked() {
        assertEquals(androidx.media3.exoplayer.upstream.CmcdConfiguration.MODE_REQUEST_HEADER, ExoPlayerAdapter.getCmcdMode(false))
        assertEquals(androidx.media3.exoplayer.upstream.CmcdConfiguration.MODE_QUERY_PARAMETER, ExoPlayerAdapter.getCmcdMode(true))
    }

    // TS 26.512 V19.3.0 clause E.2.5: #<class>/<key> selects one key of the class of the scheme
    @Test
    fun aTermSelectsOneKeyOfTheClass() {
        val keys = ExoPlayerAdapter.getCmcdAllowedKeys(
            listOf(CmcdSchemes.REQUEST), "d1",
            mapOf(CmcdSchemes.REQUEST to listOf("urn:3gpp:5gms:event-exposure:common-media-client-data#request/mtp"))
        )
        assertEquals(setOf("mtp", "sid"), keys)
    }

    @Test
    fun aTermOfTheClassSelectsAllItsKeys() {
        val keys = ExoPlayerAdapter.getCmcdAllowedKeys(
            listOf(CmcdSchemes.STATUS), null,
            mapOf(CmcdSchemes.STATUS to listOf("urn:3gpp:5gms:event-exposure:common-media-client-data#status"))
        )
        assertEquals(CmcdSchemes.KEYS[CmcdSchemes.STATUS]!! + "sid", keys)
    }

    @Test
    fun termsOfAnotherClassOrAnUnknownKeySelectNothing() {
        val keys = ExoPlayerAdapter.getCmcdAllowedKeys(
            listOf(CmcdSchemes.REQUEST), "d1",
            mapOf(CmcdSchemes.REQUEST to listOf("urn:3gpp:5gms:event-exposure:common-media-client-data#session/pr", "urn:3gpp:5gms:event-exposure:common-media-client-data#request/zz"))
        )
        assertEquals(setOf("sid"), keys)
    }

    @Test
    fun noTermsSelectTheWholeClass() {
        val keys = ExoPlayerAdapter.getCmcdAllowedKeys(listOf(CmcdSchemes.OBJECT), "d1", mapOf(CmcdSchemes.OBJECT to emptyList()))
        assertEquals(CmcdSchemes.KEYS[CmcdSchemes.OBJECT]!! + "sid", keys)
    }
}
