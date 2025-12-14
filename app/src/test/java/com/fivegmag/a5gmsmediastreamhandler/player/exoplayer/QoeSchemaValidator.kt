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
    fun validate(xmlContent: String) {
        val schemaUrl = javaClass.classLoader.getResource("schemas/qoe/main.xsd")
            ?: error("main.xsd not found")

        val parser = SAXParser()

        parser.setFeature("http://apache.org/xml/features/validation/schema", true)
        parser.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true)

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
            "urn:3gpp:metadata:2017:HSD:receptionreport ${schemaUrl.toExternalForm()}"
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
