package com.fivegmag.a5gmsmediastreamhandler

import com.fivegmag.a5gmsmediastreamhandler.controller.ThresholdMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// TS 26.510 V19.2.0 clause 5.2.11.1: a metric is reported once when its value crosses a threshold and again only after it crossed back
class ThresholdMonitorUnitTest {
    private val buffer = "urn:3GPP:ns:PSS:DASH:QM10#BufferLevel"

    private fun monitor(positive: List<Float>? = null, negative: List<Float>? = null) = ThresholdMonitor(
        positive?.let { mapOf(buffer to it) }, negative?.let { mapOf(buffer to it) }
    )

    @Test
    fun aPositiveThresholdIsReportedOnceWhenTheValueExceedsIt() {
        val m = monitor(positive = listOf(1000f))
        assertTrue(m.sample("BufferLevel", 500.0).isEmpty())
        assertEquals(1, m.sample("BufferLevel", 1500.0).size)
        assertTrue(m.sample("BufferLevel", 2000.0).isEmpty())
    }

    @Test
    fun aPositiveThresholdIsReportedAgainOnlyAfterTheValueFellBelowIt() {
        val m = monitor(positive = listOf(1000f))
        m.sample("BufferLevel", 1500.0)
        assertTrue(m.sample("BufferLevel", 1000.0).isEmpty())
        assertTrue(m.sample("BufferLevel", 1200.0).isEmpty())
        m.sample("BufferLevel", 900.0)
        assertEquals(1, m.sample("BufferLevel", 1100.0).size)
    }

    @Test
    fun aNegativeThresholdIsReportedOnceWhenTheValueFallsBelowIt() {
        val m = monitor(negative = listOf(500f))
        assertTrue(m.sample("BufferLevel", 800.0).isEmpty())
        val first = m.sample("BufferLevel", 300.0)
        assertEquals(1, first.size)
        assertEquals(false, first[0].positive)
        assertTrue(m.sample("BufferLevel", 100.0).isEmpty())
        m.sample("BufferLevel", 600.0)
        assertEquals(1, m.sample("BufferLevel", 400.0).size)
    }

    @Test
    fun eachThresholdHasItsOwnState() {
        val m = monitor(positive = listOf(1000f, 2000f))
        assertEquals(1, m.sample("BufferLevel", 1500.0).size)
        assertEquals(1, m.sample("BufferLevel", 2500.0).size)
        assertTrue(m.sample("BufferLevel", 3000.0).isEmpty())
    }

    @Test
    fun theMetricIsNamedByTheKeyAfterTheHashWithoutParameters() {
        assertEquals("BufferLevel", ThresholdMonitor.metricName("urn:3GPP:ns:PSS:DASH:QM10#BufferLevel(1500)"))
        val m = ThresholdMonitor(mapOf("urn:3GPP:ns:PSS:DASH:QM10#BufferLevel(1500)" to listOf(1f)), null)
        assertTrue("BufferLevel" in m.metrics())
        assertTrue(m.sample("AvgThroughput", 5.0).isEmpty())
    }

    @Test
    fun noThresholdsIsEmpty() {
        assertTrue(ThresholdMonitor(null, null).isEmpty())
    }
}
