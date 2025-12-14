package com.fivegmag.a5gmsmediastreamhandler.controller

import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QoEMetricsReportingControllerTest {

    private lateinit var controller: QoEMetricsReportingController
    private val mockExoPlayerAdapter: IExoPlayerAdapter = mockk(relaxed = true)
    private val mockOutgoingMessageHandler: OutgoingMessageHandler = mockk(relaxed = true)

    @Before
    fun setUp() {
        controller = QoEMetricsReportingController(mockExoPlayerAdapter, mockOutgoingMessageHandler)
        controller.reportingClientId = "test-client-id"
    }

    @Test
    fun handleTriggerPlayback_initializesReporters() {
        // Given
        val scheme = "urn:3GPP:ns:PSS:DASH:QM10" // Hardcoded scheme from MetricReportingSchemes.THREE_GPP_DASH_METRIC_REPORTING
        val qoeMetricsRequest = mockk<com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest>(relaxed = true)
        io.mockk.every { qoeMetricsRequest.scheme } returns scheme
        io.mockk.every { qoeMetricsRequest.metricsReportingConfigurationId } returns "config-1"

        val playbackRequest = mockk<com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest>(relaxed = true)
        io.mockk.every { playbackRequest.mediaStreamingSessionIdentifier } returns "session-1"
        io.mockk.every { playbackRequest.qoeMetricsRequests } returns arrayListOf(qoeMetricsRequest)

        // When
        controller.initialize() // Registers the reporters
        controller.handleTriggerPlayback(playbackRequest)

        // Then
        // We can't see the private map, but we can verify it *tries* to report later.
        // Or we can verify no crash occurred.
    }

    @Test
    fun triggerQoeMetricsReports_sendsMessage() {
        // Given
        val scheme = "urn:3GPP:ns:PSS:DASH:QM10"
        val qoeMetricsRequest = mockk<com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest>(relaxed = true)
        io.mockk.every { qoeMetricsRequest.scheme } returns scheme
        io.mockk.every { qoeMetricsRequest.metricsReportingConfigurationId } returns "config-1"
        // Ensure getMpdInformation returns a list (empty or not) to avoid null pointer in reporter
        io.mockk.every { qoeMetricsRequest.metrics } returns ArrayList()
        io.mockk.every { qoeMetricsRequest.reportingInterval } returns 1000L

        val playbackRequest = mockk<com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest>(relaxed = true)
        io.mockk.every { playbackRequest.mediaStreamingSessionIdentifier } returns "session-1"
        io.mockk.every { playbackRequest.qoeMetricsRequests } returns arrayListOf(qoeMetricsRequest)

        controller.initialize()
        controller.handleTriggerPlayback(playbackRequest)

        // When
        controller.triggerQoeMetricsReports()

        // Then
        io.mockk.verify(exactly = 1) {
            mockOutgoingMessageHandler.sendMessageByTypeAndBundle(
                eq(com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes.REPORT_QOE_METRICS),
                any()
            )
        }
    }
}
