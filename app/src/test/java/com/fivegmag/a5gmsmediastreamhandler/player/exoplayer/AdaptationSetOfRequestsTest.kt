package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.dash.DashChunkSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.chunk.Chunk
import androidx.media3.exoplayer.source.chunk.ChunkHolder
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// ISO/IEC 23009-1:2026 table K.7: the filter adaptationSets of the client data reporting, per request
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdaptationSetOfRequestsTest {
    // the ladder of the phone run: AdaptationSet 0 holds Representations 0 to 3 (video), AdaptationSet 1 Representation 4 (audio)
    private val mpd = """<?xml version="1.0"?><MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT10S" minBufferTime="PT1S">
        <Period id="0"><AdaptationSet id="0" contentType="video" mimeType="video/mp4">
          <Representation id="0" bandwidth="5000000"><BaseURL>v0.mp4</BaseURL></Representation>
          <Representation id="1" bandwidth="2500000"><BaseURL>v1.mp4</BaseURL></Representation></AdaptationSet>
        <AdaptationSet id="1" contentType="audio" mimeType="audio/mp4"><Representation id="4" bandwidth="128000"><BaseURL>a4.mp4</BaseURL></Representation></AdaptationSet>
        <AdaptationSet contentType="text" mimeType="text/vtt"><Representation id="9" bandwidth="1000"><BaseURL>t.vtt</BaseURL></Representation></AdaptationSet>
        <AdaptationSet id="5" mimeType="video/mp4"><Representation id="0" bandwidth="1"><BaseURL>dup.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>"""
    private val manifest: DashManifest = DashManifestParser().parse(Uri.parse("http://as.example/m.mpd"), mpd.byteInputStream())

    @Test
    fun eachRepresentationHasTheIdOfItsAdaptationSet() {
        val ids = AdaptationSetOfRequests.adaptationSetOfRepresentations(manifest, 0, intArrayOf(0, 1))
        assertEquals(mapOf("0" to 0L, "1" to 0L, "4" to 1L), ids)
    }

    @Test
    fun aRepresentationIdOfTwoAdaptationSetsOrAnAdaptationSetWithoutIdGivesNone() {
        val ids = AdaptationSetOfRequests.adaptationSetOfRepresentations(manifest, 0, intArrayOf(0, 1, 2, 3))
        assertEquals(mapOf("1" to 0L, "4" to 1L), ids)
        assertTrue(AdaptationSetOfRequests.adaptationSetOfRepresentations(manifest, 3, intArrayOf(0)).isEmpty())
    }

    private fun chunk(uri: String, formatId: String): Chunk =
        object : Chunk(mockk<DataSource>(relaxed = true), DataSpec(Uri.parse(uri)), C.DATA_TYPE_MEDIA, Format.Builder().setId(formatId).build(), C.SELECTION_REASON_UNKNOWN, null, 0, 1000) {
            override fun cancelLoad() {}
            override fun load() {}
        }

    private fun source(registry: AdaptationSetOfRequests, next: Chunk?, indices: IntArray = intArrayOf(0, 1)): Pair<AdaptationSetRecordingChunkSource, DashChunkSource> {
        val delegate = mockk<DashChunkSource>(relaxed = true)
        io.mockk.every { delegate.getNextChunk(any(), any(), any(), any()) } answers { (arg<ChunkHolder>(3)).chunk = next }
        return AdaptationSetRecordingChunkSource(delegate, manifest, 0, indices, registry) to delegate
    }

    @Test
    fun theChunkSourceRecordsTheAdaptationSetOfTheRequestsItCreates() {
        val registry = AdaptationSetOfRequests()
        val (video, _) = source(registry, chunk("http://as.example/dist2/chunk-1-00561.m4s?CMCD=x", "1"))
        video.getNextChunk(LoadingInfo.Builder().setPlaybackPositionUs(0).build(), 0, emptyList(), ChunkHolder())
        assertEquals(0L, registry.idOf("http://as.example/dist2/chunk-1-00561.m4s?CMCD=x"))
        val (audio, _) = source(registry, chunk("http://as.example/chunk-4-00561.m4s", "4"))
        audio.getNextChunk(LoadingInfo.Builder().setPlaybackPositionUs(0).build(), 0, emptyList(), ChunkHolder())
        assertEquals(1L, registry.idOf("http://as.example/chunk-4-00561.m4s"))
        // the manifest and any other URL have none; a Representation that is in no AdaptationSet of the chunk source has none
        assertNull(registry.idOf("http://as.example/m.mpd"))
        val (other, _) = source(registry, chunk("http://as.example/x.m4s", "99"))
        other.getNextChunk(LoadingInfo.Builder().setPlaybackPositionUs(0).build(), 0, emptyList(), ChunkHolder())
        assertNull(registry.idOf("http://as.example/x.m4s"))
    }

    @Test
    fun theChunkSourceDelegatesTheRest() {
        val (wrapper, delegate) = source(AdaptationSetOfRequests(), null)
        wrapper.updateManifest(manifest, 0)
        wrapper.release()
        verify { delegate.updateManifest(manifest, 0) }
        verify { delegate.release() }
        val holder = ChunkHolder()
        wrapper.getNextChunk(LoadingInfo.Builder().setPlaybackPositionUs(0).build(), 0, emptyList(), holder)
        assertNull(holder.chunk)
    }

    @Test
    fun theRegistryKeepsTheLastRequestsOnly() {
        val registry = AdaptationSetOfRequests()
        for (i in 0 until 300) registry.record("http://as.example/$i", i.toLong())
        assertNull(registry.idOf("http://as.example/0"))
        assertEquals(299L, registry.idOf("http://as.example/299"))
        registry.clear()
        assertNull(registry.idOf("http://as.example/299"))
    }

    @Test
    fun theMediaSourceFactoryMakesDashSourcesAndLeavesTheRestToTheDefault() {
        val factory = RecordingMediaSourceFactory(RuntimeEnvironment.getApplication(), mockk<DataSource.Factory>(relaxed = true), AdaptationSetOfRequests())
        assertTrue(factory.createMediaSource(MediaItem.Builder().setUri("http://as.example/m.mpd").setMimeType(MimeTypes.APPLICATION_MPD).build()) is DashMediaSource)
        assertTrue(factory.createMediaSource(MediaItem.fromUri("http://as.example/m.mpd")) is DashMediaSource)
        assertTrue(factory.createMediaSource(MediaItem.fromUri("http://as.example/v.mp4")) is ProgressiveMediaSource)
    }

    // the filter at the request: only the requests of the listed AdaptationSets are covered, whatever their URL
    @Test
    fun anEntryThatNamesAdaptationSetsCoversTheirRequestsOnly() {
        val video = ClientDataReportingEntry(keys = arrayListOf("br"), adaptationSets = arrayListOf(0L))
        val url = "http://as.example/seg.m4s"
        assertEquals(listOf(video), CmcdMediaPlayerEntry.covering(listOf(video), url, "v", 0L))
        assertTrue(CmcdMediaPlayerEntry.covering(listOf(video), url, "a", 1L).isEmpty())
        // a request that is no media request, or whose AdaptationSet is not known, is not covered
        assertTrue(CmcdMediaPlayerEntry.covering(listOf(video), "http://as.example/m.mpd", "m", null).isEmpty())
        // without the filter every request is covered, as before
        assertEquals(1, CmcdMediaPlayerEntry.covering(listOf(ClientDataReportingEntry(keys = arrayListOf("br"))), url, "a", 1L).size)
        assertEquals("br=1", CmcdMediaPlayerEntry.formatData(CmcdMediaPlayerEntry.apply(CmcdMediaPlayerEntry.parseData("br=1,bl=2"), listOf(video), url, { false }, null, null, 0L)))
        assertEquals("", CmcdMediaPlayerEntry.formatData(CmcdMediaPlayerEntry.apply(CmcdMediaPlayerEntry.parseData("br=1,bl=2"), listOf(video), url, { false }, null, null, 1L)))
    }
}
