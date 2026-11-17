package com.fivegmag.a5gmsmediastreamhandler.service

import android.content.Intent
import android.net.Uri
import android.os.Messenger
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys

/** The payload of the Service URL launch request to the Media Session Handler (TS 26.510 V19.2.0 clause 6.3) */
object ServiceUrlPayloads {
    fun launchRequest(serviceUrl: String): Map<String, Any?> = mapOf(SessionHandlerBundleKeys.SERVICE_URL to serviceUrl)
}

/**
 * The request of a 3GPP Service URL by the URL handler of the Media Session Handler (TS 26.512 V19.3.0 clause 4.8.3.1). The intent goes to the
 * Media Session Handler's package, so that no browser is offered for the https URL. The Messenger of this Media Stream Handler instance names its
 * media delivery session context (the launch is then the one of LAUNCH_SERVICE_URL); the HTTP status of clause 4.8.3.4 is the extra
 * SessionHandlerBundleKeys.HTTP_RESPONSE_CODE of the activity result.
 */
object ServiceUrlIntents {
    const val MEDIA_SESSION_HANDLER_PACKAGE = "com.fivegmag.a5gmsmediasessionhandler"

    fun request(serviceUrl: String, invoker: Messenger?): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(serviceUrl)
        intent.setPackage(MEDIA_SESSION_HANDLER_PACKAGE)
        if (invoker != null) intent.putExtra(SessionHandlerBundleKeys.LAUNCH_CLIENT_MESSENGER, invoker)
        return intent
    }

    /** The HTTP status in the result of the activity; null when the Media Session Handler gave none (it did not run or was not found) */
    fun statusOf(result: Intent?): Int? =
        result?.takeIf { it.hasExtra(SessionHandlerBundleKeys.HTTP_RESPONSE_CODE) }?.getIntExtra(SessionHandlerBundleKeys.HTTP_RESPONSE_CODE, 0)
}
