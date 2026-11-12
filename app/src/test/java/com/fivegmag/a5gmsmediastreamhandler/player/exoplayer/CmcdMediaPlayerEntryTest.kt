package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.net.Uri
import androidx.media3.datasource.DataSpec
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// TS 26.512 V19.3.0 clause 10.5.1 and ISO/IEC 23009-1:2026 K.3.7: dist1 and dist2, segment requests only, a set of keys, a content identifier
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CmcdMediaPlayerEntryTest {
    private val dist1 = "http://distribution-1.example/"
    private val dist2 = "http://distribution-2.example/"
    private val entry1 = ClientDataReportingEntry(
        arrayListOf(dist1), arrayListOf("a", "v", "av", "c", "tt"), null, null, "md:cid:EIDR:1", arrayListOf("br", "bl", "cid", "dl", "mtp", "nor", "ot", "sf", "v")
    )
    private val entry2 = ClientDataReportingEntry(arrayListOf(dist2), arrayListOf("a", "v", "av", "c", "tt"), null, null, "md:cid:EIDR:1", arrayListOf("br", "bl", "cid", "dl", "mtp", "nor"))
    private val entries = listOf(entry1, entry2)
    private val nothingConfigured = { _: String -> false }

    private fun pairs(text: String) = CmcdMediaPlayerEntry.parseData(text)

    private fun apply(text: String, url: String, entries: List<ClientDataReportingEntry> = this.entries, configured: (String) -> Boolean = nothingConfigured, contentId: String? = null) =
        CmcdMediaPlayerEntry.formatData(CmcdMediaPlayerEntry.apply(pairs(text), entries, url, configured, null, contentId))

    @Test
    fun dataIsReadAndWrittenAsItIs() {
        val text = """bl=21300,br=3200,cid="a,b\"c",nor="x%2Fy",su,sid="s""""
        assertEquals(listOf("bl", "br", "cid", "nor", "su", "sid"), pairs(text).map { it.first })
        assertEquals("\"a,b\\\"c\"", pairs(text)[2].second)
        assertNull(pairs(text)[4].second)
        assertEquals(text, CmcdMediaPlayerEntry.formatData(pairs(text)))
    }

    @Test
    fun aRequestOfAServiceLocationAndObjectTypeTheEntryCoversCarriesItsKeys() {
        // sf is a key of entry 1 and not of entry 2, so it is carried only to dist1; ot is carried because entry 1 names it; v is named too
        assertEquals("bl=1,br=2,cid=\"md:cid:EIDR:1\",ot=v,sf=d", apply("""bl=1,br=2,cid="x",ot=v,sf=d,su""", dist1 + "seg1.m4s"))
        assertEquals("bl=1,br=2,cid=\"md:cid:EIDR:1\"", apply("""bl=1,br=2,cid="x",ot=v,sf=d,su""", dist2 + "seg1.m4s"))
    }

    @Test
    fun aRequestThatNoEntryCoversCarriesTheSidOnly() {
        assertEquals("""sid="s"""", apply("""bl=1,ot=m,sid="s"""", dist1 + "manifest.mpd"))
        assertEquals("""sid="s"""", apply("""bl=1,ot=v,sid="s"""", "http://other.example/seg.m4s"))
        // no ot: the object type cannot be told, an entry that restricts the object types does not cover it
        assertEquals("""sid="s"""", apply("""bl=1,sid="s"""", dist1 + "seg.m4s"))
    }

    @Test
    fun theKeysOfTheConfigurationsAreKeptWhereAnEntryCoversTheRequest() {
        assertEquals("bd=1,bl=2,ot=v", apply("bd=1,bl=2,ot=v,pr=1", dist2 + "seg.m4s", configured = { it == "bd" || it == "ot" }))
    }

    @Test
    fun anEntryWithoutRestrictionCoversEveryRequest() {
        val all = listOf(ClientDataReportingEntry(keys = arrayListOf("bl")))
        assertEquals("bl=1", apply("bl=1,br=2", "http://any.example/m.mpd", all))
    }

    @Test
    fun theSessionAndContentIdentifierOfTheEntryReplaceThoseOfThePlayer() {
        val own = listOf(ClientDataReportingEntry(sessionId = "entry-sid", contentId = "entry-cid", keys = arrayListOf("cid", "sid")))
        assertEquals("""cid="entry-cid",sid="entry-sid"""", apply("""cid="c",sid="s"""", "http://any.example/seg.m4s", own))
    }

    // table K.8: the identifiers of the entry are for the keys that it names
    @Test
    fun theIdentifiersOfTheEntryAreUsedOnlyForTheKeysThatItNames() {
        val own = listOf(ClientDataReportingEntry(sessionId = "entry-sid", contentId = "entry-cid", keys = arrayListOf("br")))
        assertEquals("""br=1,cid="c",sid="s"""", apply("""br=1,cid="c",sid="s"""", "http://any.example/seg.m4s", own, configured = { it == "cid" }, contentId = "c"))
        val sidOnly = listOf(ClientDataReportingEntry(sessionId = "entry-sid", contentId = "entry-cid", keys = arrayListOf("sid")))
        assertEquals("""cid="c",sid="entry-sid"""", apply("""cid="c",sid="s"""", "http://any.example/seg.m4s", sidOnly, configured = { it == "cid" }, contentId = "c"))
    }

    // table I.4: init segments are the object type i, MPD requests m; a request type that the entry does not name gets none of its keys
    @Test
    fun requestTypesAreTheObjectTypesOfTheRequests() {
        val init = listOf(ClientDataReportingEntry(objectTypes = arrayListOf("i", "m"), keys = arrayListOf("br")))
        assertEquals("br=1,ot=i", apply("br=1,ot=i", "http://any.example/init.mp4", init, configured = { it == "ot" }))
        assertEquals("""sid="s"""", apply("""br=1,ot=v,sid="s"""", "http://any.example/seg.m4s", init))
        val all = listOf(ClientDataReportingEntry(keys = arrayListOf("br")))
        assertEquals("br=1", apply("br=1,ot=k", "http://any.example/key", all))
    }

    @Test
    fun theKeysAndObjectTypesAreWhatThePlayerHasToProduce() {
        assertEquals(setOf("br", "bl", "cid", "dl", "mtp", "nor", "ot", "sf", "v"), CmcdMediaPlayerEntry.keysOf(entries))
        assertTrue(CmcdMediaPlayerEntry.needsObjectType(entries))
        assertFalse(CmcdMediaPlayerEntry.needsObjectType(listOf(ClientDataReportingEntry(keys = arrayListOf("bl")))))
    }

    @Test
    fun theModesAreThoseOfTheServiceUrl() {
        assertEquals(true, CmcdMediaPlayerEntry.usesQueryParameters("cmcd-query"))
        assertEquals(false, CmcdMediaPlayerEntry.usesQueryParameters("cmcd-headers"))
        assertNull(CmcdMediaPlayerEntry.usesQueryParameters(null))
    }

    @Test
    fun theHeadersOfARequestAreReducedTogether() {
        val spec = DataSpec.Builder().setUri(dist1 + "seg.m4s").setHttpRequestHeaders(
            mapOf("CMCD-Object" to "br=1,ot=v", "CMCD-Request" to "bl=2,dl=3,su", "CMCD-Session" to """cid="c",sid="s",sf=d""", "CMCD-Status" to "bs", "Range" to "bytes=0-")
        ).build()
        val resolved = CmcdMediaPlayerEntry.resolve(spec, entries, { it == "bs" })
        assertEquals("br=1,ot=v", resolved.httpRequestHeaders["CMCD-Object"])
        assertEquals("bl=2,dl=3", resolved.httpRequestHeaders["CMCD-Request"])
        assertEquals("""cid="md:cid:EIDR:1",sid="s",sf=d""", resolved.httpRequestHeaders["CMCD-Session"])
        assertEquals("bs", resolved.httpRequestHeaders["CMCD-Status"])
        assertEquals("bytes=0-", resolved.httpRequestHeaders["Range"])
    }

    @Test
    fun aHeaderThatLosesAllItsKeysIsRemoved() {
        val spec = DataSpec.Builder().setUri(dist1 + "manifest.mpd").setHttpRequestHeaders(
            mapOf("CMCD-Object" to "ot=m", "CMCD-Request" to "bl=2", "CMCD-Session" to """sid="s"""")
        ).build()
        val resolved = CmcdMediaPlayerEntry.resolve(spec, entries, nothingConfigured)
        assertEquals(mapOf("CMCD-Session" to """sid="s""""), resolved.httpRequestHeaders)
    }

    @Test
    fun theQueryOfARequestIsReducedAndTheOtherParametersAreLeftAlone() {
        val cmcd = Uri.encode("""bl=1,br=2,cid="x",ot=v,sf=d,su,sid="s"""")
        val spec = DataSpec.Builder().setUri(Uri.parse(dist2 + "seg.m4s?token=a%20b&CMCD=$cmcd&x=1")).build()
        val resolved = CmcdMediaPlayerEntry.resolve(spec, entries, nothingConfigured)
        val query = resolved.uri.encodedQuery!!.split('&')
        assertEquals("token=a%20b", query[0])
        assertEquals("x=1", query[2])
        assertEquals("""bl=1,br=2,cid="md:cid:EIDR:1",sid="s"""", Uri.decode(query[1].substringAfter('=')))
    }

    @Test
    fun aQueryThatLosesAllItsKeysLosesTheParameter() {
        val spec = DataSpec.Builder().setUri(Uri.parse(dist1 + "m.mpd?CMCD=" + Uri.encode("bl=1"))).build()
        val resolved = CmcdMediaPlayerEntry.resolve(spec, entries, nothingConfigured)
        assertNull(resolved.uri.encodedQuery)
    }

    @Test
    fun withoutEntriesTheRequestIsLeftAsItIs() {
        val spec = DataSpec.Builder().setUri(dist1 + "seg.m4s").setHttpRequestHeaders(mapOf("CMCD-Request" to "bl=2")).build()
        assertSame(spec, CmcdMediaPlayerEntry.resolve(spec, emptyList(), nothingConfigured))
    }

    // clause 13.2.4: the cmcdReporting setting of the application adds its keys to the configured ones and restricts nothing
    @Test
    fun theKeysOfTheApplicationAreAddedToTheConfiguredKeys() {
        val application = ClientDataReportingEntry(keys = arrayListOf("mtp"), contentId = "app-cid")
        val text = """bl=1,cid="c",mtp=2,br=3,sid="s""""
        val reduced = CmcdMediaPlayerEntry.apply(pairs(text), emptyList(), "http://any.example/seg.m4s", { it == "bl" || it == "cid" }, application)
        assertEquals("""bl=1,cid="app-cid",mtp=2,sid="s"""", CmcdMediaPlayerEntry.formatData(reduced))
    }

    // clause 10.5.1: the restriction of the Media Player Entry stays: a request that it does not cover gets no key of the entries or of the application,
    // only the configured keys and the sid
    @Test
    fun theApplicationDoesNotLiftTheRestrictionOfTheEntry() {
        val application = ClientDataReportingEntry(keys = arrayListOf("mtp"))
        val reduced = CmcdMediaPlayerEntry.apply(pairs("""bl=1,mtp=2,ot=m,sid="s""""), entries, dist1 + "manifest.mpd", { it == "bl" }, application)
        assertEquals("""bl=1,sid="s"""", CmcdMediaPlayerEntry.formatData(reduced))
    }

    // clause 13.2.4: the setting of the application takes precedence over the content identifier of the entry
    @Test
    fun theContentIdentifierOfTheApplicationTakesPrecedenceOverTheEntry() {
        val application = ClientDataReportingEntry(contentId = "app-cid")
        val reduced = CmcdMediaPlayerEntry.apply(pairs("""cid="c",ot=v"""), entries, dist1 + "seg.m4s", { it == "cid" }, application)
        // ot is a key that entry 1 names
        assertEquals("""cid="app-cid",ot=v""", CmcdMediaPlayerEntry.formatData(reduced))
    }

    @Test
    fun theApplicationAloneIsAppliedToTheHeadersOfARequest() {
        val spec = DataSpec.Builder().setUri("http://any.example/seg.m4s").setHttpRequestHeaders(mapOf("CMCD-Request" to "bl=2,mtp=3,su")).build()
        val resolved = CmcdMediaPlayerEntry.resolve(spec, emptyList(), nothingConfigured, ClientDataReportingEntry(keys = arrayListOf("mtp")))
        assertEquals("mtp=3", resolved.httpRequestHeaders["CMCD-Request"])
    }

    // clause 10.5.1 and ISO table K.7: an entry that names Service Locations covers only the requests under their BaseURLs; the parser gives an
    // empty list for labels that no BaseURL carries, which covers no request (segment, init or manifest), so only the configured keys and the sid stay
    @Test
    fun anEntryWithServiceLocationsThatNoBaseUrlCarriesCoversNoRequest() {
        val none = listOf(ClientDataReportingEntry(arrayListOf(), keys = arrayListOf("br", "bl", "ot")))
        for ((url, ot) in listOf("http://as.example/seg.m4s" to "v", "http://as.example/init.mp4" to "i", "http://as.example/manifest.mpd" to "m")) {
            assertEquals("""bd=1,sid="s"""", apply("""bd=1,br=2,bl=3,ot=$ot,sid="s"""", url, none, configured = { it == "bd" }))
            assertEquals(emptyList<ClientDataReportingEntry>(), CmcdMediaPlayerEntry.covering(none, url, ot))
        }
        val spec = DataSpec.Builder().setUri("http://as.example/seg.m4s").setHttpRequestHeaders(mapOf("CMCD-Request" to "bl=3", "CMCD-Object" to "br=2,ot=v")).build()
        assertTrue(CmcdMediaPlayerEntry.resolve(spec, none, nothingConfigured).httpRequestHeaders.isEmpty())
    }

    @Test
    fun anEntryWithServiceLocationsCoversTheRequestsUnderTheirBaseUrlsOnly() {
        val some = listOf(ClientDataReportingEntry(arrayListOf(dist1), keys = arrayListOf("br", "bl")))
        assertEquals("br=2,bl=3", apply("br=2,bl=3,su", dist1 + "seg.m4s", some))
        assertEquals("", apply("br=2,bl=3,su", dist2 + "seg.m4s", some))
        // absent serviceLocations: all of them
        assertEquals("br=2", apply("br=2,su", "http://anywhere.example/seg.m4s", listOf(ClientDataReportingEntry(keys = arrayListOf("br")))))
    }

    // the phone run: dist2 lies below dist1 (http://as/ps/ and http://as/ps/dist2/), the manifest is in the directory of dist1
    private val nested1 = "http://10.45.0.1:8080/m4d/ps/"
    private val nested2 = "http://10.45.0.1:8080/m4d/ps/dist2/"

    @Test
    fun aRequestIsInTheServiceLocationOfTheLongestBaseUrl() {
        val onlyDist1 = ClientDataReportingEntry(arrayListOf(nested1), keys = arrayListOf("br"), otherServiceLocationUrls = arrayListOf(nested2))
        val onlyDist2 = ClientDataReportingEntry(arrayListOf(nested2), keys = arrayListOf("br"), otherServiceLocationUrls = arrayListOf(nested1))
        assertEquals("br=2", apply("br=2", nested1 + "chunk-stream0-00561.m4s", listOf(onlyDist1)))
        assertEquals("", apply("br=2", nested2 + "chunk-stream0-00561.m4s", listOf(onlyDist1)))
        assertEquals("br=2", apply("br=2", nested2 + "init-stream0.m4s", listOf(onlyDist2)))
        assertEquals("", apply("br=2", nested1 + "init-stream0.m4s", listOf(onlyDist2)))
        assertEquals("", apply("br=2", nested1 + "manifest-d2.mpd", listOf(onlyDist2)))
        // without the other locations the shorter URL claims the nested one (the entry of an older Media Session Handler)
        assertEquals("br=2", apply("br=2", nested2 + "init-stream0.m4s", listOf(ClientDataReportingEntry(arrayListOf(nested1), keys = arrayListOf("br")))))
    }

    // the content identifier of an entry that covers no request does not reach it through the cid of the player
    @Test
    fun theContentIdentifierOfAnEntryThatCoversNoRequestIsNotSent() {
        val none = listOf(ClientDataReportingEntry(arrayListOf(), contentId = "entry-cid", keys = arrayListOf("cid", "br")))
        assertEquals("""sid="s"""", apply("""cid="entry-cid",br=1,sid="s"""", "http://as.example/seg.m4s", none))
        // the content identifier of the configurations is reported, with the value of the configurations
        assertEquals("""cid="cfg",sid="s"""", apply("""cid="entry-cid",br=1,sid="s"""", "http://as.example/seg.m4s", none, configured = { it == "cid" }, contentId = "cfg"))
        // an entry that covers the request and names cid without a content identifier of its own gives none: the configurations' one, or no cid
        val noId = listOf(ClientDataReportingEntry(keys = arrayListOf("cid")), ClientDataReportingEntry(arrayListOf(), contentId = "other", keys = arrayListOf("cid")))
        assertEquals("""sid="s"""", apply("""cid="other",sid="s"""", "http://as.example/seg.m4s", noId))
        assertEquals("""cid="cfg",sid="s"""", apply("""cid="other",sid="s"""", "http://as.example/seg.m4s", noId, contentId = "cfg"))
    }

    // end to end on the data of a request: the query of the player, reduced for an entry as the Media Session Handler reads it from the manifest
    @Test
    fun theQueryOfARequestOutsideTheNamedLocationLosesTheKeysOfTheEntry() {
        val entry = ClientDataReportingEntry(arrayListOf(), arrayListOf("a", "v", "av", "c", "tt", "i", "m"), "cmcd-query", null, "entry-cid-1", arrayListOf("bl", "br", "cid", "ot", "sid", "v"))
        val query = "CMCD=" + Uri.encode("""bl=1,br=2,cid="entry-cid-1",ot=v,sid="s"""")
        val spec = DataSpec.Builder().setUri("http://10.45.0.1:8080/m4d/ps/dist2/chunk-stream0-00561.m4s?$query").build()
        assertEquals(
            "http://10.45.0.1:8080/m4d/ps/dist2/chunk-stream0-00561.m4s?CMCD=" + Uri.encode("""sid="s""""),
            CmcdMediaPlayerEntry.resolve(spec, listOf(entry), nothingConfigured).uri.toString()
        )
    }

    // D4 on the phone, 2026-11-12: the player produced the cid of a description that an Event had not activated yet, and the request carried it
    // from the first request on. Until an Event activates the description the request keeps only the configured keys and the sid.
    @Test
    fun theCidOfADescriptionThatNoEventHasActivatedIsRemovedFromTheRequest() {
        val spec = DataSpec.Builder().setUri(dist1 + "seg.m4s").setHttpRequestHeaders(
            mapOf("CMCD-Object" to "br=1,ot=v", "CMCD-Session" to """cid="entry-cid-1",sf=d,sid="s"""")
        ).build()
        val configured = { key: String -> key == "br" || key == "ot" || key == "sf" }
        val resolved = CmcdMediaPlayerEntry.resolve(spec, emptyList(), configured, null, null, null, eventPending = true)
        assertEquals("br=1,ot=v", resolved.httpRequestHeaders["CMCD-Object"])
        assertEquals("""sf=d,sid="s"""", resolved.httpRequestHeaders["CMCD-Session"])
        // the entry that an Event has activated gives its cid
        val active = listOf(ClientDataReportingEntry(contentId = "entry-cid-1", keys = arrayListOf("cid", "sid"), serviceDescriptionId = 1250, activatedByEvent = true))
        val activated = CmcdMediaPlayerEntry.resolve(spec, active, configured, null, null, null, eventPending = true)
        assertEquals("""cid="entry-cid-1",sf=d,sid="s"""", activated.httpRequestHeaders["CMCD-Session"])
        // without a description that an Event can activate the request is left alone, as before
        assertSame(spec, CmcdMediaPlayerEntry.resolve(spec, emptyList(), configured))
    }

    @Test
    fun theCidOfAnEventDescriptionIsAlsoRemovedFromTheQuery() {
        val cmcd = Uri.encode("""br=1,cid="entry-cid-1",ot=v,sid="s"""")
        val spec = DataSpec.Builder().setUri(Uri.parse(dist1 + "seg.m4s?CMCD=$cmcd")).build()
        val resolved = CmcdMediaPlayerEntry.resolve(spec, emptyList(), { it == "br" || it == "ot" }, null, null, null, eventPending = true)
        assertEquals("""br=1,ot=v,sid="s"""", Uri.decode(resolved.uri.encodedQuery!!.substringAfter('=')))
    }
}
