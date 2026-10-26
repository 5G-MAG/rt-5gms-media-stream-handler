package com.fivegmag.a5gmsmediastreamhandler.controller

/** A metric that crossed a threshold: its name, the value that crossed it and the direction */
data class ThresholdCrossing(val metric: String, val value: Double, val positive: Boolean)

/**
 * The crossing thresholds of a metrics reporting configuration (TS 26.510 V19.2.0 clause 5.2.11.1): a metric is reported once when its value
 * exceeds one of the positive thresholds and is not reported again until it falls below that threshold and exceeds it again; for a negative
 * threshold once when the value falls below it, and again only after it exceeded it. The keys of the maps are metric URIs
 * (urn:3GPP:ns:PSS:DASH:QM10#BufferLevel): the part after the '#' without parameters names the metric.
 */
class ThresholdMonitor(
    positive: Map<String, List<Float>>?,
    negative: Map<String, List<Float>>?
) {
    private val positive = normalise(positive)
    private val negative = normalise(negative)
    // per metric and threshold: the value has exceeded the positive threshold / has fallen below the negative threshold since it last crossed back
    private val exceeded = HashMap<Pair<String, Float>, Boolean>()
    private val fallen = HashMap<Pair<String, Float>, Boolean>()

    fun isEmpty(): Boolean = positive.isEmpty() && negative.isEmpty()

    /** The names of the metrics that have thresholds */
    fun metrics(): Set<String> = positive.keys + negative.keys

    /** A new value of a metric: the crossings that it causes */
    fun sample(metric: String, value: Double): List<ThresholdCrossing> {
        val crossings = ArrayList<ThresholdCrossing>()
        positive[metric]?.forEach { t ->
            val key = metric to t
            if (value > t && exceeded[key] != true) {
                exceeded[key] = true
                crossings.add(ThresholdCrossing(metric, value, true))
            } else if (value < t) {
                exceeded[key] = false
            }
        }
        negative[metric]?.forEach { t ->
            val key = metric to t
            if (value < t && fallen[key] != true) {
                fallen[key] = true
                crossings.add(ThresholdCrossing(metric, value, false))
            } else if (value > t) {
                fallen[key] = false
            }
        }
        return crossings
    }

    companion object {
        fun metricName(uri: String): String = uri.substringAfter('#').substringBefore('(')

        private fun normalise(map: Map<String, List<Float>>?): Map<String, List<Float>> =
            map?.entries?.associate { metricName(it.key) to it.value }.orEmpty()
    }
}
