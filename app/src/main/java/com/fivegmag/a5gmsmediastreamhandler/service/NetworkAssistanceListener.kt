package com.fivegmag.a5gmsmediastreamhandler.service

/**
 * The answers of the Media Session Handler to the Network Assistance client API (TS 26.510 V19.2.0 clause 11.4). The events BIT_RATE_RECOMMENDATION,
 * ERROR_BIT_RATE_RECOMMENDATION and ERROR_DELIVERY_BOOST (tables 11.4.2-2 and 11.4.2-3) arrive at the GeneralSessionListener.onNotification().
 */
interface NetworkAssistanceListener {
    /** requestBitRateRecommendation() */
    fun onBitRateRecommendation(reply: BitRateRecommendationReply) {}

    /** requestDeliveryBoost() */
    fun onDeliveryBoost(reply: DeliveryBoostReply) {}
}
