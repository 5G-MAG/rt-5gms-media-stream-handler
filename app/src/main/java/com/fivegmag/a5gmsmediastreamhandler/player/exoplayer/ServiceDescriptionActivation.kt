package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.emsg.EventMessage
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The activation of service descriptions by Events (ISO/IEC 23009-1:2026 clause K.6, TS 26.512 V19.3.0 clause 13.2.4): an Event of the scheme
 * urn:mpeg:dash:event:service-description:2024 names the description to activate by its identifier, or carries it. Media3 delivers the Events
 * of an EventStream of the MPD (and the emsg boxes of a Representation that declares an InbandEventStream) as EventMessage metadata at their
 * presentation time. The text defines:
 * - an Event with an Event@id that has not been seen activates its description at the presentation time of the Event (K.6.3); another one with a
 *   known id is ignored;
 * - the duration of the Event is the time after the presentation time in which the description may still be activated (table K.19). Here the
 *   description stays activated for that duration of playback; an Event without a duration (C.TIME_UNSET) leaves it activated.
 * The text does not say what ends an activation otherwise, what several activated descriptions do together (they are all applied), what an Event
 * does that the player reaches after its window, or the encoding of message_data of an emsg box (UTF-8 text with the identifier, or the XML of the
 * ServiceDescription, is read).
 */
class ServiceDescriptionActivation {
    private val seenEvents = HashSet<Long>()
    // the identifier of an activated description and the playback position (ms) at which its duration ends; null: no end
    private val activated = LinkedHashMap<Long, Long?>()

    /** false when the Event is ignored (its id has been seen) */
    @Synchronized
    fun onEvent(eventId: Long, descriptionId: Long, positionMs: Long, durationMs: Long): Boolean {
        if (!seenEvents.add(eventId)) return false
        val end = if (durationMs == C.TIME_UNSET || durationMs < 0) null else positionMs + durationMs
        val previous = activated[descriptionId]
        // a description that is activated again keeps the later end, or none
        activated[descriptionId] = when {
            !activated.containsKey(descriptionId) -> end
            previous == null || end == null -> null
            else -> maxOf(previous, end)
        }
        return true
    }

    /** Ends the activations whose duration has passed at [positionMs]; true if one ended */
    @Synchronized
    fun expire(positionMs: Long): Boolean = activated.entries.removeAll { it.value != null && positionMs >= it.value!! }

    @Synchronized
    fun activeIds(): Set<Long> = activated.keys.toSet()

    /** Whether an activation will end: the player has to look at the position */
    @Synchronized
    fun hasEnd(): Boolean = activated.values.any { it != null }

    @Synchronized
    fun reset() {
        seenEvents.clear()
        activated.clear()
    }

    companion object {
        private const val TAG = "ServiceDescription"

        /**
         * The playback position on a clock that does not run backwards in a live presentation. The position of the player is relative to its window,
         * and the window of a live presentation slides (it stays near the same value while the presentation advances), so the end of an activation,
         * which is a duration after the Event, is measured on the time of the period: the start of the window in the period plus the position.
         */
        fun periodPositionMs(windowPositionMs: Long, windowStartInPeriodMs: Long): Long = windowStartInPeriodMs + windowPositionMs
        const val SCHEME = "urn:mpeg:dash:event:service-description:2024"
        private const val EMSG_MIME_TYPE = "application/x-emsg"

        /**
         * The identifier of the service description that an Event names or carries (table K.19: the message is the identifier; the Event can
         * carry the ServiceDescription element instead, clause K.6.4); null for an Event of another scheme or one that has none
         */
        fun descriptionIdOf(message: EventMessage): Long? {
            if (message.schemeIdUri != SCHEME) return null
            val text = String(message.messageData, Charsets.UTF_8).trim()
            text.toLongOrNull()?.let { return it }
            return try {
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val root: Element = factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))).documentElement
                if ((root.localName ?: root.nodeName) == "ServiceDescription") root.getAttribute("id").toLongOrNull() else null
            } catch (_: Exception) {
                Log.d(TAG, "event ${message.id}: message of the service description scheme is neither an identifier nor a ServiceDescription")
                null
            }
        }

        /**
         * The override that makes the player select the metadata track of the EventStream of the scheme: the player has one renderer for metadata
         * and selects the first group, which may be that of another scheme. null: nothing to override (no such track, or it is selected)
         */
        @UnstableApi
        fun trackOverride(tracks: Tracks): TrackSelectionOverride? {
            val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_METADATA }
            if (groups.any { group -> group.isSelected && (0 until group.length).any { isOurs(group, it) } }) return null
            val ours = groups.firstOrNull { group -> group.isSupported && (0 until group.length).any { isOurs(group, it) } } ?: return null
            return TrackSelectionOverride(ours.mediaTrackGroup, listOf(0))
        }

        // the Format of the EventStream of the MPD has the id schemeIdUri/value (DashMediaPeriod)
        private fun isOurs(group: Tracks.Group, index: Int): Boolean {
            val format = group.getTrackFormat(index)
            return format.sampleMimeType == EMSG_MIME_TYPE && format.id?.startsWith("$SCHEME/") == true
        }
    }
}
