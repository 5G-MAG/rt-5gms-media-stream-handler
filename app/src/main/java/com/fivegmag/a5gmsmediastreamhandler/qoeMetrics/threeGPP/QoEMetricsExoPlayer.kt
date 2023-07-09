package com.fivegmag.a5gmsmediastreamhandler.qoeMetrics.threeGPP

import androidx.media3.common.util.UnstableApi
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.helpers.XmlSchemaStrings.THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.AvgThroughput
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.QoeReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.ReceptionReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitchList
import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter


@UnstableApi
class QoEMetricsExoPlayer(
    private val exoPlayerAdapter: ExoPlayerAdapter
) {

    private val utils: Utils = Utils()
    private val TAG = "QoEMetricsExoPlayer"

    private fun getAverageThroughput(): AvgThroughput? {
        return null
    }

    private fun getBufferLevel(): BufferLevel {
        val level: Int = exoPlayerAdapter.getBufferLength().toInt()
        val entries = ArrayList<BufferLevelEntry>()
        val time: Long = utils.getCurrentTimestamp()
        val entry = BufferLevelEntry(time, level)
        entries.add(entry)

        return BufferLevel(entries)
    }

    private fun getRepresentationSwitchList(): RepresentationSwitchList {
        return exoPlayerAdapter.getRepresentationSwitchList()
    }

    private fun getHttpList(): HttpList {
        return exoPlayerAdapter.getHttpList()
    }

    fun serializeQoEMetricsReportToXml(input: ReceptionReport): String {
        val xmlMapper = XmlMapper()
        val serializedResult = xmlMapper.writeValueAsString(input)

        return "<?xml version=\"1.0\"?>$serializedResult"
    }

    fun getQoeMetricsReport(): String {
        val qoeMetricsReport = QoeReport()

        val bufferLevel = getBufferLevel()
        if (bufferLevel.entries.size > 0) {
            qoeMetricsReport.bufferLevel = arrayListOf(bufferLevel)
        }

        val representationSwitchList = getRepresentationSwitchList()
        if (representationSwitchList.entries.size > 0) {
            qoeMetricsReport.representationSwitchList = arrayListOf(representationSwitchList)
        }

        val httpList = getHttpList()
        if (httpList.entries.size > 0) {
            qoeMetricsReport.httpList = arrayListOf(httpList)
        }

        val receptionReport =
            ReceptionReport(qoeMetricsReport, exoPlayerAdapter.getCurrentManifestUrl())
        receptionReport.xmlns = THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.SCHEMA
        receptionReport.schemaLocation =
            THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.SCHEMA + " " + THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.LOCATION
        receptionReport.xsi = THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.XSI

        return serializeQoEMetricsReportToXml(receptionReport)
    }


}