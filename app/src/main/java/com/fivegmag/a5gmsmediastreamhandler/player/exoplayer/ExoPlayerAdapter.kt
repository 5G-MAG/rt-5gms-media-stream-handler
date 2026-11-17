package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.CmcdConfiguration.MODE_QUERY_PARAMETER
import androidx.media3.exoplayer.upstream.CmcdConfiguration.MODE_REQUEST_HEADER
import com.fivegmag.a5gmscommonlibrary.helpers.CmcdSchemes
import com.fivegmag.a5gmscommonlibrary.qoeMetricsReporting.QoeMetricsRequest
import com.fivegmag.a5gmscommonlibrary.session.ClientDataReportingEntry
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.StatusInformation
import com.fivegmag.a5gmscommonlibrary.helpers.UserAgentTokens
import java.util.UUID

@UnstableApi
class ExoPlayerAdapter: IExoPlayerAdapter {
    override val eventBus: org.greenrobot.eventbus.EventBus = org.greenrobot.eventbus.EventBus.builder().build()
    private lateinit var playerInstance: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var context: Context
    private var activeMediaItem: MediaItem? = null
    private lateinit var activeManifestUrl: String
    private lateinit var playerListener: ExoPlayerListener
    private lateinit var bandwidthMeter: DefaultBandwidthMeter

    companion object {
        private const val SERVICE_DESCRIPTION_TAG = "ServiceDescription"
        private const val ENTRY_TAG = "CmcdMediaPlayerEntry"

        // the transmission mode of CMCD: HTTP request headers by default, the URL query when asked (TS 26.512 V19.3.0 clause 4.9.2)
        fun getCmcdMode(useQueryParameters: Boolean): Int =
            if (useQueryParameters) MODE_QUERY_PARAMETER else MODE_REQUEST_HEADER

        // the transmission mode of one playback (TS 26.512 V19.3.0 clauses 10.5.1 and 12.4): the client-data-reporting-mode of the 3GPP Service URL
        // takes precedence over the Media Player Entry; so does the setting of the application, which the URL has not overridden; the entry is
        // honoured only when neither gave a mode
        fun getCmcdUsesQueryParameters(urlMode: String?, applicationSetting: Boolean?, entries: List<ClientDataReportingEntry>): Boolean =
            CmcdMediaPlayerEntry.usesQueryParameters(urlMode) ?: applicationSetting
                ?: entries.firstNotNullOfOrNull { CmcdMediaPlayerEntry.usesQueryParameters(it.mode) } ?: false

        // the keys of the provisioned schemes, plus sid: every M4d request cites the media delivery session identifier
        // in the CMCD-Session header (TS 26.512 clauses 10.1A and 6.2.3.6), also when no CMCD scheme is provisioned;
        // plus cid whenever a CMCD report is made (clause 10.5.1: the content identifier is in every report, if available)
        fun getCmcdAllowedKeys(
            schemes: Collection<String>,
            sessionId: String?,
            metricsByScheme: Map<String, List<String>> = emptyMap()
        ): Set<String> {
            val keys = HashSet<String>()
            for (scheme in schemes) {
                val classKeys = CmcdSchemes.KEYS[scheme] ?: continue
                keys.addAll(selectCmcdKeys(scheme, classKeys, metricsByScheme[scheme]))
            }
            if (keys.isNotEmpty()) keys.add("cid")
            if (keys.isNotEmpty() || sessionId != null) keys.add("sid")
            return keys
        }

        /** The CMCD keys of one configuration and the part of the session (ms from its start) in which they are reported */
        data class CmcdRule(val configurationId: String, val keys: Set<String>, val startMs: Long, val endMs: Long?, val locationFiltered: Boolean)

        fun getCmcdRules(requests: List<QoeMetricsRequest>): List<CmcdRule> =
            requests.mapNotNull { request ->
                val scheme = request.scheme ?: return@mapNotNull null
                val classKeys = CmcdSchemes.KEYS[scheme] ?: return@mapNotNull null
                val startMs = (request.reportingStartOffset ?: 0L).coerceAtLeast(0L) * 1000L
                CmcdRule(
                    request.metricsReportingConfigurationId,
                    withContentId(selectCmcdKeys(scheme, classKeys, request.metrics)),
                    startMs,
                    request.reportingDuration?.coerceAtLeast(0L)?.let { startMs + it * 1000L },
                    request.locationFiltered
                )
            }

        // a key other than sid is sent only while a configuration that names it has its reporting window open and, if it has a locationFilter,
        // the UE is inside it (not before the Media Session Handler has said so); no rules, no restriction
        fun isCmcdKeyInWindow(rules: List<CmcdRule>, key: String, elapsedMs: Long, located: (String) -> Boolean = { true }): Boolean =
            key == "sid" || rules.isEmpty() ||
                rules.any {
                    key in it.keys && elapsedMs >= it.startMs && (it.endMs == null || elapsedMs <= it.endMs) &&
                        (!it.locationFiltered || located(it.configurationId))
                }

        // clause 10.5.1: the content identifier is in every CMCD report, whatever class the configuration selects (the player leaves it out
        // when there is none); a configuration that selects no key makes no report
        private fun withContentId(keys: Set<String>): Set<String> = if (keys.isEmpty()) keys else keys + "cid"

        // TS 26.512 V19.3.0 clause E.2.5: a term is #<class> or #<class>/<key>, the class being the one of the scheme; no term selects the
        // whole class (clause E.2.1: the terminal path elements are pruned to select all below a common branch)
        private fun selectCmcdKeys(scheme: String, classKeys: Set<String>, terms: List<String>?): Set<String> {
            if (terms.isNullOrEmpty()) return classKeys
            val cmcdClass = scheme.substringAfterLast(':')
            val selected = HashSet<String>()
            for (term in terms) {
                val path = term.substringAfter('#', "")
                if (path == cmcdClass) selected.addAll(classKeys)
                else if (path.startsWith("$cmcdClass/") && path.substringAfter('/') in classKeys) selected.add(path.substringAfter('/'))
            }
            return selected
        }
    }

    // the CMCD keys that the provisioned schemes allow, the sid and cid of the media delivery session and the transmission mode
    @Volatile private var cmcdAllowedKeys: Set<String> = emptySet()
    @Volatile private var cmcdSessionId: String? = null
    @Volatile private var cmcdContentId: String? = null
    @Volatile private var cmcdApplicationSetting: Boolean? = null
    @Volatile private var cmcdUrlMode: String? = null
    @Volatile private var cmcdEntries: List<ClientDataReportingEntry> = emptyList()
    @Volatile private var cmcdApplicationReporting: ClientDataReportingEntry? = null
    // the entries that an Event can activate (clause K.6 of the ISO text) and what has been activated so far
    @Volatile private var cmcdEventEntries: List<ClientDataReportingEntry> = emptyList()
    private val serviceDescriptionActivation = ServiceDescriptionActivation()
    private val adaptationSetOfRequests = AdaptationSetOfRequests()
    @Volatile private var serviceDescriptions: List<ServiceDescriptionSettings> = emptyList()
    @Volatile private var serviceDescriptionId: Long? = null
    @Volatile private var cmcdRules: List<CmcdRule> = emptyList()
    @Volatile private var cmcdStartMs = 0L
    private val cmcdLocated = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    override fun configureCmcd(schemes: Collection<String>, sessionId: String?, contentId: String?, metricsByScheme: Map<String, List<String>>) {
        cmcdAllowedKeys = getCmcdAllowedKeys(schemes, sessionId, metricsByScheme)
        cmcdSessionId = sessionId
        cmcdContentId = contentId
    }

    override fun updateCmcdConfiguration(schemes: Collection<String>, metricsByScheme: Map<String, List<String>>, requests: List<QoeMetricsRequest>) {
        cmcdAllowedKeys = getCmcdAllowedKeys(schemes, cmcdSessionId, metricsByScheme)
        cmcdRules = getCmcdRules(requests)
    }

    override fun configureCmcdReportingWindows(requests: List<QoeMetricsRequest>) {
        cmcdRules = getCmcdRules(requests)
        cmcdLocated.clear()
        cmcdStartMs = android.os.SystemClock.elapsedRealtime()
    }

    override fun setCmcdLocated(configurationId: String, located: Boolean) {
        cmcdLocated[configurationId] = located
    }

    override fun setCmcdUseQueryParameters(useQueryParameters: Boolean) {
        cmcdApplicationSetting = useQueryParameters
    }

    override fun configureCmcdMediaPlayerEntry(
        entries: List<ClientDataReportingEntry>,
        clientDataReportingMode: String?,
        context: com.fivegmag.a5gmscommonlibrary.session.MediaPlayerEntryContext?
    ) {
        cmcdEntries = ServiceDescriptionSelection.effective(entries, serviceDescriptions, serviceDescriptionId, context)
        cmcdEventEntries = ServiceDescriptionSelection.activatable(entries, serviceDescriptions, serviceDescriptionId, context)
        serviceDescriptionActivation.reset()
        cmcdUrlMode = clientDataReportingMode
    }

    override fun setServiceDescriptions(descriptions: List<ServiceDescriptionSettings>) {
        serviceDescriptions = descriptions
    }

    override fun setServiceDescriptionId(id: Long?) {
        serviceDescriptionId = id
    }

    override fun setCmcdReporting(keys: List<String>?, contentId: String?, useQueryParameters: Boolean?) {
        cmcdApplicationReporting = if (keys == null && contentId == null) null else ClientDataReportingEntry(keys = keys?.let { ArrayList(it) }, contentId = contentId)
        useQueryParameters?.let { cmcdApplicationSetting = it }
    }

    // the content identifier of the configuration, or else the one that the application (cmcdReporting) or a Media Player Entry declares (clause 10.5.1)
    private fun cmcdEffectiveContentId(): String? =
        cmcdContentId ?: cmcdApplicationReporting?.contentId ?: (cmcdEntries + cmcdEventEntries).firstNotNullOfOrNull { it.contentId }

    /** The entries that apply to a request now: those of the Media Player Entry and the settings, and those that an Event has activated */
    private fun cmcdCurrentEntries(): List<ClientDataReportingEntry> {
        val events = cmcdEventEntries
        return if (events.isEmpty()) cmcdEntries else ServiceDescriptionSelection.current(cmcdEntries, events, serviceDescriptionActivation.activeIds())
    }

    override fun getContentIdentifier(): String? = cmcdEffectiveContentId()

    // a key that the client metrics reporting configurations allow now (their classes, reporting windows and locations)
    private fun isCmcdKeyConfigured(key: String): Boolean {
        // the content identifier of an entry reaches the request only through the entries that cover it (CmcdMediaPlayerEntry.apply)
        if (key == "cid" && (cmcdContentId ?: cmcdApplicationReporting?.contentId) == null) return false
        return cmcdAllowedKeys.contains(key) &&
            isCmcdKeyInWindow(cmcdRules, key, android.os.SystemClock.elapsedRealtime() - cmcdStartMs) { cmcdLocated[it] == true }
    }


    override fun initialize(
        exoPlayerView: PlayerView,
        context: Context
    ) {
        val defaultUserAgent = Util.getUserAgent(context, "A5GMSMediaStreamHandler")
        val deviceName = android.os.Build.MODEL
        val osVersion = android.os.Build.VERSION.RELEASE
        val modifiedUserAgent =
            "${UserAgentTokens.FIVE_G_MS_MEDIA_STREAM_HANDLER} $defaultUserAgent (Android $osVersion; $deviceName)"
        val httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(modifiedUserAgent)

        // transferListenerRef is captured by the factory lambda and set after playerListener is created.
        // DataSources are created lazily when media loads, so the listener is guaranteed to be set by then.
        var transferListenerRef: ExoPlayerListener? = null
        val listenedDataSourceFactory =
            DataSource.Factory {
                val dataSource = httpDataSourceFactory.createDataSource()
                transferListenerRef?.let { dataSource.addTransferListener(it) }
                dataSource
            }
        // the Media Player Entry restricts the CMCD data per request (clause 10.5.1): it is reduced here, where the request is known
        val dataSourceFactory = ResolvingDataSource.Factory(listenedDataSourceFactory, object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec) =
                CmcdMediaPlayerEntry.resolve(
                    dataSpec, cmcdCurrentEntries(), { isCmcdKeyConfigured(it) }, cmcdApplicationReporting, cmcdContentId, adaptationSetOfRequests.idOf(dataSpec.uri.toString()),
                    cmcdEventEntries.isNotEmpty()
                )
        })

        val cmcdConfigurationFactory = object : CmcdConfiguration.Factory {
            override fun createCmcdConfiguration(mediaItem: MediaItem): CmcdConfiguration {
                val cmcdConfig = object : CmcdConfiguration.RequestConfig {
                    // the keys that the player produces: those of the configurations and those that a Media Player Entry names (ot too, to tell the
                    // object types apart); the entries are applied to each request in the data source above
                    override fun isKeyAllowed(key: String): Boolean {
                        if (isCmcdKeyConfigured(key)) return true
                        // the keys of the descriptions that an Event can activate are produced too, so that a request has them once it is activated
                        val possible = cmcdEntries + cmcdEventEntries
                        return key in CmcdMediaPlayerEntry.keysOf(possible) || key in cmcdApplicationReporting?.keys.orEmpty() ||
                            (key == "ot" && CmcdMediaPlayerEntry.needsObjectType(possible))
                    }

                    override fun getRequestedMaximumThroughputKbps(throughputKbps: Int): Int {
                        return 5 * throughputKbps
                    }
                }

                // the media delivery session identifier is the CMCD sid (clause 10.5.1); the Media Session Handler gives it before playback
                val sessionId = cmcdSessionId ?: UUID.randomUUID().toString()
                val mode = getCmcdMode(getCmcdUsesQueryParameters(cmcdUrlMode, cmcdApplicationSetting, cmcdEntries))

                return CmcdConfiguration(sessionId, cmcdEffectiveContentId(), cmcdConfig, mode)
            }
        }

        // the media source factory records the AdaptationSet of each media request (the filter adaptationSets of the client data reporting)
        playerInstance = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                RecordingMediaSourceFactory(context, dataSourceFactory, adaptationSetOfRequests)
                    .setCmcdConfigurationFactory(cmcdConfigurationFactory)
            )
            .build()

        playerInstance.addAnalyticsListener(EventLogger())
        playerInstance.addListener(serviceDescriptionEventListener())
        bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        this.context = context
        playerView = exoPlayerView
        playerView.player = playerInstance
        playerListener =
            ExoPlayerListener(playerInstance, playerView, eventBus)
        playerInstance.addAnalyticsListener(playerListener)
        transferListenerRef = playerListener
    }

    // The playback position on the time of the period (see ServiceDescriptionActivation.periodPositionMs): the window of a live presentation slides
    private fun periodPositionMs(): Long {
        val timeline = playerInstance.currentTimeline
        if (timeline.isEmpty) return playerInstance.currentPosition
        val window = timeline.getWindow(playerInstance.currentMediaItemIndex, androidx.media3.common.Timeline.Window())
        return ServiceDescriptionActivation.periodPositionMs(playerInstance.currentPosition, window.positionInFirstPeriodMs)
    }

    // clause K.6 of ISO/IEC 23009-1:2026: Media3 hands the Events of the scheme to the player listener at their presentation time
    private fun serviceDescriptionEventListener() = object : androidx.media3.common.Player.Listener {
        private val handler = android.os.Handler(playerInstance.applicationLooper)
        private val expiry = object : Runnable {
            override fun run() {
                val position = periodPositionMs()
                val before = serviceDescriptionActivation.activeIds()
                if (serviceDescriptionActivation.expire(position)) {
                    Log.d(SERVICE_DESCRIPTION_TAG, "activation ended at $position ms")
                    Log.d(ENTRY_TAG, "activation ended at period position $position ms: ${before - serviceDescriptionActivation.activeIds()}, active now ${serviceDescriptionActivation.activeIds()}")
                }
                if (serviceDescriptionActivation.hasEnd()) handler.postDelayed(this, 500)
            }
        }

        override fun onMetadata(metadata: androidx.media3.common.Metadata) {
            for (i in 0 until metadata.length()) {
                val message = metadata.get(i) as? androidx.media3.extractor.metadata.emsg.EventMessage ?: continue
                Log.d(ENTRY_TAG, "onMetadata: scheme ${message.schemeIdUri}, value '${message.value}', id ${message.id}, presentation time ${metadata.presentationTimeUs} us, duration ${message.durationMs} ms")
                val id = ServiceDescriptionActivation.descriptionIdOf(message) ?: continue
                val position = periodPositionMs()
                val activated = serviceDescriptionActivation.onEvent(message.id, id, position, message.durationMs)
                Log.d(SERVICE_DESCRIPTION_TAG, "event ${message.id} at $position ms: description $id " + if (activated) "activated for ${message.durationMs} ms" else "ignored, the event id is known")
                if (activated) Log.d(ENTRY_TAG, "activation of description $id started at period position $position ms for ${message.durationMs} ms, active ${serviceDescriptionActivation.activeIds()}")
                if (activated && serviceDescriptionActivation.hasEnd()) {
                    handler.removeCallbacks(expiry)
                    handler.postDelayed(expiry, 500)
                }
            }
        }

        // the player has one renderer for metadata: it must select the EventStream of the scheme, not the first one
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            Log.d(ENTRY_TAG, "tracks changed, metadata groups: " + tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_METADATA }.map { group ->
                (0 until group.length).map { "id ${group.getTrackFormat(it).id}, selected ${group.isTrackSelected(it)}, supported ${group.isTrackSupported(it)}" }
            })
            if (cmcdEventEntries.isEmpty()) return
            ServiceDescriptionActivation.trackOverride(tracks)?.let {
                Log.d(SERVICE_DESCRIPTION_TAG, "selecting the metadata track of the service description EventStream")
                playerInstance.trackSelectionParameters = playerInstance.trackSelectionParameters.buildUpon().setOverrideForType(it).build()
            }
        }
    }

    override fun attach(url: String, contentType: String) {
        // Reset listener state for new presentation
        playerListener.newPlaybackSession()
        
        val mediaItem: MediaItem
        when (contentType) {
            ContentTypes.DASH -> {
                mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
            }

            ContentTypes.HLS -> {
                mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            }

            else -> {
                mediaItem = MediaItem.fromUri(url)
            }
        }

        playerInstance.setMediaItem(mediaItem)
        activeMediaItem = mediaItem
        activeManifestUrl = url
    }

    override fun hasActiveMediaItem() : Boolean {
        return activeMediaItem != null
    }

    override fun getCurrentManifestUri(): String {
        return activeManifestUrl
    }

    override fun getCurrentManifestUrl(): String {
        return playerInstance.currentMediaItem?.localConfiguration?.uri.toString()
    }

    override fun preload() {
        playerInstance.prepare()
    }

    override fun play() {
        playerInstance.play()
    }

    override fun pause() {
        playerInstance.pause()
    }

    // TS 26.512 V19.3.0 clause 13.2.3.7: the earliest media time is the mediaTime (milliseconds) and playback goes on from there
    override fun seek(time: Long) {
        playerInstance.seekTo(time)
        playerInstance.play()
    }

    override fun stop() {
        playerInstance.stop()
    }

    // clause 13.2.3.8: playback ends, its requests and the current MPD are removed and the Media Player is INITIALIZED again
    override fun reset() {
        playerInstance.stop()
        playerInstance.clearMediaItems()
        activeMediaItem = null
    }

    override fun destroy() {
        playerInstance.release()
    }

    override fun getPlayerInstance(): ExoPlayer {
        return playerInstance
    }

    override fun getPlaybackState(): Int {
        return playerInstance.playbackState
    }

    override fun getCurrentPosition(): Long {
        return playerInstance.currentPosition
    }

    override fun getBufferLength(): Long {
        return playerInstance.totalBufferedDuration
    }
    override fun getAverageThroughput(): Long {
        return bandwidthMeter.bitrateEstimate
    }

    private fun getLiveLatency(): Long {
        return playerInstance.currentLiveOffset
    }

    override fun getCurrentPeriodId(): String {
        try {
            val manifest = playerInstance.currentManifest
            if (manifest is DashManifest) {
                val periodId = manifest.getPeriod(playerInstance.currentPeriodIndex).id
                if (periodId != null) {
                    return periodId
                }
            }
        } catch (e: Exception) {
            // Manifest not loaded yet or period index out of bounds
            return ""
        }
        return ""
    }

    override fun getStatusInformation(status: String): Any? {
        when (status) {
            StatusInformation.AVERAGE_THROUGHPUT -> return getAverageThroughput()
            StatusInformation.BUFFER_LENGTH -> return getBufferLength()
            StatusInformation.LIVE_LATENCY -> return getLiveLatency()
            else -> {
                return null
            }
        }
    }

    override fun getPlayerState(): String {
        val state: String?
        if (playerInstance.isPlaying) {
            state = PlayerStates.PLAYING
        } else if (playerInstance.playbackState == Player.STATE_READY && !playerInstance.playWhenReady) {
            state = PlayerStates.PAUSED
        } else {
            state = mapStateToConstant(playerInstance.playbackState)
        }

        return state
    }

    override fun getVideoWidth(): Int {
        // Return displayed video width (rendered size), not encoded resolution
        return playerView.width
    }

    override fun getVideoHeight(): Int {
        // Return displayed video height (rendered size), not encoded resolution
        return playerView.height
    }

    override fun getScreenWidth(): Int {
        return context.resources.displayMetrics.widthPixels
    }

    override fun getScreenHeight(): Int {
        return context.resources.displayMetrics.heightPixels
    }

    override fun getPixelDensityX(): Float {
        return context.resources.displayMetrics.xdpi
    }

    override fun getPixelDensityY(): Float {
        return context.resources.displayMetrics.ydpi
    }
}