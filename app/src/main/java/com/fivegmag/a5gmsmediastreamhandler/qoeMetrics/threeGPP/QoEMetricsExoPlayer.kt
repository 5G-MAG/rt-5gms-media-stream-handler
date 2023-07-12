/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2023 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsmediastreamhandler.qoeMetrics.threeGPP

import androidx.media3.common.util.UnstableApi
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fivegmag.a5gmscommonlibrary.helpers.Metrics
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.helpers.XmlSchemaStrings.THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.AvgThroughput
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.PlaybackMetricsRequest
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

    /**
     * Returns the buffer level entries that were recorded since the list was reset the last time
     *
     * @return {BufferLevel}
     */
    private fun getBufferLevel(): BufferLevel {
        return exoPlayerAdapter.getBufferLevel()
    }

    /**
     * Returns all representation switches that have occurred since the list was reset the last time
     *
     * @return {RepresentationSwitchList}
     */
    private fun getRepresentationSwitchList(): RepresentationSwitchList {
        return exoPlayerAdapter.getRepresentationSwitchList()
    }

    /**
     * Returns all HTTP requests that were issues since the list was reset the last time
     *
     * @return {HttpList}
     */
    private fun getHttpList(): HttpList {
        return exoPlayerAdapter.getHttpList()
    }

    /**
     * Converts a ReceptionReport to XML using the Jackson library
     *
     * @param {ReceptionReport} input
     * @return
     */
    fun serializeReceptionReportToXml(input: ReceptionReport): String {
        val xmlMapper = XmlMapper()
        val serializedResult = xmlMapper.writeValueAsString(input)

        return "<?xml version=\"1.0\"?>$serializedResult"
    }

    /**
     * Adds a delimiter after the last QoeMetric element
     *
     * @param xmlString
     * @return
     */
    private fun addDelimiter(xmlString: String): String {
        val delimiter = "<sv:delimiter>0</sv:delimiter>"
        val qoeMetricEndTag = "</QoeMetric>"

        // Find the last occurrence of the </QoeMetric> tag
        val lastIndex = xmlString.lastIndexOf(qoeMetricEndTag)
        if (lastIndex != -1) {
            // Insert the delimiter after the last </QoeMetric> tag
            val stringBuilder = StringBuilder(xmlString)
            stringBuilder.insert(lastIndex + qoeMetricEndTag.length, delimiter)
            return stringBuilder.toString()
        }

        // If the </QoeMetric> tag is not found, return the original XML string
        return xmlString
    }

    /**
     * Generate a Qoe metrics report and serializes it to XML
     *
     * @param {PlaybackMetricsRequest} playbackMetricsRequest
     * @return {String}
     */
    fun getQoeMetricsReport(playbackMetricsRequest: PlaybackMetricsRequest): String {
        val qoeMetricsReport = QoeReport()
        qoeMetricsReport.reportTime = utils.getCurrentXsDateTime()
        qoeMetricsReport.periodId = exoPlayerAdapter.getCurrentPeriodId()
        qoeMetricsReport.reportPeriod = playbackMetricsRequest.reportPeriod?.toInt()

        if (shouldReportMetric(Metrics.BUFFER_LEVEL, playbackMetricsRequest.metrics)) {
            val bufferLevel = getBufferLevel()
            if (bufferLevel.entries.size > 0) {
                qoeMetricsReport.bufferLevel = arrayListOf(bufferLevel)
            }
        }

        if (shouldReportMetric(Metrics.REP_SWITCH_LIST, playbackMetricsRequest.metrics)) {
            val representationSwitchList = getRepresentationSwitchList()
            if (representationSwitchList.entries.size > 0) {
                qoeMetricsReport.representationSwitchList = arrayListOf(representationSwitchList)
            }
        }

        if (shouldReportMetric(Metrics.HTTP_LIST, playbackMetricsRequest.metrics)) {
            val httpList = getHttpList()
            if (httpList.entries.size > 0) {
                qoeMetricsReport.httpList = arrayListOf(httpList)
            }
        }

        val receptionReport =
            ReceptionReport(qoeMetricsReport, exoPlayerAdapter.getCurrentManifestUrl())
        receptionReport.xmlns = THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.SCHEMA
        receptionReport.schemaLocation =
            THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.SCHEMA + " " + THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.LOCATION
        receptionReport.xsi = THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.XSI
        receptionReport.sv = THREE_GPP_METADATA_2011_HSD_RECEPTION_REPORT.SV

        var xml = serializeReceptionReportToXml(receptionReport)
        xml = addDelimiter(xml)

        return xml
    }

    /**
     * Checks if a metric is supposed to be reported based on the metrics array provided via M5
     *
     * @param {String} metric
     * @param {ArrayList<String>} metricsList
     * @return
     */
    private fun shouldReportMetric(metric: String, metricsList: ArrayList<String>?): Boolean {
        return metricsList.isNullOrEmpty() || metricsList.contains(metric)
    }

}