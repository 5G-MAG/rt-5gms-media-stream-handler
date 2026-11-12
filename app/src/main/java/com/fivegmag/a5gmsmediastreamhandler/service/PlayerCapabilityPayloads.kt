package com.fivegmag.a5gmsmediastreamhandler.service

import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys as Keys

/** The payload of the reply to the interrogation of the Media Player capabilities (TS 26.512 V19.3.0 clause 4.7.3, table 13.2.4-1) */
object PlayerCapabilityPayloads {
    fun reply(mediaDeliverySessionId: String?, capabilities: List<String>): Map<String, Any?> = mapOf(
        Keys.MEDIA_DELIVERY_SESSION_IDENTIFIER to mediaDeliverySessionId,
        Keys.CAPABILITIES to ArrayList(capabilities)
    )
}
