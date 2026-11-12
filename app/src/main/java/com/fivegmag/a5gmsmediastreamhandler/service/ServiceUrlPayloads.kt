package com.fivegmag.a5gmsmediastreamhandler.service

import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys

/** The payload of the Service URL launch request to the Media Session Handler (TS 26.510 V19.2.0 clause 6.3) */
object ServiceUrlPayloads {
    fun launchRequest(serviceUrl: String): Map<String, Any?> = mapOf(SessionHandlerBundleKeys.SERVICE_URL to serviceUrl)
}
