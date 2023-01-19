package com.example.a5gms_mediastreamhandler.helpers

object StatusInformation {
    const val AVERAGE_THROUGHPUT = "AverageThroughput"
    const val BUFFER_LENGTH = "BufferLength"
    const val LIVE_LATENCY = "liveLatency"
}

object PlayerStates {
    const val IDLE = "IDLE"
    const val BUFFERING = "BUFFERING"
    const val ENDED = "ENDED"
    const val READY = "READY"
    const val PLAYING = "PLAYING"
    const val PAUSED = "PAUSED"
    const val UNKNOWN = "UNKNOWN"
}

object M5Interface {
    //const val ENDPOINT = "http://192.168.178.31:7777/3gpp-m5/v2/"
    const val ENDPOINT = "http://192.168.0.91:3000/3gpp-m5/v2/"
}

object SessionHandlerMessageTypes {
    const val STATUS_MESSAGE = 1
    const val START_PLAYBACK_MESSAGE = 2
    const val SERVICE_ACCESS_INFORMATION_MESSAGE = 3
    const val REGISTER_CLIENT = 4
    const val UNREGISTER_CLIENT = 5
}

object SessionHandlerEvents {
    object Notification {
        const val SESSION_HANDLING_ACTIVATED = "SESSION_HANDLING_ACTIVATED"
        const val SESSION_HANDLING_STOPPED = "SESSION_HANDLING_STOPPED"
    }
}