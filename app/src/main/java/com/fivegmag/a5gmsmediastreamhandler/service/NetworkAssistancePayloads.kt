package com.fivegmag.a5gmsmediastreamhandler.service

import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys as Keys

/** The return value of requestBitRateRecommendation() (table 11.4.1.1-2); the rates are null when the invocation was unsuccessful, with the reason */
data class BitRateRecommendationReply(
    val mediaDeliverySessionId: String?,
    val componentReference: String?,
    val succeeded: Boolean,
    val recommendedDownlinkBitRate: String?,
    val recommendedUplinkBitRate: String?,
    val reason: String?
)

/** The return value of requestDeliveryBoost() (table 11.4.1.2-2): granted is true if the delivery boost is granted */
data class DeliveryBoostReply(
    val mediaDeliverySessionId: String?,
    val componentReference: String?,
    val granted: Boolean,
    val reason: String?
)

/** The payloads of the messages of the Network Assistance client API (TS 26.510 V19.2.0 clause 11.4), as keys of SessionHandlerBundleKeys */
object NetworkAssistancePayloads {
    /** Tables 11.4.1.1-1 and 11.4.1.2-1 */
    fun request(sessionId: String, componentReference: String): Map<String, Any?> = mapOf(
        Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to sessionId,
        Keys.COMPONENT_REFERENCE to componentReference
    )

    fun readRecommendation(reader: PayloadReader) = BitRateRecommendationReply(
        reader.string(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER), reader.string(Keys.COMPONENT_REFERENCE), reader.bool(Keys.SUCCEEDED),
        reader.string(Keys.RECOMMENDED_DOWNLINK_BIT_RATE), reader.string(Keys.RECOMMENDED_UPLINK_BIT_RATE), reader.string(Keys.ERROR_REASON)
    )

    fun readBoost(reader: PayloadReader) = DeliveryBoostReply(
        reader.string(Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER), reader.string(Keys.COMPONENT_REFERENCE), reader.bool(Keys.SUCCEEDED), reader.string(Keys.ERROR_REASON)
    )
}
