package com.fivegmag.a5gmsmediastreamhandler

/**
 * The reporting client identifier that is not an MSISDN: a random identifier made once and kept, so that it is the same across media delivery
 * sessions of this installation ("stable and globally unique", TS 26.510 V19.2.0 clause 5.3.6.2).
 */
class ReportingClientIdStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val generate: () -> String
) {
    companion object {
        /** Whether the platform can give the MSISDN, which is the GPSI "if available" (TS 26.510 V19.2.0 clause 5.3.6.2): SubscriptionManager.getPhoneNumber exists from Android 13 (API 33) */
        fun msisdnIsAvailableOn(sdkInt: Int): Boolean = sdkInt >= 33
    }

    fun get(): String {
        read()?.takeIf { it.isNotEmpty() }?.let { return it }
        val created = generate()
        write(created)
        return created
    }
}
