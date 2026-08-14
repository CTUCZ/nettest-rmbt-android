package at.specure.test

import android.content.Context
import android.telephony.SubscriptionManager
import at.specure.config.Config
import at.specure.data.repository.MeasurementRepository
import at.specure.data.repository.TestDataRepository
import at.specure.info.strength.SignalStrengthLiveData
import at.specure.info.strength.SignalStrengthWatcher
import at.specure.location.LocationWatcher
import at.specure.location.cell.CellLocationWatcher
import cz.mroczis.netmonster.core.INetMonster
import dagger.Component
import dagger.Module
import dagger.Provides
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import javax.inject.Singleton

/**
 * StateRecorder must be a Dagger singleton. In production MeasurementService initializes
 * the LoopModeRecord with the session configuration (cert mode parameters for certified
 * measurements) while TestControllerImpl reads it back when building the LoopModeSettings
 * payload sent to the control server. If every injection point received its own instance,
 * TestControllerImpl's record would stay null forever and its getters would fall back to
 * the user's current loop mode settings, so certified measurements would report the loop
 * mode configuration instead of the fixed cert parameters (6 tests / 11 min).
 *
 * These tests build a real (test-scoped) Dagger graph so they verify the actual scoping
 * behavior, not just the presence of an annotation.
 */
class StateRecorderScopeTest {

    private lateinit var config: Config
    private lateinit var component: StateRecorderScopeTestComponent

    @Before
    fun setup() {
        config = mockk(relaxed = true)
        component = DaggerStateRecorderScopeTestComponent.builder()
            .stateRecorderScopeTestModule(StateRecorderScopeTestModule(config))
            .build()
    }

    @Test
    fun daggerGraph_providesSameStateRecorderInstanceToAllInjectionPoints() {
        val first = component.stateRecorder()
        val second = component.stateRecorder()

        assertSame(
            "StateRecorder is not singleton-scoped — every injection point (MeasurementService, " +
                "TestControllerImpl) receives its own instance, so state initialized by one is " +
                "invisible to the others.",
            first,
            second
        )
    }

    @Test
    fun certParameters_initializedByServiceInstance_areVisibleToControllerInstance() {
        every { config.certModeEnabled } returns true
        every { config.certNumberOfTests } returns 6
        every { config.certWaitingTimeMin } returns 11
        every { config.loopModeNumberOfTests } returns 10
        every { config.loopModeWaitingTimeMin } returns 5

        // instance as injected into MeasurementService — initializes the loop record on test start
        val serviceStateRecorder = component.stateRecorder()
        // instance as injected into TestControllerImpl — reads the values reported to the control server
        val controllerStateRecorder = component.stateRecorder()

        serviceStateRecorder.initializeLoopModeData(null)

        assertEquals(
            "TestControllerImpl would report the loop mode number of tests instead of the cert value",
            6,
            controllerStateRecorder.loopNumberOfTests
        )
        assertEquals(
            "TestControllerImpl would report the loop mode waiting time instead of the cert value",
            11,
            controllerStateRecorder.loopWaitingTimeMin
        )
    }
}

@Module
class StateRecorderScopeTestModule(private val config: Config) {
    @Provides
    fun provideContext(): Context = mockk(relaxed = true)

    @Provides
    fun provideNetmonster(): INetMonster = mockk(relaxed = true)

    @Provides
    fun provideTestDataRepository(): TestDataRepository = mockk(relaxed = true)

    @Provides
    fun provideLocationWatcher(): LocationWatcher = mockk(relaxed = true)

    @Provides
    fun provideSignalStrengthLiveData(): SignalStrengthLiveData = mockk(relaxed = true)

    @Provides
    fun provideSignalStrengthWatcher(): SignalStrengthWatcher = mockk(relaxed = true)

    @Provides
    fun provideConfig(): Config = config

    @Provides
    fun provideSubscriptionManager(): SubscriptionManager = mockk(relaxed = true)

    @Provides
    fun provideCellLocationWatcher(): CellLocationWatcher = mockk(relaxed = true)

    @Provides
    fun provideMeasurementRepository(): MeasurementRepository = mockk(relaxed = true)
}

@Singleton
@Component(modules = [StateRecorderScopeTestModule::class])
interface StateRecorderScopeTestComponent {
    fun stateRecorder(): StateRecorder
}
