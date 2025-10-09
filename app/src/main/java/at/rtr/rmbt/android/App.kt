package at.rtr.rmbt.android

import android.content.Context
import at.rtr.rmbt.android.di.DaggerAppComponent
import at.rtr.rmbt.android.di.Injector
import at.specure.config.Config
import at.specure.di.CoreApp
import at.specure.di.CoreComponent
import at.specure.di.CoreInjector
import at.specure.worker.WorkLauncher
import javax.inject.Inject

class App : CoreApp() {

    @Inject
    lateinit var config: Config

    override val coreComponent: CoreComponent
        get() = Injector.component

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