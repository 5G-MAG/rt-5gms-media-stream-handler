package com.fivegmag.a5gmsmediastreamhandler.player.exoplayer

import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.trackers.VrDeviceInformationTracker
import io.mockk.every
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The VrDeviceInformation metric of TS 26.118 V19.0.0 clause 9.3.4: logged at the start of the session and whenever changed, 0 for what cannot be logged */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VrDeviceInformationTrackerTest {
    private val adapter = mockk<IExoPlayerAdapter>(relaxed = true)
    private val tracker = VrDeviceInformationTracker(adapter, Utils()) { "brand model 15" }

    init {
        every { adapter.eventBus } returns EventBus.getDefault()
        every { adapter.getCurrentPosition() } returns 2500L
        every { adapter.getDisplayRefreshRate() } returns 90f
    }

    @After
    fun tearDown() = tracker.unregister()

    @Test
    fun theStartOfTheSessionLogsWhatThePlatformGivesAndZeroForTheRest() {
        tracker.initialize()
        val entry = tracker.getEntries().single()
        assertEquals("brand model 15", entry.deviceIdentifier)
        assertEquals(90L, entry.refreshRate)
        assertEquals("P0Y0DT0H0M2S", entry.mstart)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L), listOf(
            entry.horizontalResolution, entry.verticalResolution, entry.horizontalFoV, entry.verticalFoV, entry.renderedHorizontalFoV, entry.renderedVerticalFoV
        ))
    }

    @Test
    fun anInformationThatHasNotChangedIsNotLoggedAgainNotEvenAfterAReport() {
        tracker.initialize()
        tracker.getEntries()
        tracker.reset()
        assertEquals(0, tracker.getEntries().size)
    }

    @Test
    fun aChangeIsLoggedAndAnUnknownRefreshRateIsZero() {
        tracker.initialize()
        every { adapter.getDisplayRefreshRate() } returns 0f
        assertEquals(listOf(90L, 0L), tracker.getEntries().map { it.refreshRate })
    }

    @Test
    fun aNewSessionLogsTheInformationAgain() {
        tracker.initialize()
        tracker.reset()
        tracker.initialize()
        assertEquals(1, tracker.getEntries().size)
    }
}
