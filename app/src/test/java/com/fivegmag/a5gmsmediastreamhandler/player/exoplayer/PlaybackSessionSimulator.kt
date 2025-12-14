package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import android.net.Uri
import android.telephony.CellInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.fivegmag.a5gmscommonlibrary.eventbus.*
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus

/**
 * Simulates a realistic playback session by posting a plausible sequence of
 * EventBus events using descriptive named methods.
 */
object PlaybackSessionSimulator {

    /** Fake EventTime — we only need a relaxed mock since trackers don't inspect it deeply */
    private fun fakeEventTime(): AnalyticsListener.EventTime = mockk(relaxed = true)

    private val bus: EventBus get() = EventBus.getDefault()

    // --- Primitive Event Simulation Methods ---

    fun simulatePlaybackStartTrigger(realtimeMs: Long = 1000L) {
        bus.post(PlaybackStartTriggerEvent(realtimeMs))
    }

    fun simulateFirstMediaSegmentRequested(realtimeMs: Long = 1100L) {
        bus.post(FirstMediaSegmentRequestedEvent(realtimeMs))
    }

    fun simulateFirstFrameRendered(realtimeMs: Long = 1350L) {
        bus.post(FirstFrameRenderedEvent(realtimeMs))
    }

    fun simulatePlaybackStateChanged(state: String) {
        bus.post(PlaybackStateChangedEvent(fakeEventTime(), state))
    }

    fun simulateSeekEvent(oldPositionMs: Long, newPositionMs: Long) {
        bus.post(SeekEvent(fakeEventTime(), oldPositionMs, newPositionMs))
    }

    fun simulatePlaybackSpeedChanged(speed: Float) {
        bus.post(PlaybackSpeedChangedEvent(fakeEventTime(), speed))
    }

    fun simulateVideoSizeChanged(videoWidth: Int, videoHeight: Int, screenWidth: Int, screenHeight: Int) {
        bus.post(VideoSizeChangedEvent(videoWidth, videoHeight, screenWidth, screenHeight))
    }

    fun simulateCellInfoUpdated(cellInfoList: MutableList<CellInfo>) {
        bus.post(CellInfoUpdatedEvent(cellInfoList))
    }

    fun simulateLoadStarted(
        uri: String,
        elapsedRealtimeMs: Long,
        dataType: Int = C.DATA_TYPE_MEDIA,
        loadId: Long = 100L
    ) {
        val loadUri = Uri.parse(uri)
        val loadInfo = LoadEventInfo(loadId, DataSpec(loadUri), loadUri, emptyMap(), elapsedRealtimeMs, 0L, 0L)
        bus.post(LoadStartedEvent(fakeEventTime(), loadInfo, MediaLoadData(dataType)))
    }

    fun simulateLoadCompleted(
        uri: String,
        elapsedRealtimeMs: Long,
        durationMs: Long,
        bytesLoaded: Long,
        dataType: Int,
        loadId: Long
    ) {
        val loadUri = Uri.parse(uri)
        val loadInfo = LoadEventInfo(loadId, DataSpec(loadUri), loadUri, emptyMap(), elapsedRealtimeMs, durationMs, bytesLoaded)
        bus.post(LoadCompletedEvent(fakeEventTime(), loadInfo, MediaLoadData(dataType)))
    }

    fun simulateDownstreamFormatChanged(loadData: MediaLoadData) {
        bus.post(DownstreamFormatChangedEvent(fakeEventTime(), loadData))
    }

    fun simulateBytesTransferred(bytesTransferred: Int) {
        bus.post(BytesTransferredEvent(bytesTransferred))
    }

    // --- Higher-level Logical Simulation Methods ---

    fun simulateManifestLoad(
        uri: String = "http://example.com/stream/manifest.mpd",
        elapsedRealtimeMs: Long = 1050L,
        durationMs: Long = 50L,
        bytesLoaded: Long = 15000L,
        loadId: Long = 1L
    ) {
        simulateLoadStarted(uri, elapsedRealtimeMs - durationMs, C.DATA_TYPE_MANIFEST, loadId)
        simulateBytesTransferred(bytesLoaded.toInt())
        simulateLoadCompleted(uri, elapsedRealtimeMs, durationMs, bytesLoaded, C.DATA_TYPE_MANIFEST, loadId)
    }

    fun simulateMediaSegmentLoad(
        uri: String,
        elapsedRealtimeMs: Long,
        durationMs: Long = 200L,
        bytesLoaded: Long = 500000L,
        loadId: Long = 2L
    ) {
        simulateLoadStarted(uri, elapsedRealtimeMs - durationMs, C.DATA_TYPE_MEDIA, loadId)
        simulateBytesTransferred(bytesLoaded.toInt())
        simulateLoadCompleted(uri, elapsedRealtimeMs, durationMs, bytesLoaded, C.DATA_TYPE_MEDIA, loadId)
    }

    fun simulateFormatChange(
        representationId: String = "video_720p",
        codecs: String = "avc1.4d401f",
        bitrate: Int = 2_500_000,
        width: Int = 1280,
        height: Int = 720
    ) {
        val videoFormat = Format.Builder()
            .setId(representationId)
            .setCodecs(codecs)
            .setContainerMimeType("video/mp4")
            .setPeakBitrate(bitrate)
            .setFrameRate(30.0f)
            .setWidth(width)
            .setHeight(height)
            .build()
        val loadData = MediaLoadData(
            C.DATA_TYPE_MEDIA, C.TRACK_TYPE_VIDEO, videoFormat,
            C.SELECTION_REASON_ADAPTIVE, null, 0L, 5000L
        )
        simulateDownstreamFormatChanged(loadData)
    }

    fun simulateSeek(seekFromMs: Long, seekToMs: Long) {
        simulatePlaybackStateChanged(PlayerStates.BUFFERING)
        simulateSeekEvent(seekFromMs, seekToMs)
        simulatePlaybackStateChanged(PlayerStates.PLAYING)
    }

    fun simulatePause() {
        simulatePlaybackStateChanged(PlayerStates.PAUSED)
    }

    fun simulateResume() {
        simulatePlaybackStateChanged(PlayerStates.PLAYING)
    }

    fun simulatePlaybackEnd() {
        simulatePlaybackStateChanged(PlayerStates.ENDED)
    }

    /**
     * Simulates a full playback session using named event methods.
     */
    fun simulateFullPlaybackSession() {
        // --- Phase 1: Startup ---
        simulatePlaybackStartTrigger(1000L)
        simulateManifestLoad(elapsedRealtimeMs = 1050L)
        simulateFirstMediaSegmentRequested(1100L)
        simulateMediaSegmentLoad("http://example.com/stream/segment_001.m4s", elapsedRealtimeMs = 1100L, loadId = 2L)
        simulateFormatChange()
        simulateFirstFrameRendered(1350L)

        // --- Phase 2: Active Playback ---
        simulatePlaybackStateChanged(PlayerStates.PLAYING)
        simulateMediaSegmentLoad("http://example.com/stream/segment_002.m4s", elapsedRealtimeMs = 2000L, durationMs = 150L, bytesLoaded = 480000L, loadId = 3L)

        // User Interaction Simulation
        Thread.sleep(50)
        simulatePause()
        
        Thread.sleep(50)
        simulateSeek(seekFromMs = 10000L, seekToMs = 30000L)
        
        Thread.sleep(50)
        simulateResume()
        
        Thread.sleep(50)
        simulatePlaybackSpeedChanged(1.5f)
        Thread.sleep(50)

        // More segments load (post-seek)
        simulateMediaSegmentLoad("http://example.com/stream/segment_003.m4s", elapsedRealtimeMs = 4000L, durationMs = 180L, bytesLoaded = 520000L, loadId = 4L)

        // --- Phase 3: Playback Ends ---
        simulatePlaybackEnd()
    }
}

