package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.LoadCompletedEvent
import io.mockk.every
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConsumptionReporterRedirectTest {
    private val bus = EventBus.builder().build()
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true)
    private val reporter: ConsumptionReporterExoplayer

    init {
        every { adapter.eventBus } returns bus
        every { adapter.getCurrentManifestUri() } returns "http://origin.example/live.mpd"
        reporter = ConsumptionReporterExoplayer(adapter)
        reporter.initialize()
    }

    @After
    fun tearDown() = reporter.reset()

    private fun loaded(dataType: Int, uri: String) {
        val spec = DataSpec(Uri.parse("http://origin.example/live.mpd"))
        val info = LoadEventInfo(1L, spec, Uri.parse(uri), emptyMap(), 0L, 0L, 0L)
        bus.post(LoadCompletedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), info, MediaLoadData(dataType)))
    }

    private fun entry(): String = reporter.getConsumptionReport("client", ConsumptionRequest())

    // TS 26.512 V19.3.0 clause 11.3.3.1: the URL retrieved at M4d after following any HTTP redirects
    @Test
    fun theMediaPlayerEntryIsTheManifestUrlAfterRedirects() {
        loaded(C.DATA_TYPE_MANIFEST, "https://as.example/dyn/live.mpd")
        assertTrue(entry().contains("\"mediaPlayerEntry\":\"https://as.example/dyn/live.mpd\""))
    }

    @Test
    fun theLoadOfAMediaSegmentDoesNotChangeIt() {
        loaded(C.DATA_TYPE_MANIFEST, "https://as.example/dyn/live.mpd")
        loaded(C.DATA_TYPE_MEDIA, "https://as.example/dyn/seg1.m4s")
        assertTrue(entry().contains("https://as.example/dyn/live.mpd"))
    }

    @Test
    fun withoutAManifestLoadTheUrlGivenToThePlayerIsUsed() {
        assertTrue(entry().contains("\"mediaPlayerEntry\":\"http://origin.example/live.mpd\""))
    }
}
