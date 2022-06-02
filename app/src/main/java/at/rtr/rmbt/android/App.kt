package at.rtr.rmbt.android

import android.content.Context
import at.rtr.rmbt.android.di.DaggerAppComponent
import at.rtr.rmbt.android.di.Injector
import at.specure.config.Config
import at.specure.di.CoreApp
import at.specure.di.CoreComponent
import at.specure.di.CoreInjector
import at.specure.info.Network5GSimulator
import at.specure.worker.WorkLauncher
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.maps.MapsInitializer
import org.acra.ACRA
import org.acra.config.httpSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import java.io.File
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

        Injector.inject(this)
        Network5GSimulator.config = config

        if(!ACRA.isACRASenderServiceProcess())
            WorkLauncher.enqueueSettingsRequest(this)

        // https://issuetracker.google.com/issues/154855417#comment367 Workaround
//        try {
//            val sharedPreferences = getSharedPreferences("google_bug_154855417", Context.MODE_PRIVATE)
//            if (!sharedPreferences.contains("fixed")) {
//                val corruptedZoomTables = File(filesDir, "ZoomTables.data")
//                val corruptedSavedClientParameters = File(filesDir, "SavedClientParameters.data.cs")
//                val corruptedClientParametersData = File(filesDir, "DATA_ServerControlledParametersManager.data.$packageName")
//                val corruptedClientParametersDataV1 = File(filesDir, "DATA_ServerControlledParametersManager.data.v1.$packageName")
//                corruptedZoomTables.delete()
//                corruptedSavedClientParameters.delete()
//                corruptedClientParametersData.delete()
//                corruptedClientParametersDataV1.delete()
//                sharedPreferences.edit().putBoolean("fixed", true).apply()
//            }
//        } catch (exception: Exception) {
//        }
//
//        val config = AGConnectServicesConfig.fromContext(this)
//        MapsInitializer.setApiKey(config.getString("client/api_key"))
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        initAcra {

            reportFormat = StringFormat.JSON
            alsoReportToAndroidFramework = true

//            mailSender {
//                //required
//                mailTo = "furmanekd@gmail.com"
//                //defaults to true
//                reportAsFile = true
//                //defaults to ACRA-report.stacktrace
//                reportFileName = "crash-stacktrace.txt"
//                //defaults to "<applicationId> Crash Report"
//                subject = "NetTest crash report"
//                //defaults to empty
//                body = ""
//            }

            httpSender {
                uri = "http://example.com/report"
                basicAuthLogin = "U3oAC18pTaIPGYUY"
                basicAuthPassword = "***REMOVED***"
                httpMethod = HttpSender.Method.POST
            }

//            dialog {
//                //required
//                text = "Chyba v aplikaci NetTest. Pošlete prosím logy."
//                //optional, enables the dialog title
//                title = "Chyba"
//                //defaults to android.R.string.ok
//                positiveButtonText = "Poslat emailem"
//                //defaults to android.R.string.cancel
//                negativeButtonText = "Zrušit"
//                //optional, enables the comment input
//                commentPrompt = "Komentář"
//            }
        }
    }
}