package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.extractor.metadata.emsg.EventMessage
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ISO/IEC 23009-1:2026 clause K.6 (Service Description Event), table K.19 and TS 26.512 V19.3.0 clause 13.2.4
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServiceDescriptionActivationTest {
    private val scheme = ServiceDescriptionActivation.SCHEME

    private fun message(data: String, id: Long = 1, durationMs: Long = 60000, schemeIdUri: String = scheme) =
        EventMessage(schemeIdUri, "", durationMs, id, data.toByteArray())

    @Test
    fun anEventWithANewIdActivatesItsDescription() {
        val activation = ServiceDescriptionActivation()
        assertTrue(activation.activeIds().isEmpty())
        assertTrue(activation.onEvent(0, 1250, 1000, 60000))
        assertEquals(setOf(1250L), activation.activeIds())
    }

    // clause K.6.3: an Event with a known Event@id is ignored
    @Test
    fun anEventWithAKnownIdIsIgnored() {
        val activation = ServiceDescriptionActivation()
        assertTrue(activation.onEvent(0, 1250, 0, 1000))
        activation.expire(2000)
        assertFalse(activation.onEvent(0, 1250, 3000, 1000))
        assertTrue(activation.activeIds().isEmpty())
    }

    // table K.19: the duration after the presentation time
    @Test
    fun theDescriptionStaysActivatedForTheDurationOfTheEvent() {
        val activation = ServiceDescriptionActivation()
        activation.onEvent(0, 1250, 5000, 60000)
        assertFalse(activation.expire(64999))
        assertEquals(setOf(1250L), activation.activeIds())
        assertTrue(activation.hasEnd())
        assertTrue(activation.expire(65000))
        assertTrue(activation.activeIds().isEmpty())
        assertFalse(activation.hasEnd())
    }

    @Test
    fun anEventWithoutADurationLeavesTheDescriptionActivated() {
        val activation = ServiceDescriptionActivation()
        activation.onEvent(0, 1, 0, C.TIME_UNSET)
        assertFalse(activation.hasEnd())
        assertFalse(activation.expire(Long.MAX_VALUE))
        assertEquals(setOf(1L), activation.activeIds())
    }

    @Test
    fun descriptionsThatAreActivatedTogetherAreAllActiveAndALaterEventExtendsOne() {
        val activation = ServiceDescriptionActivation()
        activation.onEvent(0, 1, 0, 10000)
        activation.onEvent(1, 2, 0, 20000)
        activation.onEvent(2, 1, 5000, 10000)
        assertEquals(setOf(1L, 2L), activation.activeIds())
        activation.expire(16000)
        assertEquals(setOf(2L), activation.activeIds())
        activation.reset()
        assertTrue(activation.activeIds().isEmpty())
        assertTrue(activation.onEvent(0, 1, 0, 1))
    }

    @Test
    fun theEventNamesTheDescriptionByItsIdentifierOrCarriesIt() {
        assertEquals(1250L, ServiceDescriptionActivation.descriptionIdOf(message("1250")))
        assertEquals(1250L, ServiceDescriptionActivation.descriptionIdOf(message(" 1250\n")))
        assertEquals(2000L, ServiceDescriptionActivation.descriptionIdOf(message("""<ServiceDescription id="2000"><Latency target="2000"/></ServiceDescription>""")))
        assertNull(ServiceDescriptionActivation.descriptionIdOf(message("abc")))
        assertNull(ServiceDescriptionActivation.descriptionIdOf(message("""<Other id="2000"/>""")))
        assertNull(ServiceDescriptionActivation.descriptionIdOf(message("1250", schemeIdUri = "urn:mpeg:dash:event:2012")))
    }

    // the events as Media3 reads them from an MPD, then decoded here
    @Test
    fun theEventsOfAnMpdAsMedia3ReadsThemGiveTheDescriptions() {
        val mpd = """<?xml version="1.0"?><MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT3M" minBufferTime="PT1S">
            <Period id="1"><EventStream schemeIdUri="$scheme" timescale="1000">
              <Event presentationTime="0" duration="60000" id="0">1250</Event>
              <Event presentationTime="60000" duration="60000" id="1"><ServiceDescription id="2000"><ClientDataReporting schemeIdUri="urn:mpeg:dash:cta-5004:2023"><CMCDParameters keys="br"/></ClientDataReporting></ServiceDescription></Event>
            </EventStream><AdaptationSet id="1" mimeType="video/mp4"><Representation id="r" bandwidth="1000"><BaseURL>v.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>"""
        val manifest = DashManifestParser().parse(android.net.Uri.parse("http://as.example/m.mpd"), mpd.byteInputStream())
        val stream = manifest.getPeriod(0).eventStreams.single()
        assertEquals("$scheme/", stream.id())
        assertEquals(listOf(1250L, 2000L), stream.events.map { ServiceDescriptionActivation.descriptionIdOf(it) })
        assertEquals(listOf(60000L, 60000L), stream.events.map { it.durationMs })
        assertEquals(listOf(0L, 1L), stream.events.map { it.id })
    }

    private fun group(id: String, formatId: String, selected: Boolean, mime: String = "application/x-emsg", type: Int = C.TRACK_TYPE_METADATA) =
        Tracks.Group(TrackGroup(id, Format.Builder().setId(formatId).setSampleMimeType(mime).build()), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected))

    // the player has one metadata renderer: the EventStream of the scheme is selected if another one was chosen
    @Test
    fun theMetadataTrackOfTheSchemeIsSelectedWhenAnotherOneIs() {
        val other = group("a", "urn:mpeg:dash:event:2012/1", true)
        val ours = group("b", "$scheme/", false)
        val override = ServiceDescriptionActivation.trackOverride(Tracks(listOf(other, ours)))
        assertNotNull(override)
        assertEquals(ours.mediaTrackGroup, override!!.mediaTrackGroup)
        assertNull(ServiceDescriptionActivation.trackOverride(Tracks(listOf(group("a", "urn:mpeg:dash:event:2012/1", false), group("b", "$scheme/", true)))))
        assertNull(ServiceDescriptionActivation.trackOverride(Tracks(listOf(other))))
        assertNull(ServiceDescriptionActivation.trackOverride(Tracks.EMPTY))
    }

    @Test
    fun anActivatedDescriptionAddsItsEntryToTheCurrentOnes() {
        val fixed = ClientDataReportingEntry(keys = arrayListOf("br"))
        val event = ClientDataReportingEntry(keys = arrayListOf("bl"), serviceDescriptionId = 7L, activatedByEvent = true)
        assertEquals(listOf(fixed), ServiceDescriptionSelection.current(listOf(fixed), listOf(event), emptySet()))
        assertEquals(listOf(fixed, event), ServiceDescriptionSelection.current(listOf(fixed), listOf(event), setOf(7L)))
        assertEquals(listOf(fixed), ServiceDescriptionSelection.current(listOf(fixed), emptyList(), setOf(7L)))
    }

    // D4 on the phone, 2026-11-12: the manifest is live; the position of the player is relative to its window, which slides, so an activation
    // measured on it (60 s window position + 40 s duration) never ended: the window position stayed between 15 and 20 s for two minutes.
    @Test
    fun anActivationOfALivePresentationEndsAfterItsDurationOnTheTimeOfThePeriod() {
        val activation = ServiceDescriptionActivation()
        // the Event is delivered at a window position of 16 s; the window starts 2252 s into the period
        val start = ServiceDescriptionActivation.periodPositionMs(16000, 2252000)
        assertTrue(activation.onEvent(0, 1250, start, 40000))
        // the window slides: 20 s later it starts 2272 s into the period, the position in it is again 16 s
        assertFalse(activation.expire(ServiceDescriptionActivation.periodPositionMs(16000, 2272000)))
        // 40 s later: the same window position (16 s), the window starts 2292 s into the period
        assertTrue(activation.expire(ServiceDescriptionActivation.periodPositionMs(16000, 2292000)))
        assertTrue(activation.activeIds().isEmpty())
        // on the window position alone it would still be active
        val onWindowOnly = ServiceDescriptionActivation()
        onWindowOnly.onEvent(0, 1250, 16000, 40000)
        assertFalse(onWindowOnly.expire(16000))
    }

    // the live manifest of the phone proof D4 (ServiceDescription 1250 with the Scope, an EventStream of the scheme in a dynamic MPD)
    @Test
    fun theEventStreamOfTheLiveManifestOfD4HasTheEventAtItsPresentationTime() {
        val mpd = """<?xml version="1.0" encoding="utf-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-live:2011" type="dynamic" minimumUpdatePeriod="PT4S"
              suggestedPresentationDelay="PT4S" availabilityStartTime="2026-11-12T14:41:44.362Z" publishTime="2026-11-12T15:21:20.043Z" timeShiftBufferDepth="PT40.0S" minBufferTime="PT8.0S">
              <Period id="0" start="PT0.0S">
                <EventStream schemeIdUri="urn:mpeg:dash:event:service-description:2024" timescale="1000">
                  <Event presentationTime="2268638" duration="40000" id="0">1250</Event>
                </EventStream>
                <AdaptationSet id="0" contentType="video"><Representation id="0" mimeType="video/mp4" codecs="avc1.640028" bandwidth="5000000" width="1920" height="1080">
                  <SegmentTemplate timescale="15360" initialization="init.m4s" media="c${"$"}Number${"$"}.m4s" startNumber="585"><SegmentTimeline><S t="35880960" d="61440" r="9"/></SegmentTimeline></SegmentTemplate>
                </Representation></AdaptationSet>
              </Period></MPD>"""
        val manifest = DashManifestParser().parse(android.net.Uri.parse("http://as.example/manifest-event.mpd"), mpd.byteInputStream())
        assertTrue(manifest.dynamic)
        val stream = manifest.getPeriod(0).eventStreams.single()
        assertEquals("$scheme/", stream.id())
        assertEquals(listOf(2268638000L), stream.presentationTimesUs.toList())
        assertEquals(40000L, stream.events[0].durationMs)
        assertEquals(1250L, ServiceDescriptionActivation.descriptionIdOf(stream.events[0]))
        // the track of the stream is the one that the override looks for
        val format = Format.Builder().setId(stream.id()).setSampleMimeType("application/x-emsg").build()
        val tracks = Tracks(listOf(Tracks.Group(TrackGroup(stream.id(), format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))))
        assertNotNull(ServiceDescriptionActivation.trackOverride(tracks))
    }
}
