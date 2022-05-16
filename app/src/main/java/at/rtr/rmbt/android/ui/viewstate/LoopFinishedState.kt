package at.rtr.rmbt.android.ui.viewstate

import android.os.Bundle
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import at.rtr.rmbt.android.config.AppConfig

private const val KEY_CERT_MODE_ACTIVE = "KEY_CERT_MODE_ACTIVE"

class LoopFinishedState(config: AppConfig) : ViewState {

    val isCertModeActive = ObservableBoolean(config.certModeEnabled)

    override fun onRestoreState(bundle: Bundle?) {
        bundle?.let {
            isCertModeActive.set(bundle.getBoolean(KEY_CERT_MODE_ACTIVE))
        }
    }

    override fun onSaveState(bundle: Bundle?) {
        bundle?.apply {
            putBoolean(KEY_CERT_MODE_ACTIVE, isCertModeActive.get())
        }
    }
}