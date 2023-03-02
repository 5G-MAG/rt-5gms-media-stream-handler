package com.example.a5gms_mediastreamhandler.models

data class TypedLocation(
    val locationIdentifierType: String,
    val location: String
)

data class EndpointAddress(
    val ipv4Addr: String? = null,
    val ipv6Addr: String? = null,
    val portNumber: UInt
)