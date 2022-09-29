package at.rtr.rmbt.android.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.checkSelfPermission
import at.rmbt.client.control.IpProtocol
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.FragmentHomeBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.ui.activity.*
import at.rtr.rmbt.android.ui.dialog.*
import at.rtr.rmbt.android.util.InfoWindowStatus
import at.rtr.rmbt.android.util.ToolbarTheme
import at.rtr.rmbt.android.util.changeStatusBarColor
import at.rtr.rmbt.android.util.listen
import at.rtr.rmbt.android.viewmodel.HomeViewModel
import at.rtr.rmbt.android.viewmodel.MeasurementViewModel
import at.specure.data.entity.LoopModeState
import at.specure.info.TransportType
import at.specure.info.network.WifiNetworkInfo
import at.specure.location.LocationState
import at.specure.measurement.MeasurementService
import at.specure.util.toast
import timber.log.Timber
import java.lang.IndexOutOfBoundsException
import cz.mroczis.netmonster.core.model.connection.SecondaryConnection

class HomeFragment : BaseFragment(), SimpleDialog.Callback {

    private val homeViewModel: HomeViewModel by viewModelLazy()
    private val binding: FragmentHomeBinding by bindingLazy()

    private val measurementViewModel: MeasurementViewModel by viewModelLazy()

    override val layoutResId = R.layout.fragment_home

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //return to previous screen orientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        binding.state = homeViewModel.state

        homeViewModel.isConnected.listen(this) {
            activity?.window?.changeStatusBarColor(if (it) ToolbarTheme.BLUE else ToolbarTheme.GRAY)
        }

        homeViewModel.signalStrengthLiveData.listen(this) {
            homeViewModel.state.signalStrength.set(it?.signalStrengthInfo)
            homeViewModel.state.activeNetworkInfo.set(it)
            homeViewModel.state.secondary5GActiveNetworkInfo.set(
                if (it?.secondary5GActiveCellNetworks?.isNotEmpty() == true) {
                    it.secondary5GActiveCellNetworks?.get(0)
                } else {
                    null
                }
            )
            homeViewModel.state.secondary5GSignalStrength.set(
                if (it?.secondary5GActiveSignalStrengthInfos?.isNotEmpty() == true) {
                    it.secondary5GActiveSignalStrengthInfos?.get(0)
                } else {
                    null
                }
            )
            if (it?.networkInfo is WifiNetworkInfo) {
                (it.networkInfo as WifiNetworkInfo).signal = it.signalStrengthInfo?.value
            }
        }

        homeViewModel.locationStateLiveData.listen(this) {
            homeViewModel.state.isLocationEnabled.set(it)
        }

        homeViewModel.ipV4ChangeLiveData.listen(this) {
            homeViewModel.state.ipV4Info.set(it)
        }

        homeViewModel.ipV6ChangeLiveData.listen(this) {
            homeViewModel.state.ipV6Info.set(it)
        }

        binding.btnSetting?.setOnClickListener {
            startActivity(Intent(requireContext(), PreferenceActivity::class.java))
        }
        binding.tvInfo?.setOnClickListener {
            homeViewModel.state.infoWindowStatus.set(InfoWindowStatus.GONE)
        }

        binding.btnIpv4?.setOnClickListener {
            IpInfoDialog.instance(IpProtocol.V4).show(activity)
        }

        binding.btnIpv6?.setOnClickListener {
            IpInfoDialog.instance(IpProtocol.V6).show(activity)
        }

        binding.btnLocation?.setOnClickListener {

            context?.let {
                homeViewModel.state.isLocationEnabled.get()?.let {
                    when (it) {
                        LocationState.ENABLED -> LocationInfoDialog.instance().show(activity)
                        LocationState.DISABLED_APP -> OpenLocationPermissionDialog.instance().show(activity)
                        LocationState.DISABLED_DEVICE -> OpenGpsSettingDialog.instance().show(activity)
                    }
                }
            }
        }

        binding.ivSignalLevel.setOnClickListener {
            if (homeViewModel.isConnected.value == true) {
                if (!homeViewModel.clientUUID.value.isNullOrEmpty()) {
                    if (homeViewModel.state.isCertModeActive.get()) {
                        if(LocationState.DISABLED_DEVICE == homeViewModel.state.isLocationEnabled.get()) {
                            OpenGpsSettingDialog.instance().show(activity)
                        }else if(isPermissionsForCertMeasuringGranted()) {
                            val networkType = homeViewModel.activeNetworkLiveData.activeNetworkWatcher.currentNetworkInfo?.type
                            if(networkType != TransportType.CELLULAR) {
                                SimpleDialog.Builder()
                                    .titleText(R.string.unsupported_network_title)
                                    .messageText(R.string.unsupported_network_text)
                                    .positiveText(android.R.string.ok)
                                    .cancelable(true)
                                    .show(childFragmentManager, CODE_NO_CELLULAR_NETWORK)
                            } else {
                                startCertMeasurement()
                            }

                        } else {
                            Timber.d("Cert measurement requires all permissions, requiring permissions...")
                            requirePermissions(true)
                        }

                    } else if (homeViewModel.state.isLoopModeActive.get()) {
                        LoopConfigurationActivity.start(requireContext())
                    } else {
                        MeasurementService.startTests(requireContext())
                        MeasurementActivity.start(requireContext())
                    }
                } else {
                    MessageDialog.instance(R.string.client_not_registered).show(activity)
                }
            } else {
                MessageDialog.instance(R.string.home_no_internet_connection).show(activity)
            }
        }

        binding.btnUpload?.setOnClickListener {
            homeViewModel.activeSignalMeasurementLiveData.value?.let { active ->
                if (!active) {
                    val intent = SignalMeasurementTermsActivity.start(requireContext())
                    startActivityForResult(intent, CODE_SIGNAL_MEASUREMENT_TERMS)
                } else {
                    homeViewModel.toggleSignalMeasurementService()
                }
            }
        }

        homeViewModel.activeSignalMeasurementLiveData.listen(this) {
            homeViewModel.state.isSignalMeasurementActive.set(it)
        }

        binding.btnLoop?.setOnClickListener {
            if (this.isResumed) {
                if (binding.btnLoop?.isChecked == true) {
                    val intent = LoopInstructionsActivity.start(requireContext())
                    startActivityForResult(intent, CODE_LOOP_INSTRUCTIONS)
                } else {
                    homeViewModel.state.isLoopModeActive.set(false)
                    binding.btnLoop?.isChecked = false
                }
            }
        }

        activity?.let {
            ArrayAdapter.createFromResource(it, R.array.spin_modes, R.layout.measurement_mode_spinner_item)
                    .also { adapter ->
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.spinMode?.adapter = adapter

                        binding.spinMode?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                if(measurementViewModel.isTestsRunningLiveData.value != true) {
                                    val loopIsChecked = binding.btnLoop?.isChecked
                                    when(position) {
                                        1 -> if(!loopIsChecked) binding.btnLoop?.performClick()
                                        else -> if(loopIsChecked) binding.btnLoop?.performClick()
                                    }

                                    when(position) {
                                        2 -> {
                                            if(!homeViewModel.state.isCertModeActive.get()) {
                                                val intent = CertInstructionsActivity.start(requireContext())
                                                startActivityForResult(intent, CODE_CERT_INSTRUCTIONS)
                                            }
                                        }
                                        else -> homeViewModel.state.isCertModeActive.set(false)
                                    }
                                }
                            }

                            override fun onNothingSelected(parent: AdapterView<*>?) {
                            // do nothing
                            }

                        }
                    }
        }

        homeViewModel.newsLiveData.listen(this) {
            it?.forEach { newItem ->
                val latestNewsShown: Long = homeViewModel.getLatestNewsShown() ?: -1
                newItem.text?.let { text ->
                    newItem.title?.let { title ->
                        SimpleDialog.Builder()
                            .messageText(text)
                            .titleText(title)
                            .positiveText(android.R.string.ok)
                            .cancelable(false)
                            .show(this.childFragmentManager, CODE_DIALOG_NEWS)
                    }
                }
                homeViewModel.setNewsShown(newItem)
            }
        }

        binding.tvFrequency?.setOnClickListener {
            if (homeViewModel.shouldDisplayNetworkDetails()) {
                NetworkInfoDialog.show(childFragmentManager)
            }
        }

        binding.tvSignal?.setOnClickListener {
            if (homeViewModel.shouldDisplayNetworkDetails()) {
                NetworkInfoDialog.show(childFragmentManager)
            }
        }

        binding.debugDataButton?.setOnClickListener {
            SimpleDialog.Builder()
                .titleText("Debug data")
                .messageText(debugData())
                .positiveText(android.R.string.ok)
                .cancelable(true)
                .show(this.childFragmentManager, 0)

            //LoopFinishedActivity.startForCertMode(requireContext(), "L7142468f-7cf3-4ae8-ac0c-0baf25b44c11")
        }
    }

    @SuppressLint("MissingPermission")
    private fun debugData(): String {
        val signalWatcher = homeViewModel.signalStrengthLiveData.signalStrengthWatcher
        val networkWatcher = homeViewModel.activeNetworkLiveData.activeNetworkWatcher
        val s = StringBuilder(1000)
        s.append("Cell UUID: ")
        s.appendLine(networkWatcher.currentNetworkInfo?.cellUUID)
        s.appendLine()
        s.append("Network name: ")
        s.appendLine(signalWatcher.lastNetworkInfo?.name)
        s.append("Network type: ")
        s.appendLine(networkWatcher.currentNetworkInfo?.type?.name)
        s.append("Signal strength(dbm): ")
        s.appendLine(signalWatcher.lastDetailedNetworkInfo?.signalStrengthInfo?.value)
        s.append("Network apn: ")
        s.appendLine(networkWatcher.cellInfoWatcher.activeNetwork?.apn)

        s.append("Data subscriptionId: ")
        s.appendLine(networkWatcher.cellInfoWatcher.dataSubscriptionId)
        s.append("Area code: ")
        s.appendLine(networkWatcher.cellInfoWatcher.activeNetwork?.areaCode)
        s.append("LocationId: ")
        s.appendLine(networkWatcher.cellInfoWatcher.activeNetwork?.locationId)
        s.append("Scrambling Code: ")
        s.appendLine(networkWatcher.cellInfoWatcher.activeNetwork?.scramblingCode)
        s.append("Band channel attribution: ")
        s.appendLine(networkWatcher.cellInfoWatcher.activeNetwork?.band?.channelAttribution?.name)

        s.append("Roaming: ")
        s.appendLine(networkWatcher.cellInfoWatcher.activeNetwork?.isRoaming)
        s.appendLine()
        s.appendLine("Cell info")

        if(networkWatcher.currentNetworkInfo?.type == TransportType.CELLULAR) {
            signalWatcher.lastDetailedNetworkInfo?.allCellInfos?.forEach {
                Timber.d("Cell info: $it")
                if (it.connectionStatus.toString() == "PrimaryConnection()") {
                    s.appendLine("PrimaryConnection: ")
                    s.append("Cell type: ")
                    s.appendLine(networkWatcher.cellInfoWatcher.activeNetwork?.cellType?.displayName)
                    s.append("MNC: ")
                    s.appendLine(it.network?.mnc)
                    s.append("MCC: ")
                    s.appendLine(it.network?.mcc)
                    s.append("ISO: ")
                    s.appendLine(it.network?.iso)
                    s.append("Band name: ")
                    s.appendLine(it.band?.name)
                    s.append("Band number: ")
                    s.appendLine(it.band?.number)
                    s.append("Band channelNumber: ")
                    s.appendLine(it.band?.channelNumber)
                }
                if(it.connectionStatus is SecondaryConnection) {
                    s.appendLine("SecondaryConnection: ")
                    s.append("MNC: ")
                    s.appendLine(it.network?.mnc)
                    s.append("MCC: ")
                    s.appendLine(it.network?.mcc)
                    s.append("ISO: ")
                    s.appendLine(it.network?.iso)
                    s.append("Band name: ")
                    s.appendLine(it.band?.name)
                    s.append("Band number: ")
                    s.appendLine(it.band?.number)
                    s.append("Band channelNumber: ")
                    s.appendLine(it.band?.channelNumber)
                }
            }
        } else {
            s.appendLine("Není připojeno na mobilní data")
        }


        return s.toString()
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.signalStrengthLiveData.listen(this) {
            val networkInfo = it?.copy()
            homeViewModel.state.signalStrength.set(networkInfo?.signalStrengthInfo)
            homeViewModel.state.activeNetworkInfo.set(networkInfo)
            homeViewModel.state.secondary5GActiveNetworkInfo.set(
                try {
                    if (networkInfo?.secondary5GActiveCellNetworks?.isNotEmpty() == true) {
                        networkInfo.secondary5GActiveCellNetworks?.get(0)
                    } else {
                        null
                    }
                } catch (ex: IndexOutOfBoundsException) {
                    Timber.e(ex)
                    null
                }
            )
            homeViewModel.state.secondary5GSignalStrength.set(
                try{
                    if (networkInfo?.secondary5GActiveSignalStrengthInfos?.isNotEmpty() == true) {
                        networkInfo.secondary5GActiveSignalStrengthInfos?.get(0)
                    } else {
                        null
                    }
                } catch (ex: IndexOutOfBoundsException) {
                    Timber.e(ex)
                    null
                }
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            CODE_LOOP_INSTRUCTIONS -> {
                if (resultCode == Activity.RESULT_OK) {
                    homeViewModel.state.isLoopModeActive.set(true)
                    binding.btnLoop?.isChecked = true

                    binding.spinMode?.setSelection(1)
                    homeViewModel.state.isCertModeActive.set(false)
                } else {
                    homeViewModel.state.isLoopModeActive.set(false)
                    binding.btnLoop?.isChecked = false
                    binding.spinMode?.setSelection(0)
                }
            }
            CODE_SIGNAL_MEASUREMENT_TERMS -> {
                if (resultCode == Activity.RESULT_OK) {
                    homeViewModel.toggleSignalMeasurementService()
                    requireContext().toast(R.string.toast_signal_measurement_enabled)
                }
            }
            CODE_CERT_INSTRUCTIONS -> {
                if (resultCode == Activity.RESULT_OK) {
                    homeViewModel.state.isCertModeActive.set(true)
                    binding.spinMode?.setSelection(2)

                    homeViewModel.state.isLoopModeActive.set(false)
                    binding.btnLoop?.isChecked = false

                    //showDialog()
                } else {
                    homeViewModel.state.isCertModeActive.set(false)

                    homeViewModel.state.isLoopModeActive.set(false)
                    binding.btnLoop?.isChecked = false
                    binding.spinMode?.setSelection(0)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        homeViewModel.permissionsWatcher.notifyPermissionsUpdated()
        homeViewModel.getNews() // displaying news after permissions were/were not granted
    }

    override fun onStart() {
        super.onStart()
        homeViewModel.attach(requireContext())

        //checkPermissions()
        //showDialog()
        if(homeViewModel.shouldAskForPermission()) requirePermissions()
        startTimerForInfoWindow()
        homeViewModel.state.checkConfig()
    }

    private fun checkPermissions() {
        val permissions = homeViewModel.permissionsWatcher.requiredPermissions.toMutableSet()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasForegroundLocationPermission =
                checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasForegroundLocationPermission) {
                val hasBackgroundLocationPermission = checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty() /*&& homeViewModel.shouldAskForPermission()*/) {
            requestPermissions(permissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
            homeViewModel.permissionsWereAsked()
        } else {
            homeViewModel.getNews()
        }
    }

    private fun showDialog() {
        val checkPermissions: Boolean

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val background = checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val fineLocation = checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            checkPermissions = checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            checkPermissions = checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

//        ActivityCompat.requestPermissions(
//            requireActivity(),
//            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
//            PERMISSIONS_REQUEST_CODE
//        )

        if(!checkPermissions /*|| homeViewModel.shouldAskForPermission()*/) {
            SimpleDialog.Builder()
                .titleText(R.string.permissions_dialog_title)
                .messageText(R.string.permissions_dialog_text)
                .positiveText(android.R.string.ok)
                .cancelable(false)
                .show(this.childFragmentManager, CODE_PERM_INFO)
        }
    }

    private fun requirePermissions(forceBackgroundLocation: Boolean = false) {
        val fineLocation = checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phone = checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        if(!fineLocation || !phone) {
            SimpleDialog.Builder()
                .titleText(R.string.permissions_dialog_title)
                .messageText("Aplikace vyžaduje oprávnění pro polohu a informace o telefoním signálu")
                .positiveText(android.R.string.ok)
                .cancelable(false)
                .show(this.childFragmentManager, CODE_PERM_INFO)
        } else if (forceBackgroundLocation){
            requireBackgroundLocationPermission()
        }

    }

    private fun requireBackgroundLocationPermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_DENIED) {

            if(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                SimpleDialog.Builder()
                    .titleText(R.string.permissions_dialog_title)
                    .messageText(R.string.cert_location_permission_text_1)
                    .positiveText(R.string.text_button_accept)
                    .negativeText(R.string.text_button_decline)
                    .show(this.childFragmentManager, CODE_BACKGROUND_PERM_INFO)
            } else {
                SimpleDialog.Builder()
                    .titleText(R.string.permissions_dialog_title)
                    .messageText(R.string.cert_location_permission_text_2)
                    .positiveText(R.string.text_button_accept)
                    .negativeText(R.string.text_button_decline)
                    .show(this.childFragmentManager, CODE_BACKGROUND_BACKUP_PERM_INFO)
            }
        }
    }

    private fun isPermissionsForCertMeasuringGranted(): Boolean {
        val fineLocation = checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phone = checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val backgroundLocation: Boolean

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocation = checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            backgroundLocation = true
        }

        return fineLocation && phone && backgroundLocation
    }

    private fun startCertMeasurement() {
        Timber.d("Starting cert measurement")
        homeViewModel.state.isLoopModeActive.set(true)
        homeViewModel.appConfig.savedLoopModeNumberOfTests = homeViewModel.appConfig.loopModeNumberOfTests
        homeViewModel.appConfig.savedLoopModeWaitingTimeMin = homeViewModel.appConfig.loopModeWaitingTimeMin
        homeViewModel.appConfig.savedLoopModeDistanceMeters = homeViewModel.appConfig.loopModeDistanceMeters
        homeViewModel.appConfig.savedSkipQoSTests = homeViewModel.appConfig.skipQoSTests

        homeViewModel.appConfig.loopModeNumberOfTests = 6
        homeViewModel.appConfig.loopModeWaitingTimeMin = 11
        homeViewModel.appConfig.loopModeDistanceMeters = 100000
        homeViewModel.appConfig.skipQoSTests = true

        MeasurementService.startTests(requireContext())
        MeasurementActivity.start(requireContext())
    }

    override fun onStop() {
        super.onStop()
        homeViewModel.detach(requireContext())
    }

    /**
     * If user not doing any action within 2 second, information window display
     */
    private fun startTimerForInfoWindow() {
        Handler().postDelayed({

            if (homeViewModel.state.infoWindowStatus.get() == InfoWindowStatus.NONE) {
                homeViewModel.state.infoWindowStatus.set(InfoWindowStatus.VISIBLE)
            }
        }, INFO_WINDOW_TIME_MS)
    }

    override fun onDialogPositiveClicked(code: Int) {


        if(code == CODE_PERM_INFO) {
            permRequestLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
        }

        if (code == CODE_BACKGROUND_PERM_INFO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permRequestLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }

        if(code == CODE_BACKGROUND_BACKUP_PERM_INFO) {
            val i = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity?.packageName)
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            activityLauncher.launch(i)
        }
// cant start cert measurement if not cellular
//        if(code == CODE_NO_CELLULAR_NETWORK) {
//            startCertMeasurement()
//        }
    }

    private val permRequestLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        Timber.d("Permissions request result: $permissions")
        if(permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true && homeViewModel.state.isCertModeActive.get()) {
            requireBackgroundLocationPermission()
        }
        homeViewModel.permissionsWereAsked()
        homeViewModel.permissionsWatcher.notifyPermissionsUpdated()
    }

    private val activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Timber.d("Activity result: $it")
    }

    override fun onDialogNegativeClicked(code: Int) {
        // TODO("Not yet implemented")
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE: Int = 10
        private const val INFO_WINDOW_TIME_MS: Long = 2000
        private const val CODE_SIGNAL_MEASUREMENT_TERMS = 12
        private const val CODE_LOOP_INSTRUCTIONS = 13
        private const val CODE_DIALOG_NEWS = 14
        private const val CODE_CERT_INSTRUCTIONS = 15
        private const val CODE_PERM_INFO = 16
        private const val CODE_BACKGROUND_PERM_INFO = 17
        private const val CODE_BACKGROUND_BACKUP_PERM_INFO = 18
        private const val CODE_NO_CELLULAR_NETWORK = 19
    }
}