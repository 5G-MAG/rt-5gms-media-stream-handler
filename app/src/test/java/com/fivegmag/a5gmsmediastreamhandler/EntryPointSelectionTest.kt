package com.fivegmag.a5gmsmediastreamhandler

import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmsmediastreamhandler.controller.SessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryPointSelectionTest {
    private fun entry(locator: String, contentType: String?) = EntryPoint(locator, contentType, null)

    @Test
    fun dashIsPreferredOverHlsWhateverTheOrder() {
        val selected = SessionController.selectEntryPoint(listOf(entry("a.m3u8", ContentTypes.HLS), entry("a.mpd", ContentTypes.DASH)))
        assertEquals("a.mpd", selected!!.locator)
    }

    @Test
    fun hlsIsPlayedWhenThereIsNoDash() {
        val selected = SessionController.selectEntryPoint(listOf(entry("x", "video/mp4"), entry("a.m3u8", ContentTypes.HLS)))
        assertEquals("a.m3u8", selected!!.locator)
    }

    @Test
    fun theFirstOfSeveralDashEntryPointsIsPlayed() {
        val selected = SessionController.selectEntryPoint(listOf(entry("1.mpd", ContentTypes.DASH), entry("2.mpd", ContentTypes.DASH)))
        assertEquals("1.mpd", selected!!.locator)
    }

    @Test
    fun nothingIsPlayedWithoutAKnownContentType() {
        assertNull(SessionController.selectEntryPoint(listOf(entry("x", null), entry("y", "video/mp4"))))
        assertNull(SessionController.selectEntryPoint(emptyList()))
    }
}
