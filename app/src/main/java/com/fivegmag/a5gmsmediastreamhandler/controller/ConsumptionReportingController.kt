package com.fivegmag.a5gmsmediastreamhandler.controller

import android.os.Bundle
import android.os.Message
import android.telephony.*
import androidx.media3.common.util.UnstableApi
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.PropertyFilter
import com.fasterxml.jackson.databind.ser.PropertyWriter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.CellInfoUpdatedEvent
import com.fivegmag.a5gmscommonlibrary.eventbus.PlaybackStateChangedEvent
import com.fivegmag.a5gmscommonlibrary.helpers.PlayerStates
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerMessageTypes
import com.fivegmag.a5gmscommonlibrary.session.PlaybackRequest
import com.fivegmag.a5gmsmediastreamhandler.player.ConsumptionReporter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ConsumptionReporterExoplayer
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.IExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.service.OutgoingMessageHandler
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@UnstableApi
class ConsumptionReportingController(
    private val exoPlayerAdapter: IExoPlayerAdapter,
    private val outgoingMessageHandler: OutgoingMessageHandler
) : IConsumptionReportingController {
    companion object {
        const val TAG = "5GMS-ConsumptionReportingController"
    }

    lateinit var reportingClientId: String
    private val activeConsumptionReporter = mutableListOf<ConsumptionReporter>()
    private var lastConsumptionRequest: ConsumptionRequest? =
        null


    override fun initialize() {
        EventBus.getDefault().register(this)
    }

    // whether media has been played since the last report of the end of consumption
    private var played = false

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    override fun onPlaybackStateChangedEvent(event: PlaybackStateChangedEvent) {
        when (event.playbackState) {
            PlayerStates.READY, PlayerStates.PLAYING, PlayerStates.BUFFERING -> played = true
            // consumption stops when the media ends and when the player is stopped: the final report is sent then
            PlayerStates.ENDED, PlayerStates.IDLE -> if (played || event.playbackState == PlayerStates.ENDED) {
                played = false
                triggerConsumptionReport()
            }
        }
    }

    /**
     * An access network change ends the units on the old access network and starts new ones; if the report carries the access
     * (accessReporting), a report is sent at once (TS 26.510 V19.2.0 clause 5.3.6.1)
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAccessNetworkChangedEvent(event: AccessNetworkChangedEvent) {
        if (lastConsumptionRequest?.accessReporting != true) {
            return
        }
        for (consumptionReporter in activeConsumptionReporter) {
            consumptionReporter.onAccessNetworkChanged()
        }
        triggerConsumptionReport()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @UnstableApi
    override fun onCellInfoUpdatedEvent(event: CellInfoUpdatedEvent) {
        if (lastConsumptionRequest != null && lastConsumptionRequest!!.locationReporting == true) {
            triggerConsumptionReport()
        }
    }

    override fun triggerConsumptionReport() {
        if (lastConsumptionRequest == null) {
            return
        }
        for (consumptionReporter in activeConsumptionReporter) {
            val consumptionReport = consumptionReporter.getConsumptionReport(
                reportingClientId,
                lastConsumptionRequest!!
            )
            sendConsumptionReport(consumptionReport)
            consumptionReporter.resetState()
        }
    }

    private fun sendConsumptionReport(consumptionReport: String) {
        val bundle = Bundle()
        bundle.putString("consumptionReport", consumptionReport)
        outgoingMessageHandler.sendMessageByTypeAndBundle(
            SessionHandlerMessageTypes.CONSUMPTION_REPORT,
            bundle
        )
    }

    @UnstableApi
    override fun handleTriggerPlayback(playbackRequest: PlaybackRequest) {
        resetState()
        setDefaultExoplayerConsumptionReporter()
        setLastConsumptionRequest(
            playbackRequest.consumptionRequest
        )
    }

    private fun setDefaultExoplayerConsumptionReporter() {
        val consumptionReporterExoplayer = ConsumptionReporterExoplayer(exoPlayerAdapter)
        consumptionReporterExoplayer.initialize()
        activeConsumptionReporter.add(consumptionReporterExoplayer)
    }

    private fun setLastConsumptionRequest(consumptionRequest: ConsumptionRequest) {
        lastConsumptionRequest = consumptionRequest
    }

    override fun updateLastConsumptionRequest(msg: Message) {
        val consumptionRequest =
            getConsumptionRequestFromMessage(msg)

        if (consumptionRequest != null) {
            setLastConsumptionRequest(
                consumptionRequest
            )
        }
    }

    private fun getConsumptionRequestFromMessage(msg: Message): ConsumptionRequest? {
        val bundle: Bundle = msg.data
        bundle.classLoader = ConsumptionRequest::class.java.classLoader
        return bundle.getParcelable("consumptionRequest")
    }

    override fun handleGetConsumptionReport(msg: Message) {
        val consumptionRequest =
            getConsumptionRequestFromMessage(msg)

        if (consumptionRequest != null) {
            setLastConsumptionRequest(
                consumptionRequest
            )
        }
        triggerConsumptionReport()
    }

    override fun reset() {
        resetState()
    }

    override fun resetState() {
        lastConsumptionRequest = null
        for (reporter in activeConsumptionReporter) {
            reporter.reset()
        }
        activeConsumptionReporter.clear()
    }
}

class ConsumptionReportingUnitFilter(private val propertiesToIgnore: List<String>) :
    PropertyFilter {
    override fun serializeAsField(
        pojo: Any?,
        gen: JsonGenerator?,
        prov: SerializerProvider?,
        writer: PropertyWriter?
    ) {
        if (include(writer)) {
            writer?.serializeAsField(pojo, gen, prov)
        } else if (!gen?.canOmitFields()!!) {
            writer?.serializeAsOmittedField(pojo, gen, prov)
        }
    }

    override fun serializeAsElement(
        elementValue: Any?,
        gen: JsonGenerator?,
        prov: SerializerProvider?,
        writer: PropertyWriter?
    ) {
        if (include(writer)) {
            writer?.serializeAsElement(elementValue, gen, prov)
        } else if (!gen?.canOmitFields()!!) {
            writer?.serializeAsOmittedField(elementValue, gen, prov)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun depositSchemaProperty(
        writer: PropertyWriter?,
        propertiesNode: ObjectNode?,
        provider: SerializerProvider?
    ) {
    }

    override fun depositSchemaProperty(
        writer: PropertyWriter?,
        objectVisitor: JsonObjectFormatVisitor?,
        provider: SerializerProvider?
    ) {
    }

    private fun include(writer: PropertyWriter?): Boolean {
        return writer?.name !in propertiesToIgnore
    }
}

object ConsumptionReportingFilterProvider {
    fun createFilter(propertiesToIgnore: List<String>): SimpleFilterProvider {
        return SimpleFilterProvider().addFilter(
            "consumptionReportingUnitFilter",
            ConsumptionReportingUnitFilter(propertiesToIgnore)
        )
    }
}
