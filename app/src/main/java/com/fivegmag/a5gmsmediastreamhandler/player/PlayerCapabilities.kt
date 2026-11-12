package com.fivegmag.a5gmsmediastreamhandler.player

import com.fivegmag.a5gmscommonlibrary.helpers.MediaPlayerCapabilities
import com.fivegmag.a5gmscommonlibrary.models.QosMonitoringReport

/** What reacts to the QoS monitoring results that the Media Session Handler notifies (TS 26.510 V19.2.0 clause 5.4.3, QOS_MONITORING_RESULTS) */
fun interface QosMonitoringResultsConsumer {
    fun onQosMonitoringResults(mediaDeliverySessionId: String?, results: QosMonitoringReport?)
}

/**
 * The capabilities property of the Media Player configuration and settings API (TS 26.512 V19.3.0 table 13.2.4-1: "A read-only list of Media
 * Player capabilities", table 13.2.4-2). The Media Session Handler reads it when it interrogates the Media Player (clause 4.7.3).
 *
 * CAPABILITY_L4S: "a protocol stack capable of handling ECN marking for L4S". The Media3 player of this library has none (the transport is
 * Media3's HTTP stack, which neither marks nor reads ECN), so the list names it only after the code that installs such a stack in the player
 * says so with l4sTransportStackPresent.
 *
 * CAPABILITY_QOS: "capable of reacting to QoS monitoring results". The player reacts through the consumer that the code which adapts its
 * behaviour (for example the bit rate adaptation) sets; without one the player does not react and does not declare it.
 */
class PlayerCapabilities {
    @Volatile
    var l4sTransportStackPresent: Boolean = false

    @Volatile
    var qosMonitoringResultsConsumer: QosMonitoringResultsConsumer? = null

    /** The names of table 13.2.4-2 that the Media Player has */
    fun list(): List<String> = buildList {
        if (l4sTransportStackPresent) add(MediaPlayerCapabilities.CAPABILITY_L4S)
        if (qosMonitoringResultsConsumer != null) add(MediaPlayerCapabilities.CAPABILITY_QOS)
    }
}
