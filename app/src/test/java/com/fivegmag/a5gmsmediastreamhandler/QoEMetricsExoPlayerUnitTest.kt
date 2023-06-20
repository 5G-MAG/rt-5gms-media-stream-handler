package com.fivegmag.a5gmsmediastreamhandler

import com.fivegmag.a5gmsmediastreamhandler.qoeMetrics.threeGPP.QoEMetricsExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test

class QoEMetricsExoPlayerUnitTest {

    @Test
    fun cleanupXml() {
        val exoPlayerAdapter = ExoPlayerAdapter()
        val qoEMetricsExoPlayer = QoEMetricsExoPlayer(exoPlayerAdapter)
        val xmlString = """<QoeReport><QoeMetric class="java.util.ArrayList"><object class="com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevel"><BufferLevel><BufferLevelEntry level="3928" t="1687196339408"/></BufferLevel></object></QoeMetric><QoeMetric class="java.util.ArrayList"><object class="com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitchList"><RepSwitchList><RepSwitchEvent lto="-1" mt="268000" t="1687196338309" to="V300"/><RepSwitchEvent lto="-1" mt="268000" t="1687196338717" to="A48"/></RepSwitchList></object></QoeMetric></QoeReport>"""
        val targetString = """<QoeReport><QoeMetric><BufferLevel><BufferLevelEntry level="3928" t="1687196339408" /></BufferLevel></QoeMetric><QoeMetric><RepSwitchList><RepSwitchEvent mt="268000" t="1687196338309" to="V300" /><RepSwitchEvent mt="268000" t="1687196338717" to="A48" /></RepSwitchList></QoeMetric></QoeReport>"""
        val result = qoEMetricsExoPlayer.cleanupXml(xmlString)
        assertEquals(targetString, result)
    }
}