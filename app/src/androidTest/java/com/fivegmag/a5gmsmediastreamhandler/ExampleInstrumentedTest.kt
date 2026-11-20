package com.fivegmag.a5gmsmediastreamhandler

import android.content.Context
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fivegmag.a5gmscommonlibrary.models.CellIdentifierType
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.fivegmag.a5gmsmediastreamhandler", appContext.packageName)
    }
}
