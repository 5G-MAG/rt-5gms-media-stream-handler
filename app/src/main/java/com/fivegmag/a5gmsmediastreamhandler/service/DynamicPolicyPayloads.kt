package com.fivegmag.a5gmsmediastreamhandler.service

import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys as Keys
import com.fivegmag.a5gmscommonlibrary.models.BdtWindow
import com.fivegmag.a5gmscommonlibrary.models.DynamicPolicyStatus
import com.fivegmag.a5gmscommonlibrary.models.PolicyTemplateBinding

/** Read access to the payload of a message of the Media Session Handler; implemented over a Bundle (BundleReader) and over a map in the tests */
interface PayloadReader {
    fun string(key: String): String?
    fun bool(key: String): Boolean
    fun has(key: String): Boolean
    fun <T> list(key: String): List<T>
    fun <T> item(key: String): T?
}

/** The return value of getBDTInfo() (table 11.3.1.1-2); windowStart and windowEnd are null when the invocation was unsuccessful, with the reason */
data class BdtInfoReply(
    val mediaDeliverySessionId: String?,
    val serviceOperationPointReference: String?,
    val succeeded: Boolean,
    val windowStart: String?,
    val windowEnd: String?,
    val reason: String?
)

/** The return value of activatePolicy() (table 11.3.1.2-2); the values are null or empty when the invocation was unsuccessful, with the reason */
data class PolicyActivationReply(
    val mediaDeliverySessionId: String?,
    val serviceOperationPointReference: String?,
    val succeeded: Boolean,
    val recommendedDownlinkBitRate: String?,
    val recommendedUplinkBitRate: String?,
    val grantedBackgroundDataTransferWindows: List<BdtWindow>,
    val l4SEnabled: Boolean,
    val qoSMonitoringEnabled: Boolean,
    val reason: String?
)

/** The payloads of the messages of the Dynamic Policy client API (TS 26.510 V19.2.0 clause 11.3), as keys of SessionHandlerBundleKeys */
object DynamicPolicyPayloads {
    fun serviceOperationPointsRequest(sessionId: String): Map<String, Any?> = mapOf(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to sessionId)

    /** Table 11.3.1.1-1 */
    fun bdtInfoRequest(sessionId: String, serviceOperationPointReference: String): Map<String, Any?> = mapOf(
        Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to sessionId,
        Keys.SERVICE_OPERATION_POINT_REFERENCE to serviceOperationPointReference
    )

    /**
     * Table 11.3.1.2-1. componentReference, targetDownlinkBitRate and targetUplinkBitRate are not parameters of the method: they are the
     * extension that the Media Session Handler documents for a QoS-related Policy Template.
     */
    fun activatePolicyRequest(
        sessionId: String,
        serviceOperationPointReference: String,
        estimatedTransferVolume: Long?,
        componentReference: String?,
        targetDownlinkBitRate: String?,
        targetUplinkBitRate: String?
    ): Map<String, Any?> = mapOf(
        Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to sessionId,
        Keys.SERVICE_OPERATION_POINT_REFERENCE to serviceOperationPointReference,
        Keys.ESTIMATED_TRANSFER_VOLUME to estimatedTransferVolume,
        Keys.COMPONENT_REFERENCE to componentReference,
        Keys.TARGET_DOWNLINK_BIT_RATE to targetDownlinkBitRate,
        Keys.TARGET_UPLINK_BIT_RATE to targetUplinkBitRate
    )

    /** Table 11.3.1.3-1, and the request of the status of table 11.3.2-1 */
    fun sessionRequest(sessionId: String): Map<String, Any?> = mapOf(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to sessionId)

    fun readBdtInfo(reader: PayloadReader) = BdtInfoReply(
        reader.string(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER), reader.string(Keys.SERVICE_OPERATION_POINT_REFERENCE), reader.bool(Keys.SUCCEEDED),
        reader.string(Keys.WINDOW_START), reader.string(Keys.WINDOW_END), reader.string(Keys.ERROR_REASON)
    )

    fun readActivation(reader: PayloadReader) = PolicyActivationReply(
        reader.string(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER), reader.string(Keys.SERVICE_OPERATION_POINT_REFERENCE), reader.bool(Keys.SUCCEEDED),
        reader.string(Keys.RECOMMENDED_DOWNLINK_BIT_RATE), reader.string(Keys.RECOMMENDED_UPLINK_BIT_RATE),
        reader.list(Keys.GRANTED_BACKGROUND_DATA_TRANSFER_WINDOWS), reader.bool(Keys.L4S_ENABLED), reader.bool(Keys.QOS_MONITORING_ENABLED),
        reader.string(Keys.ERROR_REASON)
    )

    fun readServiceOperationPoints(reader: PayloadReader): List<PolicyTemplateBinding> = reader.list(Keys.SERVICE_OPERATION_POINTS)

    /** The status currentDynamicPolicies: null when no Dynamic Policy is in force for the media delivery session */
    fun readStatus(reader: PayloadReader): DynamicPolicyStatus? = reader.item(Keys.CURRENT_DYNAMIC_POLICIES)
}
