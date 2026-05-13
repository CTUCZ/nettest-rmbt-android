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
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import at.rmbt.client.control.IpProtocol
import at.rmbt.client.control.Server
import at.rtr.rmbt.android.BuildConfig
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.FragmentHomeBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.ui.activity.CertInstructionsActivity
import at.rtr.rmbt.android.ui.activity.LoopConfigurationActivity
import at.rtr.rmbt.android.ui.activity.LoopInstructionsActivity
import at.rtr.rmbt.android.ui.activity.MeasurementActivity
import at.rtr.rmbt.android.ui.activity.PreferenceActivity
import at.rtr.rmbt.android.ui.activity.SignalMeasurementActivity
import at.rtr.rmbt.android.ui.activity.SignalMeasurementTermsActivity
import at.rtr.rmbt.android.ui.dialog.IpInfoDialog
import at.rtr.rmbt.android.ui.dialog.LocationInfoDialog
import at.rtr.rmbt.android.ui.dialog.MessageDialog
import at.rtr.rmbt.android.ui.dialog.NetworkInfoDialog
import at.rtr.rmbt.android.ui.dialog.OpenGpsSettingDialog
import at.rtr.rmbt.android.ui.dialog.OpenLocationPermissionDialog
import at.rtr.rmbt.android.ui.dialog.SimpleDialog
import at.rtr.rmbt.android.ui.dialog.TechnicianQuickSwitchDialog
import at.rtr.rmbt.android.util.InfoWindowStatus
import at.rtr.rmbt.android.util.InformationAccessProblem
import at.rtr.rmbt.android.util.ToolbarTheme
import at.rtr.rmbt.android.util.addOnPropertyChanged
import at.rtr.rmbt.android.util.changeStatusBarColor
import at.rtr.rmbt.android.util.hasLocationPermissions
import at.rtr.rmbt.android.util.listen
import at.rtr.rmbt.android.viewmodel.HomeViewModel
import at.rtr.rmbt.android.viewmodel.MeasurementViewModel
import at.specure.info.TransportType
import at.specure.info.network.WifiNetworkInfo
import at.specure.location.LocationState
import at.specure.measurement.MeasurementService
import at.specure.util.hasPermission
import cz.mroczis.netmonster.core.model.connection.SecondaryConnection
import timber.log.Timber
import kotlin.math.max

class HomeFragment : BaseFragment(), SimpleDialog.Callback, TechnicianQuickSwitchDialog.Callback {

    private val homeViewModel: HomeViewModel by viewModelLazy()
    private val binding: FragmentHomeBinding by bindingLazy()

    private val measurementViewModel: MeasurementViewModel by viewModelLazy()

    override val layoutResId = R.layout.fragment_home

    private val getSignalMeasurementResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
//                homeViewModel.toggleSignalMeasurementService()
//                requireContext().toast(R.string.toast_signal_measurement_enabled)
            }
        }

    private val getLoopModeInstructionsResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                homeViewModel.state.isLoopModeActive.set(true)
                binding.btnLoop.isChecked = true
            } else {
                homeViewModel.state.isLoopModeActive.set(false)
                binding.btnLoop.isChecked = false
            }
        }

    private val getCertInstructionsResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                homeViewModel.state.isCertModeActive.set(true)
                binding.spinMode.setSelection(2)
                homeViewModel.state.isLoopModeActive.set(false)
                binding.btnLoop.isChecked = false
            } else {
                homeViewModel.state.isCertModeActive.set(false)
                homeViewModel.state.isLoopModeActive.set(false)
                binding.btnLoop.isChecked = false
                binding.spinMode.setSelection(0)
            }
        }


    private fun recalculateInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
                val insetsSystemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val insetsDisplayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                val topSafe = max(insetsSystemBars.top, insetsDisplayCutout.top)
                val leftSafe = max(insetsSystemBars.left, insetsDisplayCutout.left)
                val rightSafe = max(insetsSystemBars.right, insetsDisplayCutout.right)

                binding.rightGuideline?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    rightMargin = rightSafe
                }

                binding.tvTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = topSafe
                    leftMargin = leftSafe
                    rightMargin = rightSafe
                }
                binding.loopModeTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = leftSafe
                    rightMargin = rightSafe
                }
                binding.btnLoop.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = topSafe
                    leftMargin = leftSafe
                    rightMargin = rightSafe
                }
                binding.btnSetting.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = topSafe
                    leftMargin = leftSafe
                    rightMargin = rightSafe
                }

                windowInsets
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //return to previous screen orientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        binding.state = homeViewModel.state

        recalculateInsets()

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
            checkInformationAvailability()
        }

        homeViewModel.ipV4ChangeLiveData.listen(this) {
            homeViewModel.state.ipV4Info.set(it)
        }

        homeViewModel.ipV6ChangeLiveData.listen(this) {
            homeViewModel.state.ipV6Info.set(it)
        }

        binding.btnSetting.setOnClickListener {
            startActivity(Intent(requireContext(), PreferenceActivity::class.java))
        }
        binding.chipTechnicianMode.setOnClickListener {
            showTechnicianQuickSwitchDialog()
        }
        binding.tvInfo.setOnClickListener {
            homeViewModel.state.infoWindowStatus.set(InfoWindowStatus.GONE)
        }

        binding.btnIpv4.setOnClickListener {
            IpInfoDialog.instance(IpProtocol.V4).show(activity)
        }

        binding.btnIpv6.setOnClickListener {
            IpInfoDialog.instance(IpProtocol.V6).show(activity)
        }

        binding.btnLocation.setOnClickListener {

            val action = {
                LocationInfoDialog.instance().show(activity)
            }

            doGPSRelatedActionOrShowProblemDialog(action)
        }

        binding.ivSignalLevel.setOnClickListener {
            if (homeViewModel.state.technicianModeIsEnabled.get() == true &&
                homeViewModel.state.selectedMeasurementServer.get() == null) {
                Toast.makeText(requireContext(), R.string.home_technician_mode_no_server, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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

        binding.btnUpload.setOnClickListener {
            homeViewModel.activeSignalMeasurementLiveData.value?.let { active ->
                if (!active) {
                    val checksPassed = isSignalMeasurementPrechecksPassed()
                    if (checksPassed) {
                        openSignalMeasurementTermsActivity()
                    }
                } else {
                    homeViewModel.toggleSignalMeasurementService()
                }
            }
        }

        homeViewModel.activeSignalMeasurementLiveData.listen(this) {
            homeViewModel.state.isSignalMeasurementActive.set(it)
            checkInformationAvailability()
        }

        binding.btnLoop.setOnClickListener {
            if (this.isResumed) {
                if (binding.btnLoop.isChecked) {
                    val intent = LoopInstructionsActivity.start(requireContext())
                    getLoopModeInstructionsResult.launch(intent)
                } else {
                    homeViewModel.state.isLoopModeActive.set(false)
                    binding.btnLoop.isChecked = false
                }
            }
            checkInformationAvailability()
        }

        activity?.let {
            ArrayAdapter.createFromResource(it, R.array.spin_modes, R.layout.measurement_mode_spinner_item)
                    .also { adapter ->
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.spinMode.adapter = adapter

                        binding.spinMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                if(measurementViewModel.isTestsRunningLiveData.value != true) {
                                    val loopIsChecked = binding.btnLoop.isChecked
                                    when(position) {
                                        1 -> if(!loopIsChecked) binding.btnLoop.performClick()
                                        else -> if(loopIsChecked) binding.btnLoop.performClick()
                                    }

                                    when(position) {
                                        2 -> {
                                            if(!homeViewModel.state.isCertModeActive.get()) {
                                                val intent = CertInstructionsActivity.start(requireContext())
                                                getCertInstructionsResult.launch(intent)
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

        binding.tvFrequency.setOnClickListener {
            if (homeViewModel.shouldDisplayNetworkDetails()) {
                NetworkInfoDialog.show(childFragmentManager)
            }
        }

        binding.tvSignal.setOnClickListener {
            if (homeViewModel.shouldDisplayNetworkDetails()) {
                NetworkInfoDialog.show(childFragmentManager)
            }
        }

        // not used in CTU-NetTest
//        binding.panelPermissionsProblems.drawableHelp.setOnClickListener {
//            PermissionsExplanationActivity.start(this)
//        }

        homeViewModel.state.informationAccessProblem.addOnPropertyChanged { problem ->
            problem.get()?.let { updateProblemUI(it) }
        }

        binding.debugDataButton?.setOnClickListener {
            SimpleDialog.Builder()
                .titleText("Debug data")
                .messageText(debugData())
                .positiveText(android.R.string.ok)
                .cancelable(true)
                .show(this.childFragmentManager, 0)
        }
    }

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

    private fun openSignalMeasurementTermsActivity() {
        val intent = SignalMeasurementTermsActivity.start(requireContext())
        getSignalMeasurementResult.launch(intent)
    }

    private fun openSignalMeasurementActivity() {
        SignalMeasurementActivity.start(requireContext())
    }

    private fun doGPSRelatedActionOrShowProblemDialog(action: () -> Unit): Boolean {
        context?.let {
            homeViewModel.state.isLocationEnabled.get()?.let {
                when (it) {
                    LocationState.ENABLED -> {
                        action()
                        return true
                    }
                    LocationState.DISABLED_APP -> {
                        OpenLocationPermissionDialog.instance()
                            .show(activity)
                        return false
                    }

                    LocationState.DISABLED_DEVICE -> {
                        OpenGpsSettingDialog.instance().show(activity)
                        return false
                    }
                }
            }
        }
        return false
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
        checkInformationAvailability()
        homeViewModel.state.informationAccessProblem.get()?.let { updateProblemUI(it) }

        continueInSignalMeasurementIfShould()
    }

    private fun continueInSignalMeasurementIfShould() {
        if (homeViewModel.shouldOpenSignalMeasurementScreen()) {

            homeViewModel.setSignalMeasurementShouldContinueInLastSession(true)
            openSignalMeasurementActivity()
        }
    }

    private fun isSignalMeasurementPrechecksPassed(): Boolean {
        val isMobileNetworkActive = homeViewModel.isMobileNetworkActive()
        val isOnlyOneSimActive = homeViewModel.isOnlyOneSimActive()
        val isGPSEnabledAndPermitted = doGPSRelatedActionOrShowProblemDialog {}

        if (!isGPSEnabledAndPermitted) {
            return false
        }

        if (!isMobileNetworkActive) {
            showWrongNetworkTypeDialog()
            return false
        }

        if (!isOnlyOneSimActive) {
            showMoreSimsActiveDialog()
            return false
        }

        return true
    }

    private fun showMoreSimsActiveDialog() {
        context?.let {
            val title = ContextCompat.getString(it, R.string.more_sims_active_dialog_title)
            val text = ContextCompat.getString(it, R.string.more_sims_active_dialog_text)
            SimpleDialog.Builder()
                .messageText(text)
                .titleText(title)
                .positiveText(R.string.confirm)
                .cancelable(false)
                .show(this.childFragmentManager, CODE_DIALOG_MORE_SIMS)
        }
    }

    private fun showWrongNetworkTypeDialog() {
        context?.let {
            val title = ContextCompat.getString(it, R.string.wrong_network_type_active_dialog_title)
            val text = ContextCompat.getString(it, R.string.wrong_network_type_active_dialog_text)
            SimpleDialog.Builder()
                .messageText(text)
                .titleText(title)
                .positiveText(R.string.confirm)
                .cancelable(false)
                .show(this.childFragmentManager, CODE_DIALOG_MORE_SIMS)
        }

    }

    private fun checkInformationAvailability() {
        val phonePermissionsGranted =
            context?.hasPermission(Manifest.permission.READ_PHONE_STATE) == true
        val locationPermissionsGranted =
            context?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) == true || context?.hasPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == true
        val precisePermissionsGranted = context?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) == true
        val backgroundLocationPermissionsGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context?.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == true
            } else {
                true
            }
        val notificationPermissionsGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context?.hasPermission(Manifest.permission.POST_NOTIFICATIONS) == true
            } else {
                true
            }
        val problem = when {
            homeViewModel.state.isLocationEnabled.get() == LocationState.DISABLED_DEVICE -> {
                homeViewModel.state.informationAccessProblem.set(InformationAccessProblem.MISSING_LOCATION_ENABLED)
            }
            !locationPermissionsGranted -> {
                homeViewModel.state.informationAccessProblem.set(InformationAccessProblem.MISSING_LOCATION_PERMISSION)
            }
            !phonePermissionsGranted -> {
                homeViewModel.state.informationAccessProblem.set(InformationAccessProblem.MISSING_READ_PHONE_STATE_PERMISSION)
            }
            !precisePermissionsGranted -> {
                homeViewModel.state.informationAccessProblem.set(InformationAccessProblem.MISSING_PRECISE_LOCATION_PERMISSION)
                Timber.e("MISSING_PRECISE_LOCATION_PERMISSION")
            }
            (!backgroundLocationPermissionsGranted) && (homeViewModel.state.isLoopModeActive.get() || homeViewModel.activeSignalMeasurementLiveData.value == true) -> {
                homeViewModel.state.informationAccessProblem.set(
                    InformationAccessProblem.MISSING_BACKGROUND_LOCATION_PERMISSION
                )
            }
            (!notificationPermissionsGranted) && (homeViewModel.state.isLoopModeActive.get() || homeViewModel.activeSignalMeasurementLiveData.value == true) -> {
                homeViewModel.state.informationAccessProblem.set(
                    InformationAccessProblem.MISSING_NOTIFICATION_PERMISSION
                )
            }
            else -> homeViewModel.state.informationAccessProblem.set(InformationAccessProblem.NO_PROBLEM)
        }
    }

    private fun updateProblemUI(problem: InformationAccessProblem) {
        if (this.isAdded && problem != InformationAccessProblem.NO_PROBLEM) {
            binding.panelPermissionsProblems.labelPermissionDisabled.text = problem.let {
                this.getText(
                    it.titleID
                )
            }
            binding.panelPermissionsProblems.textPermissionExplanation.text =
                problem.let {
                    this.getText(
                        it.descriptionId
                    )
                }
        }
        when (problem) {
            InformationAccessProblem.MISSING_LOCATION_ENABLED -> {
                binding.panelPermissionsProblems.cardPP.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            InformationAccessProblem.MISSING_LOCATION_PERMISSION,
            InformationAccessProblem.MISSING_READ_PHONE_STATE_PERMISSION,
            InformationAccessProblem.MISSING_PRECISE_LOCATION_PERMISSION,
            InformationAccessProblem.MISSING_NOTIFICATION_PERMISSION,
            InformationAccessProblem.MISSING_BACKGROUND_LOCATION_PERMISSION -> {
                binding.panelPermissionsProblems.cardPP.setOnClickListener {
                    requirePermissions()
//                    requireContext().openAppSettings()
                }
            }
            InformationAccessProblem.NO_PROBLEM -> {
                // do nothing
            }
        }
    }

    override fun onStart() {
        super.onStart()
        homeViewModel.attach(requireContext())

        if(homeViewModel.shouldAskForPermission()) requirePermissions()
        startTimerForInfoWindow()
        homeViewModel.state.checkConfig()
        updateTechnicianBanner()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPostNotificationPermission =
                checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!hasPostNotificationPermission) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty() /*&& homeViewModel.shouldAskForPermission()*/) {
            resultRequestPermissions.launch(permissions.toTypedArray())
            homeViewModel.permissionsWereAsked()
        } else {
            homeViewModel.getNews()
        }
    }

    private val resultRequestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        homeViewModel.permissionsWatcher.notifyPermissionsUpdated()
        if (it.hasLocationPermissions()) {
            locationViewModel.updateLocationPermissions()
        }
        homeViewModel.getNews() // displaying news after permissions were/were not granted
    }

    private fun requirePermissions(forceBackgroundLocation: Boolean = false) {
        val fineLocation = checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phone = checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        if(!fineLocation) {
            SimpleDialog.Builder()
                .titleText(R.string.location_permission_title)
                .messageText(R.string.permissions_dialog_text)
                .positiveText(android.R.string.ok)
                .cancelable(false)
                .show(this.childFragmentManager, CODE_PERM_LOCATION_INFO)
        } else if(!phone) {
            SimpleDialog.Builder()
                .titleText(R.string.phone_permission_title)
                .messageText(R.string.phone_permission_explanation)
                .positiveText(android.R.string.ok)
                .cancelable(false)
                .show(this.childFragmentManager, CODE_PERM_PHONE_INFO)
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
        MeasurementService.startTests(requireContext())
        MeasurementActivity.start(requireContext())
    }

    private fun updateTechnicianBanner() {
        if (homeViewModel.state.technicianModeIsEnabled.get() == true) {
            val server = homeViewModel.state.selectedMeasurementServer.get()
            val backend = homeViewModel.state.technicianSelectedBackend.get() ?: ""
            val backendLabel = if (backend == "test") {
                getString(R.string.preferences_technician_backend_test)
            } else {
                getString(R.string.preferences_technician_backend_production)
            }
            if (server != null) {
                binding.tvLblTechnicianMode.text = getString(R.string.home_technician_mode, backendLabel, server.name)
                binding.chipTechnicianMode.setBackgroundResource(R.drawable.bg_technician_chip)
            } else {
                binding.tvLblTechnicianMode.text = getString(R.string.home_technician_mode_no_server)
                binding.chipTechnicianMode.setBackgroundResource(R.drawable.bg_technician_chip_warning)
            }
        }
    }

    private fun showTechnicianQuickSwitchDialog() {
        val currentBackend = homeViewModel.state.technicianSelectedBackend.get() ?: ""
        val currentBackendIndex = if (currentBackend == "test") 1 else 0
        val servers = homeViewModel.state.technicianMeasurementServers.get() ?: emptyList()
        val currentServerUuid = homeViewModel.state.selectedMeasurementServer.get()?.uuid
        Timber.d("TechnicianDialog: opening with ${servers.size} servers, backend=$currentBackend, serverUuid=$currentServerUuid")

        TechnicianQuickSwitchDialog.instance(
            currentBackendIndex = currentBackendIndex,
            measurementServers = servers,
            currentServerUuid = currentServerUuid
        ).show(childFragmentManager)
    }

    override fun onControlServerPreview(backend: String) {
        Timber.d("TechnicianDialog: preview backend=$backend")
        homeViewModel.previewControlServer(backend) { servers ->
            Timber.d("TechnicianDialog: got ${servers.size} servers")
            activity?.runOnUiThread {
                val dialog = childFragmentManager.findFragmentByTag("dialog") as? TechnicianQuickSwitchDialog
                dialog?.updateMeasurementServers(servers)
            }
        }
    }

    override fun onTechnicianSettingsConfirmed(backend: String, server: Server?) {
        Timber.d("TechnicianDialog: confirmed backend=$backend server=${server?.name}")
        homeViewModel.appConfig.technicianSelectedBackend = backend
        homeViewModel.state.technicianSelectedBackend.set(backend)
        if (server != null) {
            homeViewModel.state.selectedMeasurementServer.set(server)
            homeViewModel.measurementServers.selectedMeasurementServer = server
        }
        updateTechnicianBanner()
    }

    override fun onStop() {
        super.onStop()
        homeViewModel.detach(requireContext())
    }

    /**
     * If user not doing any action within 5 seconds, information window display
     */
    private fun startTimerForInfoWindow() {
        Handler(Looper.getMainLooper()).postDelayed({

            if (homeViewModel.state.infoWindowStatus.get() == InfoWindowStatus.NONE) {
                homeViewModel.state.infoWindowStatus.set(InfoWindowStatus.VISIBLE)
            }
        }, INFO_WINDOW_TIME_MS)
    }

    override fun onDialogPositiveClicked(code: Int) {


        if(code == CODE_PERM_LOCATION_INFO) {
            permRequestLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }

        if(code == CODE_PERM_PHONE_INFO) {
            permRequestLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
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
    }

    private val permRequestLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        Timber.d("Permissions request result: $permissions")
        if(permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true && homeViewModel.state.isCertModeActive.get()) {
            requireBackgroundLocationPermission()
        }
        else if(permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requirePermissions()
        }
        homeViewModel.permissionsWereAsked()
        homeViewModel.permissionsWatcher.notifyPermissionsUpdated()
    }

    private val activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Timber.d("Activity result: $it")
    }

    override fun onDialogNegativeClicked(code: Int) {
    }

    companion object {
        private const val INFO_WINDOW_TIME_MS: Long = 2000
        private const val CODE_DIALOG_NEWS = 14
        private const val CODE_DIALOG_MORE_SIMS = 15
        private const val CODE_PERM_LOCATION_INFO = 17
        private const val CODE_PERM_PHONE_INFO = 18
        private const val CODE_BACKGROUND_PERM_INFO = 19
        private const val CODE_BACKGROUND_BACKUP_PERM_INFO = 20
        private const val CODE_NO_CELLULAR_NETWORK = 21
    }
}