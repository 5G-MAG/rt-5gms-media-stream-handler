package com.fivegmag.a5gmsmediastreamhandler

import android.os.Parcel
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClientDataReportingParcelTest {
    @Test
    fun anEmptyListOfServiceLocationsSurvivesTheParcel() {
        val entry = ClientDataReportingEntry(arrayListOf(), arrayListOf(), "cmcd-headers", null, null, arrayListOf("br"))
        val parcel = Parcel.obtain()
        entry.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        @Suppress("UNCHECKED_CAST")
        val creator = ClientDataReportingEntry::class.java.getField("CREATOR").get(null) as android.os.Parcelable.Creator<ClientDataReportingEntry>
        val read = creator.createFromParcel(parcel)
        assertNotNull(read.serviceLocationUrls)
        assertEquals(entry, read)
    }
}
