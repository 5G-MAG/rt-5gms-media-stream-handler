package com.fivegmag.a5gmsmediastreamhandler

import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.helpers.ClientDataReportingModes
import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.controller.SessionController
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import androidx.media3.datasource.DataSpec
import android.net.Uri
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CmcdReportingModeTest {
    private val adapter: IExoPlayerAdapter = mockk(relaxed = true)

    private fun trigger(mode: String?, entries: ArrayList<ClientDataReportingEntry>? = null) {
        val controller = SessionController(RuntimeEnvironment.getApplication(), adapter, mockk<OutgoingMessageHandler>(relaxed = true))
        controller.handleTriggerPlayback(
            PlaybackRequest(ArrayList(), ConsumptionRequest(), ArrayList(), "s", clientDataReportingMode = mode, clientDataReporting = entries)
        )
    }

    private val query = ClientDataReportingEntry(mode = ClientDataReportingModes.CMCD_QUERY)
    private val headers = ClientDataReportingEntry(mode = ClientDataReportingModes.CMCD_HEADERS)

    // TS 26.512 V19.3.0 table 12.4-1 and clause 10.5.1: the Service URL, the application, then the Media Player Entry; headers when none says
    @Test
    fun theModeOfTheServiceUrlTakesPrecedenceOverTheApplicationAndTheEntry() {
        assertEquals(true, ExoPlayerAdapter.getCmcdUsesQueryParameters(ClientDataReportingModes.CMCD_QUERY, false, listOf(headers)))
        assertEquals(false, ExoPlayerAdapter.getCmcdUsesQueryParameters(ClientDataReportingModes.CMCD_HEADERS, true, listOf(query)))
    }

    @Test
    fun theSettingOfTheApplicationTakesPrecedenceOverTheEntry() {
        assertEquals(true, ExoPlayerAdapter.getCmcdUsesQueryParameters(null, true, listOf(headers)))
        assertEquals(false, ExoPlayerAdapter.getCmcdUsesQueryParameters(null, false, listOf(query)))
    }

    // clause 12.4: without the parameter a directive of the Media Player Entry is honoured
    @Test
    fun theEntryIsHonouredWhenNothingElseSaysAndHeadersAreTheDefault() {
        assertEquals(true, ExoPlayerAdapter.getCmcdUsesQueryParameters(null, null, listOf(ClientDataReportingEntry(), query)))
        assertEquals(false, ExoPlayerAdapter.getCmcdUsesQueryParameters(null, null, listOf(headers)))
        assertEquals(false, ExoPlayerAdapter.getCmcdUsesQueryParameters(null, null, emptyList()))
        assertEquals(false, ExoPlayerAdapter.getCmcdUsesQueryParameters("cmcd-other", null, emptyList()))
    }

    @Test
    fun theModeAndTheEntriesOfThePlaybackRequestReachThePlayer() {
        trigger(ClientDataReportingModes.CMCD_QUERY, arrayListOf(headers))
        verify(exactly = 1) { adapter.configureCmcdMediaPlayerEntry(listOf(headers), ClientDataReportingModes.CMCD_QUERY) }
    }

    @Test
    fun withoutEntriesOrModeTheSettingOfThePreviousPlaybackIsNotKept() {
        trigger(null)
        verify(exactly = 1) { adapter.configureCmcdMediaPlayerEntry(emptyList(), null) }
        verify(exactly = 0) { adapter.setCmcdUseQueryParameters(any()) }
    }

    // clause 13.2.4 metricsConfiguration[]: a CMCD configuration of the application is reported with those of the playback request; other schemes are left out
    @Test
    fun theMetricsConfigurationsOfTheApplicationAreJoinedWithThoseOfThePlaybackRequest() {
        val controller = SessionController(RuntimeEnvironment.getApplication(), adapter, mockk<OutgoingMessageHandler>(relaxed = true))
        val fromRequest = QoeMetricsRequest(CmcdSchemes.REQUEST, 0, metricsReportingConfigurationId = "r1")
        val fromApplication = QoeMetricsRequest(CmcdSchemes.OBJECT, 0, metricsReportingConfigurationId = "a1")
        controller.setApplicationMetricsConfigurations(listOf(fromApplication, QoeMetricsRequest("urn:3GPP:ns:PSS:DASH:QM10", 0)))
        controller.handleTriggerPlayback(PlaybackRequest(ArrayList(), ConsumptionRequest(), arrayListOf(fromRequest), "s"))
        verify(exactly = 1) { adapter.configureCmcd(listOf(CmcdSchemes.REQUEST, CmcdSchemes.OBJECT), any(), any(), any()) }
        verify(exactly = 1) { adapter.configureCmcdReportingWindows(listOf(fromRequest, fromApplication)) }
    }

    // TS 26.512 V19.3.0 clause 10.1A: every media request cites the identifier in the CMCD-Session header, also in query mode, where the CMCD data are in the URL
    @Test
    fun theSessionIdentifierIsInTheHeaderAlsoWhenTheCmcdDataAreInTheQuery() {
        val spec = DataSpec(Uri.parse("http://as.example/seg1.m4s"))
        val sent = ExoPlayerAdapter.withSessionIdentifierHeader(spec, "8bf9f090-82fd-4686-aa4a-39e6a9381b76", true)
        assertEquals("sid=\"8bf9f090-82fd-4686-aa4a-39e6a9381b76\"", sent.httpRequestHeaders["CMCD-Session"])
    }

    // in header mode the player gives the header; a header that is there is left as it is
    @Test
    fun theHeaderIsNotAddedInHeaderModeOrOverwritten() {
        val spec = DataSpec(Uri.parse("http://as.example/seg1.m4s"))
        assertEquals(emptyMap<String, String>(), ExoPlayerAdapter.withSessionIdentifierHeader(spec, "s", false).httpRequestHeaders)
        val given = spec.withAdditionalHeaders(mapOf("CMCD-Session" to "cid=\"c\",sid=\"s\""))
        assertEquals("cid=\"c\",sid=\"s\"", ExoPlayerAdapter.withSessionIdentifierHeader(given, "s", true).httpRequestHeaders["CMCD-Session"])
        assertEquals(emptyMap<String, String>(), ExoPlayerAdapter.withSessionIdentifierHeader(spec, null, true).httpRequestHeaders)
    }
}
