package com.fivegmag.a5gmsmediastreamhandler.controller

/** A metric of a metrics reporting configuration crossed one of its thresholds (TS 26.510 V19.2.0 clause 5.2.11.1) */
class MetricThresholdCrossedEvent(
    val metricsReportingConfigurationId: String,
    val crossing: ThresholdCrossing
)
