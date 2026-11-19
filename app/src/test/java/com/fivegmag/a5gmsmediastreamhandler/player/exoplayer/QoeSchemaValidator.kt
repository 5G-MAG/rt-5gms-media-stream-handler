package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import org.apache.xerces.parsers.SAXParser
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader

/**
 * Utility to validate QoE Report XML against the 3GPP Rel-19 XSD schema using Apache Xerces.
 */
object QoeSchemaValidator {

    /**
     * Validates the given XML string against the QoE Rel-19 schema.
     * @throws AssertionError if validation fails, containing detailed error messages.
     */
    fun validate(xmlContent: String) = validate(xmlContent, mapOf(RECEPTION_REPORT to "schemas/qoe/main.xsd"), true)

    /** The report as a whole against the base schema and the schema of TS 26.118 V19.0.0 clause 9.4.3 (see validateVr for why that fails) */
    fun validateAsWhole(xmlContent: String) =
        validate(xmlContent, mapOf(RECEPTION_REPORT to "schemas/qoe/main.xsd", VR_METRICS to "schemas/qoe/vr.xsd"), true)

    private const val RECEPTION_REPORT = "urn:3gpp:metadata:2017:HSD:receptionreport"
    private const val VR_METRICS = "urn:3gpp:metadata:2020:VR:metrics"
    private const val XSI = "http://www.w3.org/2001/XMLSchema-instance"

    /**
     * Validates a report that carries the virtual reality metrics of TS 26.118 V19.0.0 clause 9.4.3, in two parts. The schema of that clause
     * cannot validate the report as a whole: VrQoeReportType extends QoeReportType, whose content ends in a wildcard for other name spaces, and the
     * elements vrMetric and vrMetricSchemaVersion of the extension are in such a name space, so the extended type violates the unique particle
     * attribution rule (cos-nonambig, checked in QoeSchemaValidatorTest) and a validator takes the wildcard for them. The base type is therefore
     * checked with the report as the base schema allows it (the VR elements and xsi:type taken out) and each vrMetric against VrMetricType.
     */
    fun validateVr(xmlContent: String) {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xmlContent)))
        val metrics = ArrayList<org.w3c.dom.Element>()
        var versions = 0
        val reports = document.getElementsByTagNameNS(RECEPTION_REPORT, "QoeReport")
        for (i in 0 until reports.length) {
            val report = reports.item(i) as org.w3c.dom.Element
            if (report.getAttributeNS(XSI, "type") != "vr:VrQoeReportType") throw AssertionError("QoeReport is not of the type vr:VrQoeReportType")
            report.removeAttributeNS(XSI, "type")
            var child = report.firstChild
            while (child != null) {
                val next = child.nextSibling
                if (child.namespaceURI == VR_METRICS) {
                    when (child.localName) {
                        "vrMetric" -> metrics.add(child as org.w3c.dom.Element)
                        "vrMetricSchemaVersion" -> versions++
                    }
                    report.removeChild(child)
                }
                child = next
            }
        }
        if (versions != reports.length) throw AssertionError("each QoeReport needs one vrMetricSchemaVersion, found $versions")
        // the extension elements are the last of the report, so what is left is a report of the base type
        validate(serialize(document), mapOf(RECEPTION_REPORT to "schemas/qoe/main.xsd"), true)
        for (metric in metrics) {
            val holder = factory.newDocumentBuilder().newDocument()
            val root = holder.createElementNS(VR_METRICS, "vr:vrMetricTest")
            holder.appendChild(root)
            var child = metric.firstChild
            while (child != null) {
                root.appendChild(holder.importNode(child, true))
                child = child.nextSibling
            }
            // the schema of the clause as printed fails the full check (see above), the types of the metrics do not need it
            validate(serialize(holder), mapOf(VR_METRICS to "schemas/qoe/vr-parts.xsd"), false)
        }
    }

    private fun serialize(document: org.w3c.dom.Document): String {
        val out = java.io.StringWriter()
        javax.xml.transform.TransformerFactory.newInstance().newTransformer()
            .transform(javax.xml.transform.dom.DOMSource(document), javax.xml.transform.stream.StreamResult(out))
        return out.toString()
    }

    private fun validate(xmlContent: String, schemas: Map<String, String>, fullChecking: Boolean) {
        val schemaUrls = schemas.mapValues { javaClass.classLoader.getResource(it.value) ?: error("${it.value} not found") }
        val schemaUrl = schemaUrls.values.first()

        val parser = SAXParser()

        parser.setFeature("http://xml.org/sax/features/validation", true)
        parser.setFeature("http://apache.org/xml/features/validation/schema", true)
        parser.setFeature("http://apache.org/xml/features/validation/schema-full-checking", fullChecking)

        // harden
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        parser.setFeature("http://xml.org/sax/features/external-general-entities", false)
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        parser.setProperty(
            "http://apache.org/xml/properties/security-manager",
            org.apache.xerces.util.SecurityManager()
        )

        parser.setProperty(
            "http://apache.org/xml/properties/schema/external-schemaLocation",
            schemaUrls.entries.joinToString(" ") { "${it.key} ${it.value.toExternalForm()}" }
        )

        val errors = mutableListOf<String>()
        parser.errorHandler = object : ErrorHandler {
            override fun warning(ex: SAXParseException) { errors += "Warning [${ex.lineNumber}:${ex.columnNumber}]: ${ex.message}" }
            override fun error(ex: SAXParseException) { errors += "Error [${ex.lineNumber}:${ex.columnNumber}]: ${ex.message}" }
            override fun fatalError(ex: SAXParseException) {
                errors += "Fatal [${ex.lineNumber}:${ex.columnNumber}]: ${ex.message}"
                throw ex
            }
        }

        try {
            val src = InputSource(StringReader(xmlContent)).apply {
                systemId = schemaUrl.toExternalForm()
            }
            parser.parse(src)
        } catch (e: Exception) {
            errors += "Exception: ${e.javaClass.simpleName}: ${e.message}"
        }

        if (errors.isNotEmpty()) throw AssertionError("QoE Report XML Validation Failed:\n" + errors.joinToString("\n"))

    }
}
