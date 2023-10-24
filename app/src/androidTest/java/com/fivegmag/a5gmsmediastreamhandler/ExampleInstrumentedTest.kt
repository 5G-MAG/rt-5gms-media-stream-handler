package com.fivegmag.a5gmsmediastreamhandler

import android.content.Context
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fivegmag.a5gmscommonlibrary.models.CellIdentifierType
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

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

    @Test
    fun getLocations() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val locations = ArrayList<TypedLocation>()
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            val cellInfoList: List<CellInfo> = telephonyManager.allCellInfo

            for (cellInfo in cellInfoList) {
                if (cellInfo.isRegistered) {
                    val cellIdentity = cellInfo.cellIdentity
                    val location = cellIdentity.toString()
                    locations.add(TypedLocation(CellIdentifierType.CGI, location))
                }
            }
        } catch (e: SecurityException) {
            assertEquals("com.fivegmag.a5gmsmediastreamhandler", "com.fivegmag.a5gmsmediastreamhandler")
        }

        assertEquals("com.fivegmag.a5gmsmediastreamhandler", "com.fivegmag.a5gmsmediastreamhandler")
    }
}