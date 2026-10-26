package com.fivegmag.a5gmsmediastreamhandler.service

import com.fivegmag.a5gmscommonlibrary.models.DynamicPolicyStatus
import com.fivegmag.a5gmscommonlibrary.models.PolicyTemplateBinding

/**
 * The answers of the Media Session Handler to the Dynamic Policy client API (TS 26.510 V19.2.0 clause 11.3). The notification events of
 * tables 11.3.2-2 and 11.3.2-3 (POLICY_ACTIVATED, POLICY_DEACTIVATED, BACKGROUND_DATA_TRANSFER_OPPORTUNITY, L4S_ENABLED, QOS_MONITORING_ENABLED,
 * QOS_MONITORING_RESULTS, ERROR_INVALID_SERVICE_OPERATION_POINT, ERROR_UNAUTHORISED, ERROR_BACKGROUND_DATA_TRANSFER) arrive at the
 * GeneralSessionListener.onNotification() with the keys of SessionHandlerBundleKeys.
 */
interface DynamicPolicyListener {
    /** The Service Operation Points of the media delivery session: the policy template bindings, with externalReference and the L4S and QoS monitoring preferences */
    fun onServiceOperationPoints(mediaDeliverySessionId: String?, points: List<PolicyTemplateBinding>) {}

    /** getBDTInfo() */
    fun onBdtInfo(reply: BdtInfoReply) {}

    /** activatePolicy() */
    fun onPolicyActivation(reply: PolicyActivationReply) {}

    /** deactivatePolicy(): succeeded is true if the deactivation was successful */
    fun onPolicyDeactivation(mediaDeliverySessionId: String?, succeeded: Boolean, reason: String?) {}

    /** The status currentDynamicPolicies (table 11.3.2-1); null when no Dynamic Policy is in force */
    fun onDynamicPolicyStatus(mediaDeliverySessionId: String?, status: DynamicPolicyStatus?) {}
}
