package com.example.a5gms_mediastreamhandler.network

data class ConsumptionReporting(
    val mediaPlayerEntry: String,
    val reportingClientId: String,
    val consumptionReportingUnits: Array<ConsumptionReportingUnit>
)

data class ConsumptionReportingUnit(
    val mediaConsumed: String,
    val mediaEndpointAddress: EndpointAddress? = null,
    val startTime: String,
    val duration : Int,
    val locations: Array<TypedLocation>? = null
)

