package at.rtr.rmbt.android

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import at.rtr.rmbt.android.di.DaggerAppComponent
import at.rtr.rmbt.android.di.Injector
import at.specure.config.Config
import at.specure.di.CoreApp
import at.specure.di.CoreComponent
import at.specure.di.CoreInjector
import at.specure.worker.WorkLauncher
import javax.inject.Inject
import androidx.core.content.edit

class App : CoreApp(), Configuration.Provider {

    @Inject
    lateinit var config: Config

    override val coreComponent: CoreComponent
        get() = Injector.component


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        setupAnalyticsEnvironment(this)
        setupBuildEnvironment(this)

        Injector.component = DaggerAppComponent.builder()
            .context(this)
            .build()

        CoreInjector.component = Injector.component


            WorkLauncher.enqueueSettingsRequest(this)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
    }

}