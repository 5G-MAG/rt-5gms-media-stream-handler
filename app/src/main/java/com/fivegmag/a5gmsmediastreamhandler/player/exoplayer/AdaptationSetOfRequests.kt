package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.dash.BaseUrlExclusionList
import androidx.media3.exoplayer.dash.DashChunkSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.dash.PlayerEmsgHandler
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.chunk.Chunk
import androidx.media3.exoplayer.source.chunk.ChunkHolder
import androidx.media3.exoplayer.source.chunk.MediaChunk
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoaderErrorThrower

/**
 * The AdaptationSet@id of the media requests of the DASH player. The data source of Media3 sees only the URL of a request, but the chunk source
 * that creates the request knows the Representation (Chunk.trackFormat) and the AdaptationSets it serves: it records the id of the AdaptationSet
 * that holds the Representation under the URL of the request (the DataSpec that the data source opens has the same URL). Used for the filter
 * adaptationSets of the client data reporting (ISO/IEC 23009-1:2026 table K.7). A request that is not a media request (the manifest) has none.
 */
@UnstableApi
class AdaptationSetOfRequests {
    private val byUri = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > MAX_ENTRIES
    }

    @Synchronized
    fun record(uri: String, adaptationSetId: Long) {
        byUri[uri] = adaptationSetId
    }

    /** The AdaptationSet@id of the media request for [uri]; null if it is none that the chunk source created */
    @Synchronized
    fun idOf(uri: String): Long? = byUri[uri]

    @Synchronized
    fun clear() = byUri.clear()

    companion object {
        private const val MAX_ENTRIES = 256

        /**
         * The AdaptationSet@id of each Representation id among the AdaptationSets [indices] of a Period; a Representation id that two of them
         * have, or an AdaptationSet without an id, gives none (the request cannot be told apart)
         */
        fun adaptationSetOfRepresentations(manifest: DashManifest, periodIndex: Int, indices: IntArray): Map<String, Long> {
            if (periodIndex >= manifest.periodCount) return emptyMap()
            val sets = manifest.getPeriod(periodIndex).adaptationSets
            val result = HashMap<String, Long>()
            val ambiguous = HashSet<String>()
            for (index in indices) {
                val set = sets.getOrNull(index) ?: continue
                for (representation in set.representations) {
                    val id = representation.format.id ?: continue
                    if (set.id == androidx.media3.exoplayer.dash.manifest.AdaptationSet.ID_UNSET || (result[id] != null && result[id] != set.id)) ambiguous.add(id)
                    else result[id] = set.id
                }
            }
            return result - ambiguous
        }
    }
}

/** A chunk source that records the AdaptationSet of every chunk it creates and otherwise is the one it wraps */
@UnstableApi
class AdaptationSetRecordingChunkSource(
    private val delegate: DashChunkSource,
    manifest: DashManifest,
    private val periodIndex: Int,
    private val indices: IntArray,
    private val registry: AdaptationSetOfRequests
) : DashChunkSource {
    @Volatile private var adaptationSets = AdaptationSetOfRequests.adaptationSetOfRepresentations(manifest, periodIndex, indices)

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters) = delegate.getAdjustedSeekPositionUs(positionUs, seekParameters)
    override fun maybeThrowError() = delegate.maybeThrowError()
    override fun getPreferredQueueSize(playbackPositionUs: Long, queue: List<MediaChunk>) = delegate.getPreferredQueueSize(playbackPositionUs, queue)
    override fun shouldCancelLoad(playbackPositionUs: Long, loadingChunk: Chunk, queue: List<MediaChunk>) = delegate.shouldCancelLoad(playbackPositionUs, loadingChunk, queue)
    override fun onChunkLoadCompleted(chunk: Chunk) = delegate.onChunkLoadCompleted(chunk)
    override fun onChunkLoadError(chunk: Chunk, cancelable: Boolean, loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo, policy: LoadErrorHandlingPolicy) =
        delegate.onChunkLoadError(chunk, cancelable, loadErrorInfo, policy)
    override fun release() = delegate.release()
    override fun updateTrackSelection(trackSelection: ExoTrackSelection) = delegate.updateTrackSelection(trackSelection)

    override fun updateManifest(newManifest: DashManifest, newPeriodIndex: Int) {
        adaptationSets = AdaptationSetOfRequests.adaptationSetOfRepresentations(newManifest, newPeriodIndex, indices)
        delegate.updateManifest(newManifest, newPeriodIndex)
    }

    override fun getNextChunk(loadingInfo: LoadingInfo, loadPositionUs: Long, queue: List<MediaChunk>, out: ChunkHolder) {
        delegate.getNextChunk(loadingInfo, loadPositionUs, queue, out)
        val chunk = out.chunk ?: return
        chunk.trackFormat.id?.let { adaptationSets[it] }?.let { registry.record(chunk.dataSpec.uri.toString(), it) }
    }
}

@UnstableApi
class AdaptationSetRecordingChunkSourceFactory(
    private val delegate: DashChunkSource.Factory,
    private val registry: AdaptationSetOfRequests
) : DashChunkSource.Factory {
    override fun createDashChunkSource(
        manifestLoaderErrorThrower: LoaderErrorThrower,
        manifest: DashManifest,
        baseUrlExclusionList: BaseUrlExclusionList,
        periodIndex: Int,
        adaptationSetIndices: IntArray,
        trackSelection: ExoTrackSelection,
        trackType: Int,
        elapsedRealtimeOffsetMs: Long,
        enableEventMessageTrack: Boolean,
        closedCaptionFormats: List<Format>,
        playerTrackEmsgHandler: PlayerEmsgHandler.PlayerTrackEmsgHandler?,
        transferListener: TransferListener?,
        playerId: PlayerId,
        cmcdConfiguration: CmcdConfiguration?
    ): DashChunkSource = AdaptationSetRecordingChunkSource(
        delegate.createDashChunkSource(
            manifestLoaderErrorThrower, manifest, baseUrlExclusionList, periodIndex, adaptationSetIndices, trackSelection, trackType,
            elapsedRealtimeOffsetMs, enableEventMessageTrack, closedCaptionFormats, playerTrackEmsgHandler, transferListener, playerId, cmcdConfiguration
        ),
        manifest, periodIndex, adaptationSetIndices, registry
    )

    override fun setSubtitleParserFactory(subtitleParserFactory: androidx.media3.extractor.text.SubtitleParser.Factory): DashChunkSource.Factory {
        delegate.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }

    override fun experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction: Boolean): DashChunkSource.Factory {
        delegate.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction)
        return this
    }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): DashChunkSource.Factory {
        delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies)
        return this
    }

    override fun getOutputTextFormat(sourceFormat: Format): Format = delegate.getOutputTextFormat(sourceFormat)
}

/**
 * The media source factory of the player: DASH items are made with the chunk source above, everything else (HLS, progressive) by
 * DefaultMediaSourceFactory, which is what the player used before.
 */
@UnstableApi
class RecordingMediaSourceFactory(context: Context, dataSourceFactory: DataSource.Factory, registry: AdaptationSetOfRequests) : MediaSource.Factory {
    private val default = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    private val dash = DashMediaSource.Factory(AdaptationSetRecordingChunkSourceFactory(DefaultDashChunkSource.Factory(dataSourceFactory), registry), dataSourceFactory)

    override fun setCmcdConfigurationFactory(cmcdConfigurationFactory: CmcdConfiguration.Factory): MediaSource.Factory {
        default.setCmcdConfigurationFactory(cmcdConfigurationFactory)
        dash.setCmcdConfigurationFactory(cmcdConfigurationFactory)
        return this
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        default.setDrmSessionManagerProvider(drmSessionManagerProvider)
        dash.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        default.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        dash.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = default.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val configuration = mediaItem.localConfiguration
        val isDash = configuration != null && Util.inferContentTypeForUriAndMimeType(configuration.uri, configuration.mimeType) == C.CONTENT_TYPE_DASH
        return if (isDash) dash.createMediaSource(mediaItem) else default.createMediaSource(mediaItem)
    }
}
