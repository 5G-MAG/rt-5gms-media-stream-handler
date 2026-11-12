package com.fivegmag.a5gmsmediastreamhandler.player

import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerCapabilities
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys as Keys
import com.fivegmag.a5gmsmediastreamhandler.service.PlayerCapabilityPayloads
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCapabilitiesUnitTest {
    // TS 26.512 V19.3.0 table 13.2.4-2: the Media3 player has no L4S-capable protocol stack, so it declares nothing
    @Test
    fun thePlayerAsItIsDeclaresNoCapability() {
        assertEquals(emptyList<String>(), PlayerCapabilities().list())
    }

    // CAPABILITY_L4S is declared only once the code that installs an L4S-capable stack says so
    @Test
    fun anL4sCapableStackIsDeclaredAsCapabilityL4s() {
        val capabilities = PlayerCapabilities()
        capabilities.l4sTransportStackPresent = true
        assertEquals(listOf(MediaPlayerCapabilities.CAPABILITY_L4S), capabilities.list())
        capabilities.l4sTransportStackPresent = false
        assertEquals(emptyList<String>(), capabilities.list())
    }

    // Clause 4.7.3: the reply to the interrogation names the media delivery session and lists the capabilities
    @Test
    fun theReplyCarriesTheSessionAndTheCapabilities() {
        val reply = PlayerCapabilityPayloads.reply("s1", listOf("CAPABILITY_L4S"))
        assertEquals("s1", reply[Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER])
        assertEquals(arrayListOf("CAPABILITY_L4S"), reply[Keys.CAPABILITIES])
        assertEquals(arrayListOf<String>(), PlayerCapabilityPayloads.reply("s1", emptyList())[Keys.CAPABILITIES])
    }

    // Table 13.2.4-2: CAPABILITY_QOS, the Media Player is capable of reacting to QoS monitoring results: it has a consumer for them
    @Test
    fun aConsumerOfQosMonitoringResultsIsDeclaredAsCapabilityQos() {
        val capabilities = PlayerCapabilities()
        capabilities.qosMonitoringResultsConsumer = QosMonitoringResultsConsumer { _, _ -> }
        assertEquals(listOf(MediaPlayerCapabilities.CAPABILITY_QOS), capabilities.list())
        capabilities.l4sTransportStackPresent = true
        assertEquals(listOf(MediaPlayerCapabilities.CAPABILITY_L4S, MediaPlayerCapabilities.CAPABILITY_QOS), capabilities.list())
        capabilities.qosMonitoringResultsConsumer = null
        assertEquals(listOf(MediaPlayerCapabilities.CAPABILITY_L4S), capabilities.list())
    }
}
