package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSpec
import com.fivegmag.a5gmscommonlibrary.helpers.ClientDataReportingModes
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry

/**
 * What the Media Player Entry declares for CMCD, applied to one request (TS 26.512 V19.3.0 clause 10.5.1, ISO/IEC 23009-1:2026 clause K.3.7): it
 * restricts the service locations and request types (here the CMCD object types of the requests, see ClientDataReportingParser) for which CMCD
 * information is reported, gives its own sid and cid, and an explicit set of keys that is reported in addition to
 * the keys of the client metrics reporting configurations. A request that no entry covers gets none of the keys of the entries: only the configured keys and the sid (clause 10.1A).
 * With several entries that cover a request the keys are joined and the first one that has a session or content identifier gives it.
 */
object CmcdMediaPlayerEntry {
    private const val TAG = "CmcdMediaPlayerEntry"
    private const val QUERY_PARAMETER = "CMCD"
    private val HEADERS = listOf("CMCD-Object", "CMCD-Request", "CMCD-Session", "CMCD-Status")

    /** true for the URL query, false for the HTTP request headers, null for a value that is none of the modes */
    fun usesQueryParameters(mode: String?): Boolean? = when (mode) {
        ClientDataReportingModes.CMCD_QUERY -> true
        ClientDataReportingModes.CMCD_HEADERS -> false
        else -> null
    }

    /** The explicit keys of all entries: the player has to produce them so that a request that an entry covers can carry them */
    fun keysOf(entries: List<ClientDataReportingEntry>): Set<String> = entries.flatMap { it.keys.orEmpty() }.toSet()

    /** Whether an entry restricts the media object types: the player then has to produce the ot key to tell them apart */
    fun needsObjectType(entries: List<ClientDataReportingEntry>): Boolean = entries.any { it.objectTypes != null }

    /**
     * The entries that cover a request for [url] of the CMCD object type [objectType] (null when the request carries none) for a media request
     * of the AdaptationSet [adaptationSetId] (null when it is none, or the player cannot tell it: such a request is not covered by an entry that
     * names AdaptationSets, ISO/IEC 23009-1:2026 table K.7)
     */
    fun covering(entries: List<ClientDataReportingEntry>, url: String, objectType: String?, adaptationSetId: Long? = null): List<ClientDataReportingEntry> =
        entries.filter { entry ->
            inServiceLocations(entry, url) && (entry.objectTypes == null || (objectType != null && objectType in entry.objectTypes!!)) &&
                (entry.adaptationSets == null || (adaptationSetId != null && adaptationSetId in entry.adaptationSets!!))
        }

    /**
     * Whether a request is in one of the service locations that the entry names (none named: every request). A request is in the location of
     * the longest BaseURL it starts with, so one under a longer URL of a location that the entry does not name is not in the named one.
     */
    private fun inServiceLocations(entry: ClientDataReportingEntry, url: String): Boolean =
        entry.serviceLocationUrls == null || entry.serviceLocationUrls!!.any { named ->
            url.startsWith(named) && entry.otherServiceLocationUrls.orEmpty().none { it.length > named.length && url.startsWith(it) }
        }

    /** The key and the text after "=" (null for a key without value) of CMCD data, in order; commas inside double quotes do not separate */
    fun parseData(text: String): List<Pair<String, String?>> {
        val result = ArrayList<Pair<String, String?>>()
        var start = 0
        var quoted = false
        for (i in 0..text.length) {
            if (i < text.length && text[i] == '"' && (i == 0 || text[i - 1] != '\\')) quoted = !quoted
            if (i == text.length || (text[i] == ',' && !quoted)) {
                val item = text.substring(start, i).trim()
                if (item.isNotEmpty()) {
                    val equals = item.indexOf('=')
                    result.add(if (equals < 0) item to null else item.substring(0, equals) to item.substring(equals + 1))
                }
                start = i + 1
            }
        }
        return result
    }

    fun formatData(pairs: List<Pair<String, String?>>): String = pairs.joinToString(",") { (key, value) -> if (value == null) key else "$key=$value" }

    /**
     * The pairs of one request after the entries are applied. A key stays if it is the sid or the configurations allow it
     * ([configuredAllowed]), or an entry that covers the request names it. The sid and cid of an entry replace those of the
     * player; a cid that no one gives a value for is dropped. [contentId] is the content identifier of the client metrics reporting configurations.
     * [application] is the cmcdReporting setting of the application (clause 13.2.4): it restricts nothing, adds its keys and, being a
     * setting of the application, its content identifier takes precedence over the one of an entry.
     */
    fun apply(
        pairs: List<Pair<String, String?>>,
        entries: List<ClientDataReportingEntry>,
        url: String,
        configuredAllowed: (String) -> Boolean,
        application: ClientDataReportingEntry? = null,
        contentId: String? = null,
        adaptationSetId: Long? = null
    ): List<Pair<String, String?>> {
        val covering = covering(entries, url, pairs.firstOrNull { it.first == "ot" }?.second, adaptationSetId)
        val inScope = entries.isEmpty() || covering.isNotEmpty()
        val named = keysOf(covering) + application?.keys.orEmpty()
        // the identifier of an entry is for the key that its keys name (ISO/IEC 23009-1 table K.8: sessionID if sid, contentID if cid is present in keys)
        val sessionId = covering.firstOrNull { it.sessionId != null && "sid" in it.keys.orEmpty() }?.sessionId
        // the player may have produced the cid from the content identifier of an entry that does not cover this request: the value is set here
        val cid = application?.contentId ?: covering.firstOrNull { it.contentId != null && "cid" in it.keys.orEmpty() }?.contentId ?: contentId
        val result = pairs.filter { (key, _) -> key == "sid" || configuredAllowed(key) || (inScope && key in named) }.mapNotNull { (key, value) ->
            when {
                key == "sid" && covering.isNotEmpty() && sessionId != null -> key to quote(sessionId)
                key == "cid" -> cid?.let { key to quote(it) }
                else -> key to value
            }
        }
        // one line per request: which entries (by position) cover it and which keys stay
        Log.d(TAG, "${url.substringBefore('?')}: AdaptationSet $adaptationSetId, entries ${entries.indices.filter { entries[it] in covering }} of ${entries.size}, keys ${result.map { it.first }}")
        return result
    }

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** The request with the CMCD data that the player put in its headers or in its URL query reduced as [apply] says; as it is without entries */
    fun resolve(
        dataSpec: DataSpec,
        entries: List<ClientDataReportingEntry>,
        configuredAllowed: (String) -> Boolean,
        application: ClientDataReportingEntry? = null,
        contentId: String? = null,
        adaptationSetId: Long? = null
    ): DataSpec {
        if (entries.isEmpty() && application == null) return dataSpec
        val url = dataSpec.uri.toString()
        val inHeaders = HEADERS.filter { dataSpec.httpRequestHeaders.containsKey(it) }
        if (inHeaders.isNotEmpty()) {
            val byHeader = inHeaders.associateWith { parseData(dataSpec.httpRequestHeaders.getValue(it)) }
            // the entries are applied to the data of all headers together: the ot key of one tells which entries cover the request
            val applied = apply(byHeader.values.flatten(), entries, url, configuredAllowed, application, contentId, adaptationSetId).associateBy { it.first }
            val headers = HashMap(dataSpec.httpRequestHeaders)
            for ((name, pairs) in byHeader) {
                val reduced = pairs.mapNotNull { applied[it.first] }
                if (reduced.isEmpty()) headers.remove(name) else headers[name] = formatData(reduced)
            }
            return dataSpec.withRequestHeaders(headers)
        }
        val query = dataSpec.uri.encodedQuery ?: return dataSpec
        val parts = query.split('&')
        val index = parts.indexOfFirst { it.startsWith("$QUERY_PARAMETER=") }
        if (index < 0) return dataSpec
        val pairs = parseData(Uri.decode(parts[index].substringAfter('=')))
        val reduced = apply(pairs, entries, url, configuredAllowed, application, contentId, adaptationSetId)
        val rebuilt = parts.toMutableList()
        if (reduced.isEmpty()) rebuilt.removeAt(index) else rebuilt[index] = "$QUERY_PARAMETER=" + Uri.encode(formatData(reduced))
        return dataSpec.withUri(dataSpec.uri.buildUpon().encodedQuery(rebuilt.joinToString("&").ifEmpty { null }).build())
    }
}
