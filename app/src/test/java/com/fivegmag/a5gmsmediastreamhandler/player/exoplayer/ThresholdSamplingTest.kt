package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import com.fivegmag.a5gmscommonlibrary.eventbus.FirstFrameRenderedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStartTriggerEvent
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmsmediastreamhandler.controller.MetricThresholdCrossedEvent
import io.mockk.every
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThresholdSamplingTest {
    class Collector {
        val crossings = ArrayList<MetricThresholdCrossedEvent>()
        @Subscribe fun on(event: MetricThresholdCrossedEvent) { crossings.add(event) }
    }

    private val bus = EventBus.builder().build()
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true)
    private val collector = Collector()
    private val reporter: QoeMetricsReporterExoplayer

    init {
        every { adapter.eventBus } returns bus
        every { adapter.getBufferLength() } returns 0L
        bus.register(collector)
        reporter = QoeMetricsReporterExoplayer(adapter)
    }

    @After
    fun tearDown() = reporter.reset()

    private fun request(vararg thresholds: Pair<String, Float>) = QoeMetricsRequest(
        "urn:3GPP:ns:PSS:DASH:QM10", 1, 0, ArrayList(), "c1",
        HashMap(thresholds.associate { it.first to arrayListOf(it.second) }), null
    )

    // TS 26.510 V19.2.0 table 9.2.3.1-1: a metric in the threshold list is reported once when its value exceeds the threshold
    @Test
    fun playoutDelayForMediaStartupCrossingAPositiveThresholdIsReportedOnce() {
        reporter.initialize(request("urn:3GPP:ns:PSS:DASH:QM10#PlayoutDelayforMediaStartup" to 500f))
        bus.post(PlaybackStartTriggerEvent(1000))
        bus.post(FirstFrameRenderedEvent(1800))
        ShadowLooper.idleMainLooper(3500, TimeUnit.MILLISECONDS)
        assertEquals(1, collector.crossings.size)
        assertEquals("PlayoutDelayforMediaStartup", collector.crossings[0].crossing.metric)
        assertEquals(800.0, collector.crossings[0].crossing.value, 0.0)
        assertTrue(collector.crossings[0].crossing.positive)
    }

    @Test
    fun aValueBelowTheThresholdIsNotReported() {
        reporter.initialize(request("urn:3GPP:ns:PSS:DASH:QM10#PlayoutDelayforMediaStartup" to 900f))
        bus.post(PlaybackStartTriggerEvent(1000))
        bus.post(FirstFrameRenderedEvent(1800))
        ShadowLooper.idleMainLooper(3500, TimeUnit.MILLISECONDS)
        assertTrue(collector.crossings.isEmpty())
    }
}
