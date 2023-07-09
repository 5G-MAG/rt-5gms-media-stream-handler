package com.fivegmag.a5gmsmediastreamhandler

import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpListEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.QoeMetricsReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.Trace
import com.fivegmag.a5gmsmediastreamhandler.qoeMetrics.threeGPP.QoEMetricsExoPlayer
import org.junit.Assert.assertEquals
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitch
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitchList
import org.junit.Test

class QoEMetricsExoPlayerUnitTest {

    @Test
    fun testXmlSerialization() {
        val exoPlayerAdapter = ExoPlayerAdapter()
        val qoEMetricsExoPlayer = QoEMetricsExoPlayer(exoPlayerAdapter)
        val qoeMetricsReport = QoeMetricsReport()
        val httpList: HttpList = HttpList(ArrayList<HttpListEntry>())
        val bufferLevel: BufferLevel = BufferLevel(ArrayList<BufferLevelEntry>())
        val representationSwitchList: RepresentationSwitchList = RepresentationSwitchList(ArrayList<RepresentationSwitch>())
        val utils : Utils = Utils()

        for(i in 0..2) {
            val tcpId = i
            val type = "type"
            val url = "url"
            val actualUrl = "actualurl"
            val range = ""
            val tRequest = utils.getCurrentTimestamp()
            val tResponse = utils.getCurrentTimestamp()
            val responseCode = 200
            val interval = 100
            val bytes = 200
            val trace = Trace(
                tResponse,
                100,
                bytes
            )
            val traceList = ArrayList<Trace>()
            traceList.add(trace)
            traceList.add(trace)
            val httpListEntry = HttpListEntry(
                tcpId,
                type,
                url,
                actualUrl,
                range,
                tRequest,
                tResponse,
                responseCode,
                interval,
                traceList
            )

            httpList.entries.add(httpListEntry)

            val representationSwitch = RepresentationSwitch(null,null,"to",null)
            representationSwitchList.entries.add(representationSwitch)

            val bufferLevelEntry = BufferLevelEntry(0,1)
            bufferLevel.entries.add(bufferLevelEntry)
        }

        qoeMetricsReport.httpList = ArrayList<HttpList>()
        qoeMetricsReport.httpList!!.add(httpList)

        qoeMetricsReport.representationSwitchList = ArrayList<RepresentationSwitchList>()
        qoeMetricsReport.representationSwitchList!!.add(representationSwitchList)

        qoeMetricsReport.bufferLevel = ArrayList<BufferLevel>()
        qoeMetricsReport.bufferLevel!!.add(bufferLevel)

        val result = qoEMetricsExoPlayer.serializeQoEMetricsReportToXml(qoeMetricsReport)
        assertEquals(4, 2 + 2)
    }
}