package com.fivegmag.a5gmsmediastreamhandler

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.os.Message
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmsmediastreamhandler.controller.IConsumptionReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.IQoEMetricsReportingController
import com.fivegmag.a5gmsmediastreamhandler.controller.SessionController
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.IncomingMessageHandler
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.greenrobot.eventbus.EventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** TS 26.510 V19.2.0 clause 5.3.5.1: a change of a client metrics reporting configuration reaches the player during the session */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@UnstableApi
class CmcdConfigurationUpdateTest {
    private val request = QoeMetricsRequest(CmcdSchemes.REQUEST, 0, 0, arrayListOf(), "c1")
    private val object1 = QoeMetricsRequest(CmcdSchemes.OBJECT, 0, 0, arrayListOf("urn:3GPP:ns:PSS:DASH:QM10#CMCDObject/br"), "c2", reportingStartOffset = 5)

    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true).also { every { it.eventBus } returns EventBus.builder().build() }
    private val sessionController = SessionController(mockk<Context>(relaxed = true), adapter, mockk<OutgoingMessageHandler>(relaxed = true))

    @Test
    fun theSessionControllerGivesThePlayerTheSchemesTheTermsAndTheRequests() {
        sessionController.handleCmcdConfigurationUpdate(listOf(request, object1))
        verify(exactly = 1) {
            adapter.updateCmcdConfiguration(
                listOf(CmcdSchemes.REQUEST, CmcdSchemes.OBJECT),
                mapOf(CmcdSchemes.REQUEST to emptyList(), CmcdSchemes.OBJECT to listOf("urn:3GPP:ns:PSS:DASH:QM10#CMCDObject/br")),
                listOf(request, object1)
            )
        }
    }

    // clause 10.5.1: the union with the metricsConfiguration[] of the settings API stays after a change
    @Test
    fun theSettingsOfTheApplicationStayInTheUnion() {
        sessionController.setApplicationMetricsConfigurations(listOf(object1))
        sessionController.handleCmcdConfigurationUpdate(listOf(request))
        verify(exactly = 1) { adapter.updateCmcdConfiguration(listOf(CmcdSchemes.REQUEST, CmcdSchemes.OBJECT), any(), listOf(request, object1)) }
    }

    @Test
    fun noConfigurationLeavesNoCmcdScheme() {
        sessionController.handleCmcdConfigurationUpdate(emptyList())
        verify(exactly = 1) { adapter.updateCmcdConfiguration(emptyList(), emptyMap(), emptyList()) }
    }

    @Test
    fun theMessageOfTheMediaSessionHandlerReachesTheSessionController() {
        val session = mockk<com.fivegmag.a5gmsmediastreamhandler.controller.ISessionController>(relaxed = true)
        val handler = IncomingMessageHandler()
        handler.initialize(mockk<IConsumptionReportingController>(relaxed = true), mockk<IQoEMetricsReportingController>(relaxed = true), session)
        val bundle = Bundle()
        bundle.putParcelableArrayList(SessionHandlerBundleKeys.CMCD_CONFIGURATIONS, arrayListOf(request, object1))
        val message = Message.obtain(null, SessionHandlerMessageTypes.CMCD_CONFIGURATION_UPDATE)
        message.data = bundle
        handler.getIncomingMessenger().send(message)
        shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 1) { session.handleCmcdConfigurationUpdate(listOf(request, object1)) }
    }

    // the keys and the windows follow the new configurations; the session identifier and the start of the session do not change
    @Test
    fun thePlayerKeepsTheSessionAndChangesTheKeysAndTheWindows() {
        val player = ExoPlayerAdapter()
        player.configureCmcd(listOf(CmcdSchemes.REQUEST), "sid-1", "content-1")
        player.configureCmcdReportingWindows(listOf(request))
        val start = field(player, "cmcdStartMs")
        player.updateCmcdConfiguration(listOf(CmcdSchemes.OBJECT), emptyMap(), listOf(object1))
        val keys = field(player, "cmcdAllowedKeys") as Set<*>
        assertTrue(keys.containsAll(CmcdSchemes.KEYS.getValue(CmcdSchemes.OBJECT).filter { it == "br" }))
        assertTrue("the keys of the removed configuration are gone", keys.intersect(CmcdSchemes.KEYS.getValue(CmcdSchemes.REQUEST) - CmcdSchemes.KEYS.getValue(CmcdSchemes.OBJECT)).isEmpty())
        assertTrue(keys.contains("sid"))
        assertEquals("sid-1", field(player, "cmcdSessionId"))
        assertEquals("content-1", field(player, "cmcdContentId"))
        assertEquals(start, field(player, "cmcdStartMs"))
        assertEquals(listOf("c2"), (field(player, "cmcdRules") as List<*>).map { (it as ExoPlayerAdapter.Companion.CmcdRule).configurationId })
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(target)
}
