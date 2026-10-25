package com.fivegmag.a5gmsmediastreamhandler.service

import android.os.Bundle

/**
 * The answers of the Media Session Handler to the general client API (TS 26.510 V19.2.0 clause 11.2) and its notification events
 * (tables 11.2.3-2 and 11.2.3-3). The payload of an event is a bundle with the keys of SessionHandlerBundleKeys.
 */
interface GeneralSessionListener {
    /** The media delivery session identifier returned by createMediaDeliverySession(); state is ACTIVE or ERRORED */
    fun onMediaDeliverySessionCreated(mediaDeliverySessionIdentifier: String?, sessionHandlingState: String?)

    /** The general status of table 11.2.3-1; both are null when the identifier is not that of a media delivery session */
    fun onSessionStatus(mediaDeliverySessionIdentifier: String?, contentIdentifier: String?, sessionHandlingState: String?)

    /** The most recently sent consumption report of table 11.5.2-1; null when none has been sent */
    fun onConsumptionReportingStatus(mediaDeliverySessionIdentifier: String?, consumptionReport: String?) {}

    /** lastMetricsReport of table 11.6.2-1; the identifiers, the scheme and the report are null when none has been sent */
    fun onMetricsReportingStatus(
        mediaDeliverySessionIdentifier: String?,
        provisioningSessionId: String?,
        metricsReportingConfigurationId: String?,
        scheme: String?,
        metricsReport: String?
    ) {
    }

    /** An event of SessionHandlerEvents: SESSION_HANDLING_ACTIVATED, SESSION_HANDLING_TERMINATED, STREAMING_ACCESS_UPDATED or ERROR_SESSION_HANDLING */
    fun onNotification(event: String, payload: Bundle)
}
