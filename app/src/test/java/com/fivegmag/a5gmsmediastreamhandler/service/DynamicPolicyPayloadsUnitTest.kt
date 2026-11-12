package com.fivegmag.a5gmsmediastreamhandler.service

import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys as Keys
import com.fivegmag.a5gmscommonlibrary.models.BdtWindow
import com.fivegmag.a5gmscommonlibrary.models.DynamicPolicyStatus
import com.fivegmag.a5gmscommonlibrary.models.PolicyTemplateBinding
import com.fivegmag.a5gmscommonlibrary.models.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A PayloadReader over a map: what the Media Session Handler puts in the payload of a reply */
class MapReader(private val values: Map<String, Any?>) : PayloadReader {
    override fun string(key: String) = values[key] as String?
    override fun bool(key: String) = values[key] as Boolean? ?: false
    override fun has(key: String) = values.containsKey(key)

    @Suppress("UNCHECKED_CAST")
    override fun <T> list(key: String): List<T> = (values[key] as List<T>?) ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    override fun <T> item(key: String): T? = values[key] as T?
}

class DynamicPolicyPayloadsUnitTest {
    // TS 26.510 V19.2.0 table 11.3.1.1-1
    @Test
    fun getBdtInfoCarriesTheSessionAndThePoint() {
        assertEquals(
            mapOf(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to "s1", Keys.SERVICE_OPERATION_POINT_REFERENCE to "sop-1"),
            DynamicPolicyPayloads.bdtInfoRequest("s1", "sop-1")
        )
    }

    // Table 11.3.1.2-1: estimatedTransferVolume is conditional
    @Test
    fun activatePolicyCarriesTheParameters() {
        val request = DynamicPolicyPayloads.activatePolicyRequest("s1", "sop-1", 5000L, "video", "6 Mbps", null)
        assertEquals("s1", request[Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER])
        assertEquals("sop-1", request[Keys.SERVICE_OPERATION_POINT_REFERENCE])
        assertEquals(5000L, request[Keys.ESTIMATED_TRANSFER_VOLUME])
        assertEquals("video", request[Keys.COMPONENT_REFERENCE])
        assertEquals("6 Mbps", request[Keys.TARGET_DOWNLINK_BIT_RATE])
        assertNull(request[Keys.TARGET_UPLINK_BIT_RATE])
        assertNull(DynamicPolicyPayloads.activatePolicyRequest("s1", "sop-1", null, null, null, null)[Keys.ESTIMATED_TRANSFER_VOLUME])
    }

    // Table 11.3.1.3-1 and the status of table 11.3.2-1
    @Test
    fun deactivatePolicyAndTheStatusCarryTheSession() {
        assertEquals(mapOf(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to "s1"), DynamicPolicyPayloads.sessionRequest("s1"))
        assertEquals(mapOf(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to "s1"), DynamicPolicyPayloads.serviceOperationPointsRequest("s1"))
    }

    // Table 11.3.1.1-2
    @Test
    fun readsTheReturnValueOfGetBdtInfo() {
        val reply = DynamicPolicyPayloads.readBdtInfo(
            MapReader(
                mapOf(
                    Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to "s1", Keys.SERVICE_OPERATION_POINT_REFERENCE to "sop-1", Keys.SUCCEEDED to true,
                    Keys.WINDOW_START to "2026-10-28T10:00:00Z", Keys.WINDOW_END to "2026-10-28T11:00:00Z"
                )
            )
        )
        assertTrue(reply.succeeded)
        assertEquals("2026-10-28T10:00:00Z", reply.windowStart)
        assertEquals("2026-10-28T11:00:00Z", reply.windowEnd)
        assertNull(reply.reason)
    }

    @Test
    fun anUnsuccessfulGetBdtInfoHasNoWindowAndTheReason() {
        val reply = DynamicPolicyPayloads.readBdtInfo(MapReader(mapOf(Keys.SUCCEEDED to false, Keys.ERROR_REASON to "no window")))
        assertFalse(reply.succeeded)
        assertNull(reply.windowStart)
        assertEquals("no window", reply.reason)
    }

    // Table 11.3.1.2-2
    @Test
    fun readsTheReturnValueOfActivatePolicy() {
        val window = BdtWindow(TimeWindow("2026-10-28T10:00:00Z", "2026-10-28T11:00:00Z"), "5 Mbps")
        val reply = DynamicPolicyPayloads.readActivation(
            MapReader(
                mapOf(
                    Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to "s1", Keys.SERVICE_OPERATION_POINT_REFERENCE to "sop-1", Keys.SUCCEEDED to true,
                    Keys.RECOMMENDED_DOWNLINK_BIT_RATE to "6 Mbps", Keys.RECOMMENDED_UPLINK_BIT_RATE to "1 Mbps",
                    Keys.GRANTED_BACKGROUND_DATA_TRANSFER_WINDOWS to arrayListOf(window), Keys.L4S_ENABLED to true, Keys.QOS_MONITORING_ENABLED to false
                )
            )
        )
        assertEquals("6 Mbps", reply.recommendedDownlinkBitRate)
        assertEquals("1 Mbps", reply.recommendedUplinkBitRate)
        assertEquals(listOf(window), reply.grantedBackgroundDataTransferWindows)
        assertTrue(reply.l4SEnabled)
        assertFalse(reply.qoSMonitoringEnabled)
    }

    @Test
    fun anUnsuccessfulActivatePolicyHasNoValuesAndTheReason() {
        val reply = DynamicPolicyPayloads.readActivation(MapReader(mapOf(Keys.SUCCEEDED to false, Keys.ERROR_REASON to "refused")))
        assertFalse(reply.succeeded)
        assertNull(reply.recommendedDownlinkBitRate)
        assertTrue(reply.grantedBackgroundDataTransferWindows.isEmpty())
        assertFalse(reply.l4SEnabled)
        assertEquals("refused", reply.reason)
    }

    @Test
    fun readsTheServiceOperationPointsAndTheStatus() {
        val points = listOf(PolicyTemplateBinding("sop-1", "pt1", l4SEnablementPreference = true))
        assertTrue(DynamicPolicyPayloads.readServiceOperationPoints(MapReader(mapOf(Keys.SERVICE_OPERATION_POINTS to points)))[0].l4SEnablementPreference)
        val status = DynamicPolicyStatus("s1", "sop-1", "d1", arrayListOf(), true, true, null)
        assertEquals(status, DynamicPolicyPayloads.readStatus(MapReader(mapOf(Keys.CURRENT_DYNAMIC_POLICIES to status))))
        assertNull(DynamicPolicyPayloads.readStatus(MapReader(emptyMap())))
    }

    // TS 26.512 V19.3.0 table 13.2.5-1 and TS 26.510 V19.2.0 clause 5.4.3: the session, the external reference of the new point and its target QoS
    @Test
    fun anOperationPointChangeCarriesTheSessionTheReferenceAndTheTargetBitRates() {
        val payload = DynamicPolicyPayloads.operationPointChanged("s1", "sop-hd", "6 Mbps", null)
        assertEquals("s1", payload[Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER])
        assertEquals("sop-hd", payload[Keys.SERVICE_OPERATION_POINT_REFERENCE])
        assertEquals("6 Mbps", payload[Keys.TARGET_DOWNLINK_BIT_RATE])
        assertNull(payload[Keys.TARGET_UPLINK_BIT_RATE])
    }
}
