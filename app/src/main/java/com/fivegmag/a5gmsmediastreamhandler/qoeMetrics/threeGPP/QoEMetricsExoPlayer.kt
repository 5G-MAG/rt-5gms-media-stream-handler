package com.fivegmag.a5gmsmediastreamhandler.qoeMetrics.threeGPP

import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.AvgThroughput
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevel
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.BufferLevelEntry
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.HttpList
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.QoeMetricsReport
import com.fivegmag.a5gmscommonlibrary.qoeMetricsModels.threeGPP.RepresentationSwitchList
import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter
import org.simpleframework.xml.core.Persister
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.StringReader
import java.io.StringWriter


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


    private fun serializeToXml(qoeMetricsReport: QoeMetricsReport): String {
        val serializer = Persister()
        val stringWriter = StringWriter()

        serializer.write(qoeMetricsReport, stringWriter)

        val serializedData = stringWriter.toString()

        //return serializedData
        return cleanupXml(serializedData)
    }

    fun cleanupXml(xmlString: String): String {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val xpp = factory.newPullParser()
        xpp.setInput(StringReader(xmlString))

        val writer = StringWriter()
        val serializer: XmlSerializer = factory.newSerializer()
        serializer.setOutput(writer)

        var eventType = xpp.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (xpp.name != "object") {
                        serializer.startTag(xpp.namespace, xpp.name)

                        // Copy attributes except "class" and those with value -1
                        for (i in 0 until xpp.attributeCount) {
                            val attributeName = xpp.getAttributeName(i)
                            val attributeValue = xpp.getAttributeValue(i)
                            if (attributeName != "class" && attributeValue != "-1") {
                                serializer.attribute("", attributeName, attributeValue)
                            }
                        }

                    }
                }

                XmlPullParser.END_TAG -> {
                    if (xpp.name != "object") {
                        serializer.endTag(xpp.namespace, xpp.name)
                    }
                }

                XmlPullParser.TEXT -> {
                    if (xpp.name != "object") {
                        serializer.text(xpp.text)
                    }
                }
            }
            eventType = xpp.next()
        }

        val result = writer.toString()

        // Remove empty lines
        val lines = result.split("\n")
            .filter { it.trim().isNotEmpty() }
            .toMutableList()

        // Join the lines back with newlines
        return lines.joinToString("\n")
    }

    fun getQoeMetricsReport(): String {
        val metricsList = ArrayList<Any>()
        val bufferLevel = getBufferLevel()
        val representationSwitchList = getRepresentationSwitchList()
        val httpList = getHttpList()

        if (bufferLevel.entries.size > 0) {
            metricsList.add(bufferLevel)
        }
        if (representationSwitchList.entries.size > 0) {
            metricsList.add(representationSwitchList)
        }
        if (httpList.entries.size > 0) {
            metricsList.add(httpList)
        }

        val qoeMetricsReport = QoeMetricsReport(metricsList)

        return serializeToXml(qoeMetricsReport)
    }


}