package at.rtr.rmbt.android.viewmodel

import at.rtr.rmbt.android.config.AppConfig
import javax.inject.Inject

class CertConfigurationViewModel @Inject constructor(val config: AppConfig): BaseViewModel() {

    companion object {
        const val NUMBER_OF_TESTS = 6
        const val WAITING_TIME_MINUTES = 11
        const val DISTANCE_METERS = 100000
    }
}