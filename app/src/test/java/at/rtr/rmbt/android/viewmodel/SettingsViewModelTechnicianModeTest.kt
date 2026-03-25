package at.rtr.rmbt.android.viewmodel

import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.config.AppConfig
import at.specure.data.ClientUUID
import at.specure.data.ControlServerSettings
import at.specure.data.MeasurementServers
import at.specure.data.repository.SettingsRepository
import at.specure.location.LocationWatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsViewModelTechnicianModeTest {

    private lateinit var appConfig: AppConfig
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        appConfig = mockk(relaxed = true)
        val locationWatcher = mockk<LocationWatcher>(relaxed = true)
        val clientUUID = mockk<ClientUUID>(relaxed = true)
        val measurementServers = mockk<MeasurementServers>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val controlServerSettings = mockk<ControlServerSettings>(relaxed = true)

        every { appConfig.secretCodeTechnicianModeOn } returns "tech-activate"
        every { appConfig.secretCodeTechnicianModeOff } returns "tech-deactivate"
        every { appConfig.secretCodeDeveloperModeOn } returns "dev-activate"
        every { appConfig.secretCodeDeveloperModeOff } returns "dev-deactivate"
        every { appConfig.secretCodeCoverageModeOn } returns "cov-activate"
        every { appConfig.secretCodeCoverageModeOff } returns "cov-deactivate"
        every { appConfig.secretCodeAllModesOff } returns "all-off"

        viewModel = SettingsViewModel(
            appConfig, locationWatcher, clientUUID,
            measurementServers, settingsRepository, controlServerSettings
        )
    }

    @Test
    fun technicianActivation_enablesModeAndStoresCode() {
        val result = viewModel.isCodeValid("tech-activate")

        assertEquals(R.string.preferences_technician_mode_available, result)
        verify { appConfig.technicianModeEnabled = true }
        verify { appConfig.technicianActivationCode = "tech-activate" }
    }

    @Test
    fun technicianDeactivation_disablesModeAndResetsAll() {
        val result = viewModel.isCodeValid("tech-deactivate")

        assertEquals(R.string.preferences_technician_mode_disabled, result)
        verify { appConfig.technicianModeEnabled = false }
        verify { appConfig.technicianSelectedBackend = "" }
        verify { appConfig.technicianActivationCode = "" }
    }

    @Test
    fun allModesOff_resetsTechnicianModeAlongWithOthers() {
        val result = viewModel.isCodeValid("all-off")

        assertEquals(R.string.preferences_all_disabled, result)
        verify { appConfig.technicianModeEnabled = false }
        verify { appConfig.technicianSelectedBackend = "" }
        verify { appConfig.technicianActivationCode = "" }
    }

    @Test
    fun invalidCode_returnsTryAgain() {
        val result = viewModel.isCodeValid("wrong-code")

        assertEquals(R.string.preferences_developer_try_again, result)
    }

    @Test
    fun blankCode_returnsTryAgain() {
        val result = viewModel.isCodeValid("   ")

        assertEquals(R.string.preferences_developer_try_again, result)
    }

    @Test
    fun emptyCode_returnsTryAgain() {
        val result = viewModel.isCodeValid("")

        assertEquals(R.string.preferences_developer_try_again, result)
    }

    @Test
    fun technicianActivation_doesNotAffectDeveloperMode() {
        viewModel.isCodeValid("tech-activate")

        verify(exactly = 0) { appConfig.developerModeIsEnabled = any() }
        verify(exactly = 0) { appConfig.coverageModeEnabled = any() }
    }

    @Test
    fun technicianDeactivation_doesNotAffectDeveloperMode() {
        viewModel.isCodeValid("tech-deactivate")

        verify(exactly = 0) { appConfig.developerModeIsEnabled = any() }
        verify(exactly = 0) { appConfig.coverageModeEnabled = any() }
    }
}
