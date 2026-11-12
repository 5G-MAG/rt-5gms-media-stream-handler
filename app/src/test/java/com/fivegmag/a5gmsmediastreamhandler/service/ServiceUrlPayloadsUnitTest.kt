package com.fivegmag.a5gmsmediastreamhandler.service

import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceUrlPayloadsUnitTest {
    // the URL goes unchanged, so that client-data-reporting-mode reaches the Media Session Handler
    @Test
    fun launchRequestCarriesTheUrlUnchanged() {
        val url = "https://launch.3gppservices.org/ms/svc1?client-data-reporting-mode=query"
        assertEquals(mapOf(SessionHandlerBundleKeys.SERVICE_URL to url), ServiceUrlPayloads.launchRequest(url))
    }
}
