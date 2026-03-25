package at.rtr.rmbt.android.viewmodel

import at.rtr.rmbt.android.config.AppConfig
import javax.inject.Inject

class CertConfigurationViewModel @Inject constructor(val config: AppConfig) : BaseViewModel() {

    val numberOfTests: Int
        get() = config.certNumberOfTests

    val waitingTimeMinutes: Int
        get() = config.certWaitingTimeMin

}
