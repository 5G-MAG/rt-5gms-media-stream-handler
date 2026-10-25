package com.fivegmag.a5gmsmediastreamhandler.controller

/** The MPD of the media being played has been processed by the player (TS 26.501 V19.4.0 clause 5.2.3, step 10) */
class ManifestProcessedEvent(
    val dynamic: Boolean,
    val durationMs: Long?,
    val minBufferTimeMs: Long?,
    val periodCount: Int
)
