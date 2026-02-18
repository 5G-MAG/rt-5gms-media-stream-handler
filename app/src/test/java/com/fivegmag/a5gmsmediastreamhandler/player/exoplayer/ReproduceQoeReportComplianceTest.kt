package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReproduceQoeReportComplianceTest {

    private lateinit var reporter: QoeMetricsReporterExoplayer
    private val mockExoPlayerAdapter = mockk<IExoPlayerAdapter>(relaxed = true)

    @Before
    fun setUp() {
        every { mockExoPlayerAdapter.getCurrentPeriodId() } returns "Period1"
        reporter = QoeMetricsReporterExoplayer(mockExoPlayerAdapter)
    }


    @Test
    fun `report complies with Rel-19 XSD schema`() {
        val request = mockk<QoeMetricsRequest>(relaxed = true)
        every { request.metrics } returns ArrayList()
        every { request.reportingInterval } returns 5000L

        reporter.initialize(request)
        PlaybackSessionSimulator.simulateFullPlaybackSession()

        // Use valid hex for session ID to pass schema validation (hexBinary)
        val xmlReport = reporter.getQoeMetricsReport(request, "client-1", "deadbeef1234")
        
        println("Generated XML Report:\n$xmlReport")

        // Trigger validation - this will throw AssertionError with details if it fails
        QoeSchemaValidator.validate(xmlReport)
    }
}
