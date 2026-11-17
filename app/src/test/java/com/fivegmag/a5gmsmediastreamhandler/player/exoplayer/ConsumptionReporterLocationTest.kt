package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import androidx.media3.common.Format
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionRequest
import com.fivegmag.a5gmscommonlibrary.eventbus.DownstreamFormatChangedEvent
import com.fivegmag.a5gmscommonlibrary.models.CellIdentifierType
import com.fivegmag.a5gmscommonlibrary.models.TypedLocation
import io.mockk.every
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TS 26.510 V19.2.0 table 9.6.3.2-1: the locations of a unit are a time-ordered list of the locations during its period; clause 5.3.6.1: a location change triggers a report */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConsumptionReporterLocationTest {
    private val bus = EventBus.builder().build()
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true)
    private val reporter: ConsumptionReporterExoplayer
    private var changes = 0
    private val cellA = TypedLocation(CellIdentifierType.NCGI, "00101A")
    private val cellB = TypedLocation(CellIdentifierType.NCGI, "00101B")

    init {
        every { adapter.eventBus } returns bus
        every { adapter.getContentIdentifier() } returns null
        reporter = ConsumptionReporterExoplayer(adapter)
        reporter.initialize()
        reporter.locationChangeListener = { changes++ }
    }

    @After
    fun tearDown() = reporter.reset()

    private fun selected(representationId: String, mimeType: String) {
        val format = Format.Builder().setId(representationId).setContainerMimeType(mimeType).build()
        bus.post(DownstreamFormatChangedEvent(mockk<AnalyticsListener.EventTime>(relaxed = true), MediaLoadData(1, 2, format, 0, null, 0L, 0L)))
    }

    private fun units(): JsonNode = ObjectMapper().readTree(reporter.getConsumptionReport("client", ConsumptionRequest(locationReporting = true))).get("consumptionReportingUnits")

    private fun locations(unit: JsonNode): List<String> = unit.get("locations").map { it.get("location").asText() }

    @Test
    fun theFirstLocationIsNoChangeAndIsAddedToTheUnitsThatAreBeingConsumed() {
        selected("v1", "video/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        assertEquals(0, changes)
        assertEquals(listOf(listOf("00101A")), units().map { locations(it) })
    }

    @Test
    fun aMoveToAnotherCellIsAChangeAndIsAddedAfterTheOlderLocation() {
        selected("v1", "video/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        reporter.onLocationsUpdated(listOf(cellB))
        assertEquals(1, changes)
        assertEquals(listOf(listOf("00101A", "00101B")), units().map { locations(it) })
    }

    @Test
    fun theReportOfAChangeHoldsTheNewLocation() {
        selected("v1", "video/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        var inTheReport: List<String> = emptyList()
        reporter.locationChangeListener = { inTheReport = locations(units()[0]) }
        reporter.onLocationsUpdated(listOf(cellB))
        assertEquals(listOf("00101A", "00101B"), inTheReport)
    }

    @Test
    fun theSameCellAgainIsNoChange() {
        selected("v1", "video/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        reporter.onLocationsUpdated(listOf(cellA))
        assertEquals(0, changes)
        assertEquals(listOf(listOf("00101A")), units().map { locations(it) })
    }

    @Test
    fun theWayBackToACellIsAChangeAndTheListKeepsTheOrder() {
        selected("v1", "video/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        reporter.onLocationsUpdated(listOf(cellB))
        reporter.onLocationsUpdated(listOf(cellA))
        assertEquals(2, changes)
        assertEquals(listOf(listOf("00101A", "00101B", "00101A")), units().map { locations(it) })
    }

    // every unit has its own list: a unit that starts later holds the locations from its own start and not those that the others had before
    @Test
    fun unitsDoNotShareTheirList() {
        selected("v1", "video/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        reporter.onLocationsUpdated(listOf(cellB))
        selected("a1", "audio/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        assertEquals(listOf(listOf("00101A", "00101B", "00101A"), listOf("00101B", "00101A")), units().map { locations(it) })
    }

    @Test
    fun aUnitThatStartsAfterTheFirstLocationHoldsIt() {
        reporter.onLocationsUpdated(listOf(cellA))
        selected("v1", "video/mp4")
        assertEquals(listOf(listOf("00101A")), units().map { locations(it) })
    }

    @Test
    fun anEmptyAnswerOfThePlatformChangesNothing() {
        selected("v1", "video/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        reporter.onLocationsUpdated(emptyList())
        reporter.onLocationsUpdated(listOf(cellA))
        assertEquals(0, changes)
        assertEquals(listOf(listOf("00101A")), units().map { locations(it) })
    }

    @Test
    fun theListsOfTwoUnitsAreTwoObjects() {
        selected("v1", "video/mp4")
        selected("a1", "audio/mp4")
        reporter.onLocationsUpdated(listOf(cellA))
        val unitsOfTheReporter = reporter.javaClass.getDeclaredField("consumptionReportingUnitList").also { it.isAccessible = true }.get(reporter) as List<*>
        val lists = unitsOfTheReporter.map { (it as com.fivegmag.a5gmscommonlibrary.consumptionReporting.ConsumptionReportingUnit).locations }
        assertNotSame(lists[0], lists[1])
    }

    // table 9.6.3.2-1: locations (0..1) is a list of one or more locations (the OpenAPI has minItems 1), so a unit without a known location has none
    @Test
    fun aUnitWithoutAKnownLocationHasNoLocationsProperty() {
        selected("v1", "video/mp4")
        assertFalse(units()[0].has("locations"))
        reporter.onLocationsUpdated(listOf(cellA))
        assertTrue(units()[0].has("locations"))
    }
}
