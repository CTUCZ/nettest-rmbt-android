package at.specure.test

import android.content.Context
import android.telephony.SubscriptionManager
import at.specure.config.Config
import at.specure.data.entity.LoopModeRecord
import at.specure.data.entity.LoopModeState
import at.specure.data.repository.MeasurementRepository
import at.specure.data.repository.TestDataRepository
import at.specure.info.strength.SignalStrengthLiveData
import at.specure.info.strength.SignalStrengthWatcher
import at.specure.location.LocationWatcher
import at.specure.location.cell.CellLocationWatcher
import cz.mroczis.netmonster.core.INetMonster
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StateRecorderLoopConfigTest {

    private lateinit var config: Config
    private lateinit var repository: TestDataRepository
    private lateinit var stateRecorder: StateRecorder

    @Before
    fun setup() {
        config = mockk(relaxed = true)
        repository = mockk(relaxed = true)

        val context = mockk<Context>(relaxed = true)
        val netmonster = mockk<INetMonster>(relaxed = true)
        val locationWatcher = mockk<LocationWatcher>(relaxed = true)
        val signalStrengthLiveData = mockk<SignalStrengthLiveData>(relaxed = true)
        val signalStrengthWatcher = mockk<SignalStrengthWatcher>(relaxed = true)
        val subscriptionManager = mockk<SubscriptionManager>(relaxed = true)
        val cellLocationWatcher = mockk<CellLocationWatcher>(relaxed = true)
        val measurementRepository = mockk<MeasurementRepository>(relaxed = true)

        stateRecorder = StateRecorder(
            context,
            netmonster,
            repository,
            locationWatcher,
            signalStrengthLiveData,
            signalStrengthWatcher,
            config,
            subscriptionManager,
            cellLocationWatcher,
            measurementRepository
        )
    }

    private fun setLoopModeRecord(record: LoopModeRecord?) {
        val field = StateRecorder::class.java.getDeclaredField("_loopModeRecord")
        field.isAccessible = true
        field.set(stateRecorder, record)
    }

    // -- Test 1: Fallback to config when no record exists --

    @Test
    fun loopNumberOfTests_whenNoRecord_fallsBackToConfig() {
        every { config.loopModeNumberOfTests } returns 10

        assertEquals(10, stateRecorder.loopNumberOfTests)
    }

    @Test
    fun loopWaitingTimeMin_whenNoRecord_fallsBackToConfig() {
        every { config.loopModeWaitingTimeMin } returns 5

        assertEquals(5, stateRecorder.loopWaitingTimeMin)
    }

    @Test
    fun loopDistanceMeters_whenNoRecord_fallsBackToConfig() {
        every { config.loopModeDistanceMeters } returns 100

        assertEquals(100, stateRecorder.loopDistanceMeters)
    }

    // -- Test 2: Record values take precedence over config --

    @Test
    fun loopNumberOfTests_whenRecordExists_returnsRecordValues() {
        every { config.loopModeNumberOfTests } returns 10
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = null,
            lastTestUuid = null,
            certMode = false,
            configuredNumberOfTests = 25,
            configuredWaitingTimeMin = 30,
            configuredDistanceMeters = 500
        )
        setLoopModeRecord(record)

        assertEquals(25, stateRecorder.loopNumberOfTests)
    }

    @Test
    fun loopWaitingTimeMin_whenRecordExists_returnsRecordValues() {
        every { config.loopModeWaitingTimeMin } returns 5
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = null,
            lastTestUuid = null,
            certMode = false,
            configuredNumberOfTests = 25,
            configuredWaitingTimeMin = 30,
            configuredDistanceMeters = 500
        )
        setLoopModeRecord(record)

        assertEquals(30, stateRecorder.loopWaitingTimeMin)
    }

    @Test
    fun loopDistanceMeters_whenRecordExists_returnsRecordValues() {
        every { config.loopModeDistanceMeters } returns 100
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = null,
            lastTestUuid = null,
            certMode = false,
            configuredNumberOfTests = 25,
            configuredWaitingTimeMin = 30,
            configuredDistanceMeters = 500
        )
        setLoopModeRecord(record)

        assertEquals(500, stateRecorder.loopDistanceMeters)
    }

    // -- Test 3: initializeLoopModeData with cert mode --

    @Test
    fun initializeLoopModeData_certMode_populatesWithCertConfig() {
        every { config.certModeEnabled } returns true
        every { config.certNumberOfTests } returns 6
        every { config.certWaitingTimeMin } returns 15

        stateRecorder.initializeLoopModeData("remote-loop-uuid")

        val record = stateRecorder.loopModeRecord
        assertEquals(6, record?.configuredNumberOfTests)
        assertEquals(15, record?.configuredWaitingTimeMin)
        assertEquals(Int.MAX_VALUE, record?.configuredDistanceMeters)
        assertTrue(record?.certMode == true)
        verify { repository.saveLoopMode(any()) }
    }

    // -- Test 4: initializeLoopModeData with loop mode (not cert) --

    @Test
    fun initializeLoopModeData_loopMode_populatesWithLoopConfig() {
        every { config.certModeEnabled } returns false
        every { config.loopModeNumberOfTests } returns 10
        every { config.loopModeWaitingTimeMin } returns 5
        every { config.loopModeDistanceMeters } returns 250

        stateRecorder.initializeLoopModeData("remote-loop-uuid")

        val record = stateRecorder.loopModeRecord
        assertEquals(10, record?.configuredNumberOfTests)
        assertEquals(5, record?.configuredWaitingTimeMin)
        assertEquals(250, record?.configuredDistanceMeters)
        assertFalse(record?.certMode == true)
        verify { repository.saveLoopMode(any()) }
    }

    // -- Test 5: onLoopTestFinished uses record's configuredNumberOfTests --

    @Test
    fun onLoopTestFinished_usesRecordNumberOfTests_finished() {
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = "remote-uuid",
            lastTestUuid = "last-test",
            certMode = true,
            testsPerformed = 6,
            configuredNumberOfTests = 6,
            configuredWaitingTimeMin = 15,
            configuredDistanceMeters = 0
        )
        setLoopModeRecord(record)

        stateRecorder.onLoopTestFinished()

        assertEquals(LoopModeState.FINISHED, stateRecorder.loopModeRecord?.status)
        verify { repository.updateLoopMode(any()) }
    }

    @Test
    fun onLoopTestFinished_usesRecordNumberOfTests_notYetFinished() {
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = "remote-uuid",
            lastTestUuid = "last-test",
            certMode = true,
            testsPerformed = 3,
            configuredNumberOfTests = 6,
            configuredWaitingTimeMin = 15,
            configuredDistanceMeters = 0
        )
        setLoopModeRecord(record)

        stateRecorder.onLoopTestFinished()

        assertEquals(LoopModeState.IDLE, stateRecorder.loopModeRecord?.status)
        verify { repository.updateLoopMode(any()) }
    }

    // -- Test 6: Cert mode disables distance trigger --

    @Test
    fun isDistanceTriggerEnabled_certMode_returnsFalse() {
        every { config.loopModeEnabled } returns true
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = "remote-uuid",
            lastTestUuid = "last-test",
            certMode = true,
            testsPerformed = 1,
            configuredNumberOfTests = 6,
            configuredWaitingTimeMin = 11,
            configuredDistanceMeters = 100000,
            status = LoopModeState.IDLE
        )

        assertFalse(
            "Distance trigger must be disabled for cert mode",
            stateRecorder.isDistanceTriggerEnabled(record)
        )
    }

    @Test
    fun isDistanceTriggerEnabled_loopMode_returnsTrue() {
        every { config.loopModeEnabled } returns true
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = "remote-uuid",
            lastTestUuid = "last-test",
            certMode = false,
            testsPerformed = 1,
            configuredNumberOfTests = 10,
            configuredWaitingTimeMin = 5,
            configuredDistanceMeters = 250,
            status = LoopModeState.IDLE
        )

        assertTrue(
            "Distance trigger must be enabled for loop mode",
            stateRecorder.isDistanceTriggerEnabled(record)
        )
    }

    @Test
    fun isDistanceTriggerEnabled_finishedRecord_returnsFalse() {
        every { config.loopModeEnabled } returns true
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = "remote-uuid",
            lastTestUuid = "last-test",
            certMode = false,
            testsPerformed = 10,
            configuredNumberOfTests = 10,
            configuredWaitingTimeMin = 5,
            configuredDistanceMeters = 250,
            status = LoopModeState.FINISHED
        )

        assertFalse(
            "Distance trigger must be disabled for finished loop",
            stateRecorder.isDistanceTriggerEnabled(record)
        )
    }

    @Test
    fun isDistanceTriggerEnabled_loopModeDisabled_returnsFalse() {
        every { config.loopModeEnabled } returns false
        val record = LoopModeRecord(
            localUuid = "test-uuid",
            uuid = null,
            lastTestUuid = null,
            certMode = false,
            status = LoopModeState.IDLE
        )

        assertFalse(
            "Distance trigger must be disabled when loop mode is off",
            stateRecorder.isDistanceTriggerEnabled(record)
        )
    }
}
