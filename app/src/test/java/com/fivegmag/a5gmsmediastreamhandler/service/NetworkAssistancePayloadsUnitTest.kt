package com.fivegmag.a5gmsmediastreamhandler.service

import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys as Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAssistancePayloadsUnitTest {
    // TS 26.510 V19.2.0 tables 11.4.1.1-1 and 11.4.1.2-1
    @Test
    fun theRequestsCarryTheSessionAndTheComponent() {
        assertEquals(
            mapOf(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to "s1", Keys.COMPONENT_REFERENCE to "video"),
            NetworkAssistancePayloads.request("s1", "video")
        )
    }

    // Table 11.4.1.1-2
    @Test
    fun readsTheReturnValueOfTheBitRateRecommendation() {
        val reply = NetworkAssistancePayloads.readRecommendation(
            MapReader(
                mapOf(
                    Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to "s1", Keys.COMPONENT_REFERENCE to "video", Keys.SUCCEEDED to true,
                    Keys.RECOMMENDED_DOWNLINK_BIT_RATE to "8 Mbps", Keys.RECOMMENDED_UPLINK_BIT_RATE to "2 Mbps"
                )
            )
        )
        assertTrue(reply.succeeded)
        assertEquals("8 Mbps", reply.recommendedDownlinkBitRate)
        assertEquals("2 Mbps", reply.recommendedUplinkBitRate)
        assertNull(reply.reason)
    }

    @Test
    fun anUnsuccessfulRecommendationHasNoRatesAndTheReason() {
        val reply = NetworkAssistancePayloads.readRecommendation(MapReader(mapOf(Keys.SUCCEEDED to false, Keys.ERROR_REASON to "refused")))
        assertFalse(reply.succeeded)
        assertNull(reply.recommendedDownlinkBitRate)
        assertEquals("refused", reply.reason)
    }

    // Table 11.4.1.2-2: true if the delivery boost is granted
    @Test
    fun readsTheReturnValueOfTheDeliveryBoost() {
        assertTrue(NetworkAssistancePayloads.readBoost(MapReader(mapOf(Keys.SUCCEEDED to true, Keys.COMPONENT_REFERENCE to "video"))).granted)
        val refused = NetworkAssistancePayloads.readBoost(MapReader(mapOf(Keys.SUCCEEDED to false, Keys.ERROR_REASON to "no")))
        assertFalse(refused.granted)
        assertEquals("no", refused.reason)
    }
}
