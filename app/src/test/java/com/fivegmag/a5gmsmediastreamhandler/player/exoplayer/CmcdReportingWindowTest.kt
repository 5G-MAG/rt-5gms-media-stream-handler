package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CmcdReportingWindowTest {

    private fun rules(offset: Long?, duration: Long?) = ExoPlayerAdapter.getCmcdRules(
        listOf(QoeMetricsRequest(scheme = CmcdSchemes.OBJECT, samplingPeriod = 0, reportingStartOffset = offset, reportingDuration = duration))
    )

    private val key = CmcdSchemes.KEYS.getValue(CmcdSchemes.OBJECT).first()

    @Test
    fun noWindow_reportsFromTheStart() {
        val r = rules(null, null)
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(r, key, 0))
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(r, key, 3_600_000))
    }

    @Test
    fun keyIsLeftOutBeforeTheStartOffset() {
        val r = rules(10, null)
        assertFalse(ExoPlayerAdapter.isCmcdKeyInWindow(r, key, 9_999))
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(r, key, 10_000))
    }

    @Test
    fun keyIsLeftOutAfterTheDuration() {
        val r = rules(10, 20)
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(r, key, 30_000))
        assertFalse(ExoPlayerAdapter.isCmcdKeyInWindow(r, key, 30_001))
    }

    @Test
    fun sidIsSentOutsideTheWindow() {
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(rules(10, 5), "sid", 0))
    }

    @Test
    fun noRules_noRestriction() {
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(emptyList(), key, 123))
    }

    private fun filteredRules() = ExoPlayerAdapter.getCmcdRules(
        listOf(QoeMetricsRequest(scheme = CmcdSchemes.OBJECT, samplingPeriod = 0, metricsReportingConfigurationId = "c1", locationFiltered = true))
    )

    @Test
    fun locationFilter_keysAreHeldBackUntilTheUeIsReportedInside() {
        val r = filteredRules()
        assertFalse(ExoPlayerAdapter.isCmcdKeyInWindow(r, key, 1_000) { false })
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(r, key, 1_000) { it == "c1" })
    }

    @Test
    fun locationFilter_sidIsSentOutsideTheArea() {
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(filteredRules(), "sid", 1_000) { false })
    }

    @Test
    fun noLocationFilter_theLocationAnswerIsNotAsked() {
        assertTrue(ExoPlayerAdapter.isCmcdKeyInWindow(rules(null, null), key, 1_000) { false })
    }
}
