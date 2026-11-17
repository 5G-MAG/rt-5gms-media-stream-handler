package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The schema check of the test suite must reject a report that the schema of TS 26.247 V19.1.0 clause 10.6.2 does not allow, or it proves nothing */
class QoeSchemaValidatorTest {
    private fun report(qoeReportContent: String) =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<ReceptionReport contentURI=\"http://example.com/a.mpd\" clientID=\"client-1\" xmlns=\"urn:3gpp:metadata:2017:HSD:receptionreport\" " +
            "xmlns:sv=\"urn:3gpp:metadata:2016:PSS:schemaVersion\"><sv:schemaVersion>TSG105-Rel18</sv:schemaVersion>" +
            "<QoeReport periodID=\"P1\" reportTime=\"2026-11-17T17:32:02.014Z\" reportPeriod=\"5\" recordingSessionId=\"deadbeef1234\">" +
            qoeReportContent +
            "<sv:delimiter>0</sv:delimiter><sv:delimiter>0</sv:delimiter><sv:delimiter>0</sv:delimiter><sv:delimiter>0</sv:delimiter></QoeReport>" +
            "<sv:delimiter>0</sv:delimiter></ReceptionReport>"

    @Test
    fun aReportWithAQoeMetricIsValid() {
        QoeSchemaValidator.validate(report("<QoeMetric><InitialPlayoutDelay>100</InitialPlayoutDelay></QoeMetric>"))
    }

    @Test
    fun aReportWithoutAQoeMetricIsRejected() {
        val rejection = assertThrows(AssertionError::class.java) { QoeSchemaValidator.validate(report("")) }
        assertTrue(rejection.message!!.contains("QoeMetric"))
    }

    @Test
    fun aSessionIdentifierThatIsNotHexBinaryIsRejected() {
        val uuid = report("<QoeMetric><InitialPlayoutDelay>100</InitialPlayoutDelay></QoeMetric>").replace("deadbeef1234", "8bf9f090-82fd-4686-aa4a-39e6a9381b76")
        val rejection = assertThrows(AssertionError::class.java) { QoeSchemaValidator.validate(uuid) }
        assertTrue(rejection.message!!.contains("hexBinary"))
    }
}
