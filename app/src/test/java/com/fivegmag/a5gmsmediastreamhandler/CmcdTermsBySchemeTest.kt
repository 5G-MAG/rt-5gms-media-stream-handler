package com.fivegmag.a5gmsmediastreamhandler

import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.controller.SessionController
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class CmcdTermsBySchemeTest {
    private fun request(scheme: String, vararg metrics: String) =
        QoeMetricsRequest(scheme, 1, 0, arrayListOf(*metrics), "c-$scheme-${metrics.size}")

    private fun playback(vararg requests: QoeMetricsRequest): PlaybackRequest {
        val playbackRequest = mockk<PlaybackRequest>(relaxed = true)
        every { playbackRequest.qoeMetricsRequests } returns arrayListOf(*requests)
        return playbackRequest
    }

    @Test
    fun termsOfConfigurationsOfOneSchemeAreJoined() {
        val terms = SessionController.cmcdTermsByScheme(playback(request(CmcdSchemes.REQUEST, "a"), request(CmcdSchemes.REQUEST, "b")))
        assertEquals(listOf("a", "b"), terms[CmcdSchemes.REQUEST])
    }

    @Test
    fun aConfigurationWithoutTermsLiftsTheRestrictionOfItsScheme() {
        val terms = SessionController.cmcdTermsByScheme(playback(request(CmcdSchemes.REQUEST, "a"), request(CmcdSchemes.REQUEST)))
        assertEquals(emptyList<String>(), terms[CmcdSchemes.REQUEST])
    }

    // clause 10.5.1: the union of metricsConfiguration[] of the settings API and the configurations of the Service Access Information
    @Test
    fun theTermsOfTheSettingsApiAreJoinedWithThoseOfTheServiceAccessInformation() {
        val terms = SessionController.cmcdTermsByScheme(listOf(request(CmcdSchemes.REQUEST, "a"), request(CmcdSchemes.REQUEST, "b"), request(CmcdSchemes.OBJECT, "c")))
        assertEquals(listOf("a", "b"), terms[CmcdSchemes.REQUEST])
        assertEquals(listOf("c"), terms[CmcdSchemes.OBJECT])
    }
}
