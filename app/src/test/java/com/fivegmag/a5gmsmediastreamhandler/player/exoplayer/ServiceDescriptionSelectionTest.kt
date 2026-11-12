package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import com.fivegmag.a5gmscommonlibrary.helpers.ClientDataReportingModes
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry
import com.fivegmag.a5gmscommonlibrary.session.MediaPlayerEntryContext
import com.fivegmag.a5gmscommonlibrary.session.ServiceLocationUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// TS 26.512 V19.3.0 clause 13.2.4 (serviceDescriptions[], serviceDescriptionId, clientDataReporting, cmcdReporting) with ISO/IEC 23009-1:2026 tables K.7, K.8, K.10
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServiceDescriptionSelectionTest {
    private val context = MediaPlayerEntryContext(
        arrayListOf(
            ServiceLocationUrls("dist1", arrayListOf("http://as.example/ps/")),
            ServiceLocationUrls("dist2", arrayListOf("http://as.example/ps/dist2/"))
        ),
        arrayListOf("0", "1"),
        arrayListOf(1L, 2L)
    )
    private val entry1 = ClientDataReportingEntry(
        mode = ClientDataReportingModes.CMCD_QUERY, objectTypes = arrayListOf("a", "v", "av", "c", "tt"), contentId = "mpd-cid", keys = arrayListOf("br", "bl"), serviceDescriptionId = 1L
    )
    private val entry2 = entry1.copy(keys = arrayListOf("ot"), serviceDescriptionId = 2L)

    private fun effective(
        entries: List<ClientDataReportingEntry>,
        settings: List<ServiceDescriptionSettings> = emptyList(),
        selected: Long? = null,
        context: MediaPlayerEntryContext? = this.context
    ) = ServiceDescriptionSelection.effective(entries, settings, selected, context)

    @Test
    fun withoutSettingsTheDescriptionsOfTheManifestApply() {
        assertEquals(listOf(entry1, entry2), effective(listOf(entry1, entry2)))
    }

    @Test
    fun serviceDescriptionIdSelectsOneDescriptionOfTheManifest() {
        assertEquals(listOf(entry2), effective(listOf(entry1, entry2), selected = 2L))
        // an id that no description has selects nothing: the descriptions of the manifest stay
        assertEquals(listOf(entry1, entry2), effective(listOf(entry1, entry2), selected = 9L))
        // a description of the manifest that has no entry is selected: nothing is reported
        assertEquals(emptyList<ClientDataReportingEntry>(), effective(listOf(entry1), selected = 2L))
    }

    @Test
    fun aDescriptionWithTheIdOfOneOfTheManifestModifiesItAndTheGivenValuesTakePrecedence() {
        val settings = ServiceDescriptionSettings(
            1L,
            ClientDataReportingSettings(listOf("dist2"), listOf(0L, 1L)),
            CmcdReportingSettings(mode = "header", includeInRequests = listOf("init", "mpd"), keys = listOf("sf", "xx", "sid"), contentId = "api-cid")
        )
        val applied = effective(listOf(entry1, entry2), listOf(settings)).first()
        assertEquals(listOf("http://as.example/ps/dist2/"), applied.serviceLocationUrls)
        assertEquals(listOf("http://as.example/ps/"), applied.otherServiceLocationUrls)
        assertEquals(listOf(0L, 1L), applied.adaptationSets)
        assertEquals(ClientDataReportingModes.CMCD_HEADERS, applied.mode)
        assertEquals(listOf("i", "m"), applied.objectTypes)
        assertEquals(listOf("sf", "sid"), applied.keys)
        assertEquals("api-cid", applied.contentId)
        // the other description is not touched
        assertEquals(entry2, effective(listOf(entry1, entry2), listOf(settings))[1])
    }

    @Test
    fun valuesThatTheSettingsDoNotGiveStayThoseOfTheManifest() {
        val applied = effective(listOf(entry1), listOf(ServiceDescriptionSettings(1L, cmcdReporting = CmcdReportingSettings(keys = listOf("pr")))), context = context).single()
        assertEquals(entry1.copy(keys = arrayListOf("pr")), applied)
        // an invalid content identifier and a mode that is none do not replace the values of the manifest
        val invalid = effective(listOf(entry1), listOf(ServiceDescriptionSettings(1L, cmcdReporting = CmcdReportingSettings(mode = "json", contentId = "a b")))).single()
        assertEquals(entry1, invalid)
        // sessionId of the settings has the value of the media delivery session identifier: nothing to apply
        assertNull(effective(listOf(entry1), listOf(ServiceDescriptionSettings(1L, cmcdReporting = CmcdReportingSettings(sessionId = "s")))).single().sessionId)
    }

    @Test
    fun aDescriptionOfTheSettingsAloneApplyOnlyWhenSelected() {
        val own = ServiceDescriptionSettings(5L, ClientDataReportingSettings(listOf("dist1")), CmcdReportingSettings(keys = listOf("br", "cid"), contentId = "own"))
        assertEquals(listOf(entry1), effective(listOf(entry1), listOf(own)))
        val selected = effective(listOf(entry1), listOf(own), selected = 5L).single()
        // table K.17 defaults: query mode, segment requests
        assertEquals(ClientDataReportingModes.CMCD_QUERY, selected.mode)
        assertEquals(listOf("a", "v", "av", "c", "tt"), selected.objectTypes)
        assertEquals(listOf("http://as.example/ps/"), selected.serviceLocationUrls)
        assertEquals(listOf("http://as.example/ps/dist2/"), selected.otherServiceLocationUrls)
        assertEquals("own", selected.contentId)
        assertEquals(5L, selected.serviceDescriptionId)
    }

    @Test
    fun aDescriptionOfTheSettingsWithoutKeysGivesNoEntry() {
        assertTrue(effective(emptyList(), listOf(ServiceDescriptionSettings(5L, cmcdReporting = CmcdReportingSettings(mode = "header"))), selected = 5L).isEmpty())
        assertTrue(effective(emptyList(), listOf(ServiceDescriptionSettings(5L, cmcdReporting = CmcdReportingSettings(keys = listOf("zz")))), selected = 5L).isEmpty())
        assertTrue(effective(emptyList(), listOf(ServiceDescriptionSettings(5L)), selected = 5L).isEmpty())
    }

    @Test
    fun aDescriptionOfTheManifestWithoutClientDataReportingGetsOneFromTheSettings() {
        // id 2 is a description of the manifest that has no entry: the settings modify it
        val settings = ServiceDescriptionSettings(2L, cmcdReporting = CmcdReportingSettings(keys = listOf("br"), includeInRequests = listOf("*")))
        val applied = effective(listOf(entry1), listOf(settings)).last()
        assertEquals(2L, applied.serviceDescriptionId)
        assertNull(applied.objectTypes)
        assertEquals(listOf("br"), applied.keys)
    }

    // ISO table K.7: the filter by Adaptation Set stays with the entry and is evaluated for each request (CmcdMediaPlayerEntry.covering)
    @Test
    fun theFilterByAdaptationSetStaysWithTheEntry() {
        val restricted = entry1.copy(adaptationSets = arrayListOf(0L))
        assertEquals(listOf(restricted), effective(listOf(restricted)))
        assertEquals(listOf(restricted), effective(listOf(restricted), context = null))
    }

    @Test
    fun theSettingsCanReplaceTheFilterByAdaptationSet() {
        val restricted = entry1.copy(adaptationSets = arrayListOf(0L))
        assertEquals(listOf(0L, 1L), effective(listOf(restricted), listOf(ServiceDescriptionSettings(1L, ClientDataReportingSettings(adaptationSets = listOf(0L, 1L))))).single().adaptationSets)
        assertEquals(listOf(1L), effective(listOf(entry1), listOf(ServiceDescriptionSettings(1L, ClientDataReportingSettings(adaptationSets = listOf(1L))))).single().adaptationSets)
    }

    @Test
    fun anEntryWithoutAValidIdIsNeverSelectedById() {
        val noId = entry1.copy(serviceDescriptionId = null)
        assertEquals(listOf(noId, entry2), effective(listOf(noId, entry2)))
        assertEquals(listOf(entry2), effective(listOf(noId, entry2), selected = 2L))
    }

    // clause K.6.1 of the ISO text: a description with the Scope of the Event is not selected unless an Event activates it
    @Test
    fun theDescriptionsThatAnEventActivatesAreNotAppliedUnlessActivated() {
        val byEvent = entry1.copy(serviceDescriptionId = 3L, activatedByEvent = true)
        assertEquals(listOf(entry1), effective(listOf(entry1, byEvent)))
        assertEquals(listOf(byEvent), ServiceDescriptionSelection.activatable(listOf(entry1, byEvent), emptyList(), null, context))
        // the settings can modify it by the id, and the filter by Adaptation Set applies as for the others
        val settings = listOf(ServiceDescriptionSettings(3L, cmcdReporting = CmcdReportingSettings(keys = listOf("pr"))))
        assertEquals(listOf("pr"), ServiceDescriptionSelection.activatable(listOf(byEvent), settings, null, context).single().keys)
        assertEquals(listOf(0L), ServiceDescriptionSelection.activatable(listOf(byEvent.copy(adaptationSets = arrayListOf(0L))), emptyList(), null, context).single().adaptationSets)
        // a description that an Event activates cannot be selected by serviceDescriptionId, and a valid selection leaves no Event to activate
        assertEquals(listOf(entry1), effective(listOf(entry1, byEvent), selected = 3L))
        assertTrue(ServiceDescriptionSelection.activatable(listOf(byEvent), emptyList(), 1L, context).isEmpty())
        assertEquals(1, ServiceDescriptionSelection.activatable(listOf(byEvent), emptyList(), 99L, context).size)
    }
}
