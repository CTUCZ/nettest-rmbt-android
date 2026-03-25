package at.rtr.rmbt.android.viewmodel

import at.rtr.rmbt.android.config.AppConfig
import at.rtr.rmbt.android.ui.viewstate.SettingsViewState
import at.specure.data.ClientUUID
import at.specure.data.ControlServerSettings
import at.specure.data.MeasurementServers
import at.specure.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SettingsViewStateTechnicianModeTest {

    private lateinit var appConfig: AppConfig
    private lateinit var state: SettingsViewState

    @Before
    fun setup() {
        appConfig = mockk(relaxed = true)
        val clientUUID = mockk<ClientUUID>(relaxed = true)
        val measurementServers = mockk<MeasurementServers>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val controlServerSettings = mockk<ControlServerSettings>(relaxed = true)

        var technicianBackendValue = ""
        every { appConfig.technicianModeEnabled } returns true
        every { appConfig.technicianSelectedBackend } answers { technicianBackendValue }
        every { appConfig.technicianSelectedBackend = any() } answers { technicianBackendValue = firstArg() }
        every { appConfig.technicianTestControlServerHost } returns "test-server.example.com"
        every { appConfig.technicianTestControlServerPort } returns 443
        every { appConfig.technicianTestControlServerUseSSL } returns true

        state = SettingsViewState(appConfig, clientUUID, measurementServers, controlServerSettings, settingsRepository)
    }

    @Test
    fun settingTestBackend_updatesAppConfig() {
        state.technicianSelectedBackend.set("test")

        verify { appConfig.technicianSelectedBackend = "test" }
    }

    @Test
    fun settingTestBackend_switchesControlServer() {
        state.technicianSelectedBackend.set("test")

        verify { appConfig.controlServerHost = "test-server.example.com" }
        verify { appConfig.controlServerPort = 443 }
        verify { appConfig.controlServerUseSSL = true }
    }

    @Test
    fun settingTestBackend_resetsSelectedServer() {
        state.technicianSelectedBackend.set("test")

        assertNull(state.selectedMeasurementServer.get())
    }

    @Test
    fun settingProductionBackend_restoresDefaultServer() {
        state.technicianSelectedBackend.set("production")

        // Should NOT use test server host
        verify(exactly = 0) { appConfig.controlServerHost = "test-server.example.com" }
    }

    @Test
    fun technicianModeEnabled_reflectsAppConfig() {
        assertEquals(true, state.technicianModeEnabled.get())
    }
}
