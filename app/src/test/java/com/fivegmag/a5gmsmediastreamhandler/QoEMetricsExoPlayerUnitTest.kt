package com.fivegmag.a5gmsmediastreamhandler

import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpListEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.Trace
import com.fivegmag.a5gmsmediastreamhandler.qoeMetrics.threeGPP.QoEMetricsExoPlayer
import org.junit.Assert.assertEquals
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.QoeReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.ReceptionReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitch
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitchList
import org.junit.Test

class QoEMetricsExoPlayerUnitTest {

    @Test
    fun testXmlSerialization() {
        val exoPlayerAdapter = ExoPlayerAdapter()
        val qoEMetricsExoPlayer = QoEMetricsExoPlayer(exoPlayerAdapter)
        val qoeMetricsReport = QoeReport()
        val httpList: HttpList = HttpList(ArrayList<HttpListEntry>())
        val bufferLevel: BufferLevel = BufferLevel(ArrayList<BufferLevelEntry>())
        val representationSwitchList: RepresentationSwitchList =
            RepresentationSwitchList(ArrayList<RepresentationSwitch>())

        val tcpId = 1
        val type = "type"
        val url = "url"
        val actualUrl = "actualurl"
        val range = ""
        val tRequest = "1970-1-1"
        val tResponse = "1970-1-2"
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

        val representationSwitch = RepresentationSwitch("1", "2", "to", 3)
        representationSwitchList.entries.add(representationSwitch)

        val bufferLevelEntry = BufferLevelEntry("0", 1)
        bufferLevel.entries.add(bufferLevelEntry)


        qoeMetricsReport.httpList = ArrayList<HttpList>()
        qoeMetricsReport.httpList!!.add(httpList)

        qoeMetricsReport.representationSwitchList = ArrayList<RepresentationSwitchList>()
        qoeMetricsReport.representationSwitchList!!.add(representationSwitchList)

        qoeMetricsReport.bufferLevel = ArrayList<BufferLevel>()
        qoeMetricsReport.bufferLevel!!.add(bufferLevel)

        val receptionReport = ReceptionReport(qoeMetricsReport, "test.mpd", "id")
        val result = qoEMetricsExoPlayer.serializeReceptionReportToXml(receptionReport)
        val expected =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><ReceptionReport contentURI=\"test.mpd\" clientID=\"id\"><QoeReport reportTime=\"\" reportPeriod=\"0\" periodID=\"\"><QoeMetric><HttpList><HttpListEntry tcpid=\"1\" type=\"type\" url=\"url\" actualurl=\"actualurl\" range=\"\" trequest=\"1970-1-1\" tresponse=\"1970-1-2\" responsecode=\"200\" interval=\"100\"><Trace s=\"1970-1-2\" d=\"100\" b=\"200\"/><Trace s=\"1970-1-2\" d=\"100\" b=\"200\"/></HttpListEntry></HttpList></QoeMetric><QoeMetric><RepSwitchList><RepSwitchEvent t=\"1\" mt=\"2\" to=\"to\" lto=\"3\"/></RepSwitchList></QoeMetric><QoeMetric><BufferLevel><BufferLevelEntry t=\"0\" level=\"1\"/></BufferLevel></QoeMetric></QoeReport></ReceptionReport>"
        assertEquals(expected, result)
    }
}