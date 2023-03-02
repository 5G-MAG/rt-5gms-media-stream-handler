package com.example.a5gms_mediastreamhandler.models

data class ServiceAccessInformation(
    val provisioningSessionId : String,
    val provisioningSessionType: String?,
    val streamingAccess: StreamingAccess
)

data class StreamingAccess(
    val mediaPlayerEntry: String
)
