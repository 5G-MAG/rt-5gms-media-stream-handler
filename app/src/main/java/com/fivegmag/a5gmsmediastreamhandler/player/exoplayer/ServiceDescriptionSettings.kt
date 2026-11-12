package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.util.Log
import com.fivegmag.a5gmscommonlibrary.helpers.ClientDataReportingModes
import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry
import com.fivegmag.a5gmscommonlibrary.session.MediaPlayerEntryContext

/** clientDataReporting of a service description of the settings API (TS 26.512 V19.3.0 clause 13.2.4, ISO/IEC 23009-1:2026 table K.7); null: not set */
data class ClientDataReportingSettings(
    /** The service location labels (clause 5.6.6 of the ISO text) that the reporting is restricted to */
    val serviceLocations: List<String>? = null,
    /** The identifiers of the Adaptation Sets that the reporting is restricted to */
    val adaptationSets: List<Long>? = null
)

/** cmcdReporting of a service description of the settings API (clause 13.2.4, ISO/IEC 23009-1:2026 table K.8); null: not set */
data class CmcdReportingSettings(
    /** The highest CMCD version that the reporting server accepts; the player reports version 1 whatever it says */
    val version: Int? = null,
    /** "query" or "header" */
    val mode: String? = null,
    /** The request types (ISO/IEC 23009-1 table I.3 and I.4) that carry CMCD data */
    val includeInRequests: List<String>? = null,
    val keys: List<String>? = null,
    val contentId: String? = null,
    /** Clause 13.2.4: if given, it has the value of sessionId, the media delivery session identifier, so it changes nothing */
    val sessionId: String? = null
)

/** One entry of serviceDescriptions[] (clause 13.2.4): the part of a service description that concerns client data reporting */
data class ServiceDescriptionSettings(
    val id: Long,
    val clientDataReporting: ClientDataReportingSettings? = null,
    val cmcdReporting: CmcdReportingSettings? = null
)

/**
 * The service descriptions of the Media Player Entry and the settings API (TS 26.512 V19.3.0 clause 13.2.4) for client data reporting:
 * - serviceDescriptions[] defines a service description that the manifest does not have, or modifies one that it has when the id is the same
 *   (a value that the settings give takes precedence over the equivalent value of the Media Player Entry);
 * - serviceDescriptionId selects one service description; without it the player applies the descriptions of the manifest that have no Scope
 *   (with the modifications), and a description that only the settings define is not applied. A description of the manifest with the Scope of
 *   an activation by an Event is not selected here (ISO/IEC 23009-1:2026 clause K.6.1);
 * - an id that no description has selects nothing: the player acts as without serviceDescriptionId;
 * - the entries marked activatedByEvent are not among those of [effective]: [activatable] gives them (with the modifications), to be applied while
 *   an Event activates them (ServiceDescriptionActivation); with a valid serviceDescriptionId none is activatable (the selection of the application
 *   counts, the text does not say what a selection and an Event do together).
 */
object ServiceDescriptionSelection {
    private const val TAG = "ServiceDescription"
    private const val MAX_IDENTIFIER_LENGTH = 64

    // the object types (CTA-5004 table 2, key ot) of the request types of ISO/IEC 23009-1 table I.4 that the player can tell apart (as the Media Session Handler reads them)
    private val REQUEST_TYPES = mapOf("segment" to listOf("a", "v", "av", "c", "tt"), "init" to listOf("i"), "mpd" to listOf("m"))
    private val OTHER_REQUEST_TYPES = setOf("xlink", "callback", "chaining", "fallback", "sbd", "steering", "mpdpatch", "altmpd", "mpdlink")
    private val CMCD_KEYS: Set<String> = CmcdSchemes.KEYS.values.flatten().toSet()

    /** The entries that apply to the playback: those of the manifest and of the settings */
    fun effective(
        entries: List<ClientDataReportingEntry>,
        settings: List<ServiceDescriptionSettings>,
        selectedId: Long?,
        context: MediaPlayerEntryContext?
    ): List<ClientDataReportingEntry> {
        val byId = settings.associateBy { it.id }
        val manifestIds = (context?.serviceDescriptionIds.orEmpty() + entries.filter { !it.activatedByEvent }.mapNotNull { it.serviceDescriptionId }).toSet()
        val selected = selectedId?.takeIf { it in manifestIds || it in byId }
        if (selectedId != null && selected == null) Log.d(TAG, "serviceDescriptionId $selectedId names no service description: not applied")
        val result = ArrayList<ClientDataReportingEntry>()
        for (entry in entries.filter { !it.activatedByEvent }) {
            val id = entry.serviceDescriptionId
            if (selected != null && id != selected) continue
            result.add(if (id != null) byId[id]?.let { override(entry, it, context) } ?: entry else entry)
        }
        // the description that the settings define for an id without an entry: modifies a description of the manifest that has none, or is the selected one
        val withEntry = entries.filter { !it.activatedByEvent }.mapNotNull { it.serviceDescriptionId }.toSet()
        for (id in (if (selected != null) listOf(selected) else byId.keys.filter { it in manifestIds })) {
            if (id !in withEntry) byId[id]?.let { fromSettings(it, context)?.let(result::add) }
        }
        Log.d(TAG, "entries of the manifest ${entries.size}, settings ${settings.size}, selected $selected: applied ${result.size}")
        return result
    }

    /** The entries that apply to a request now: those of the Media Player Entry and the settings, and those of the descriptions that an Event has activated */
    fun current(static: List<ClientDataReportingEntry>, activatable: List<ClientDataReportingEntry>, active: Set<Long>): List<ClientDataReportingEntry> =
        if (activatable.isEmpty()) static else static + activatable.filter { it.serviceDescriptionId in active }

    /** The entries that an Event can activate, with the modifications of the settings; none when the settings select a service description */
    fun activatable(
        entries: List<ClientDataReportingEntry>,
        settings: List<ServiceDescriptionSettings>,
        selectedId: Long?,
        context: MediaPlayerEntryContext?
    ): List<ClientDataReportingEntry> {
        val byId = settings.associateBy { it.id }
        val manifestIds = (context?.serviceDescriptionIds.orEmpty() + entries.filter { !it.activatedByEvent }.mapNotNull { it.serviceDescriptionId }).toSet()
        if (selectedId != null && (selectedId in manifestIds || selectedId in byId)) return emptyList()
        return entries.filter { it.activatedByEvent && it.serviceDescriptionId != null }.map { entry ->
            byId[entry.serviceDescriptionId]?.let { override(entry, it, context) } ?: entry
        }
    }

    private fun override(entry: ClientDataReportingEntry, settings: ServiceDescriptionSettings, context: MediaPlayerEntryContext?): ClientDataReportingEntry {
        var result = entry
        settings.clientDataReporting?.let { reporting ->
            reporting.serviceLocations?.let { labels -> result = withLocations(result, labels, context) }
            reporting.adaptationSets?.let { result = result.copy(adaptationSets = ArrayList(it)) }
        }
        settings.cmcdReporting?.let { cmcd ->
            modeOf(cmcd.mode)?.let { result = result.copy(mode = it) }
            cmcd.includeInRequests?.let { result = result.copy(objectTypes = objectTypesOf(it)) }
            keysOf(cmcd.keys)?.let { result = result.copy(keys = it) }
            identifierOf(cmcd.contentId)?.let { result = result.copy(contentId = it) }
        }
        return result
    }

    // ISO/IEC 23009-1 table K.17: the keys are mandatory, so a description that has none gives no entry; the defaults are query mode and segment requests
    private fun fromSettings(settings: ServiceDescriptionSettings, context: MediaPlayerEntryContext?): ClientDataReportingEntry? {
        val cmcd = settings.cmcdReporting ?: return null
        val keys = keysOf(cmcd.keys) ?: return null
        var entry = ClientDataReportingEntry(
            mode = modeOf(cmcd.mode) ?: ClientDataReportingModes.CMCD_QUERY,
            objectTypes = objectTypesOf(cmcd.includeInRequests ?: listOf("segment")),
            contentId = identifierOf(cmcd.contentId),
            keys = keys,
            serviceDescriptionId = settings.id
        )
        settings.clientDataReporting?.let { reporting ->
            reporting.serviceLocations?.let { entry = withLocations(entry, it, context) }
            reporting.adaptationSets?.let { entry = entry.copy(adaptationSets = ArrayList(it)) }
        }
        return entry
    }

    private fun withLocations(entry: ClientDataReportingEntry, labels: List<String>, context: MediaPlayerEntryContext?): ClientDataReportingEntry {
        val all = context?.serviceLocations.orEmpty()
        val urls = ArrayList(all.filter { it.label in labels }.flatMap { it.urls }.distinct())
        val others = ArrayList(all.flatMap { it.urls }.distinct().filter { it !in urls })
        return entry.copy(serviceLocationUrls = urls, otherServiceLocationUrls = others.takeIf { it.isNotEmpty() })
    }

    private fun modeOf(mode: String?): String? = when (mode) {
        "header" -> ClientDataReportingModes.CMCD_HEADERS
        "query" -> ClientDataReportingModes.CMCD_QUERY
        else -> null
    }

    private fun objectTypesOf(requestTypes: List<String>): ArrayList<String>? {
        val known = requestTypes.filter { it == "*" || it in REQUEST_TYPES || it in OTHER_REQUEST_TYPES }
        return if ("*" in known) null else ArrayList(known.flatMap { REQUEST_TYPES[it].orEmpty() }.distinct())
    }

    // keys that CTA-5004 version 1 does not define are ignored (ISO/IEC 23009-1:2026 table K.8)
    private fun keysOf(keys: List<String>?): ArrayList<String>? = keys?.filter { it in CMCD_KEYS }?.distinct()?.takeIf { it.isNotEmpty() }?.let { ArrayList(it) }

    private fun identifierOf(value: String?): String? =
        value?.takeIf { it.isNotEmpty() && it.length <= MAX_IDENTIFIER_LENGTH && it.none { c -> c.isWhitespace() } }
}
