package at.specure.test

import android.content.Context
import android.telephony.SubscriptionManager
import at.rmbt.util.io
import at.specure.config.Config
import at.specure.data.repository.MeasurementRepository
import at.specure.data.repository.TestDataRepository
import at.specure.info.cell.CellNetworkInfo
import at.specure.info.network.DetailedNetworkInfo
import at.specure.info.strength.SignalStrengthInfo
import at.specure.info.strength.SignalStrengthLiveData
import at.specure.info.strength.SignalStrengthWatcher
import at.specure.location.LocationWatcher
import at.specure.location.cell.CellLocationWatcher
import at.specure.util.isFineLocationPermitted
import at.specure.util.isLocationServiceEnabled
import at.specure.util.isReadPhoneStatePermitted
import cz.mroczis.netmonster.core.INetMonster
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationTargetException

/**
 * Regression test for a Play Console crash (v46 / 1.7.9, Galaxy S25 Ultra, Android 16):
 *
 * java.lang.IllegalArgumentException: Illegal Capacity: -1
 *     at java.util.ArrayList.<init>
 *     at at.specure.test.StateRecorder$saveCellInfo$1.invokeSuspend(StateRecorder.kt:401)
 *
 * The 5G cell list and the 5G signal list inside DetailedNetworkInfo are snapshots of
 * concurrently updated lists, so the signal list can momentarily be longer than the cell
 * list. saveCellInfo() pads the signal list with nulls using
 * List(cells.size - signals.size) { null }, which crashes for a negative difference.
 */
class StateRecorderSaveCellInfoTest {

    private lateinit var context: Context
    private lateinit var repository: TestDataRepository
    private lateinit var signalStrengthWatcher: SignalStrengthWatcher
    private lateinit var stateRecorder: StateRecorder

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        signalStrengthWatcher = mockk(relaxed = true)

        stateRecorder = StateRecorder(
            context,
            mockk<INetMonster>(relaxed = true),
            repository,
            mockk<LocationWatcher>(relaxed = true),
            mockk<SignalStrengthLiveData>(relaxed = true),
            signalStrengthWatcher,
            mockk<Config>(relaxed = true),
            mockk<SubscriptionManager>(relaxed = true),
            mockk<CellLocationWatcher>(relaxed = true),
            mockk<MeasurementRepository>(relaxed = true)
        )

        // Run the io { } coroutine builder synchronously so that any exception thrown inside
        // saveCellInfo() propagates to the test instead of being swallowed and only logged.
        mockkStatic("at.rmbt.util.CoroutineExtensionsKt")
        every { io(any()) } answers {
            val block = firstArg<suspend CoroutineScope.() -> Unit>()
            runBlocking { block.invoke(this) }
        }

        // Location + phone state permissions must be granted to enter the cellular branch.
        mockkStatic("at.specure.util.ContextExtensionsKt")
        every { context.isLocationServiceEnabled() } returns true
        every { context.isFineLocationPermitted() } returns true
        every { context.isReadPhoneStatePermitted() } returns true

        setPrivateField("testUUID", "test-uuid")
        setPrivateField("networkInfo", CellNetworkInfo("cell-uuid", "comparison-uuid"))
    }

    @After
    fun tearDown() {
        unmockkStatic("at.rmbt.util.CoroutineExtensionsKt")
        unmockkStatic("at.specure.util.ContextExtensionsKt")
    }

    private fun setPrivateField(name: String, value: Any?) {
        val field = StateRecorder::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(stateRecorder, value)
    }

    private fun invokeSaveCellInfo() {
        val method = StateRecorder::class.java.getDeclaredMethod("saveCellInfo")
        method.isAccessible = true
        try {
            method.invoke(stateRecorder)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    @Test
    fun saveCellInfo_more5GSignalsThan5GCells_savesWithoutCrash() {
        val detailedNetworkInfo = DetailedNetworkInfo(
            networkInfo = CellNetworkInfo("primary-cell-uuid", "primary-comparison-uuid"),
            secondary5GActiveCellNetworks = emptyList(),
            secondary5GActiveSignalStrengthInfos = listOf(mockk<SignalStrengthInfo>()),
            dataSubscriptionId = -1
        )
        every { signalStrengthWatcher.lastDetailedNetworkInfo } returns detailedNetworkInfo

        invokeSaveCellInfo()

        // Reaching the end of saveCellInfo() means the remaining cells were saved too.
        verify { repository.saveCellLocationRecord(any()) }
    }
}