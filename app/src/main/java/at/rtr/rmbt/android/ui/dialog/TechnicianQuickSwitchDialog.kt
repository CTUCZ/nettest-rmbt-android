package at.rtr.rmbt.android.ui.dialog

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.databinding.DataBindingUtil
import at.rmbt.client.control.Server
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.DialogTechnicianQuickSwitchBinding
import at.rtr.rmbt.android.util.args
import timber.log.Timber
import java.io.Serializable

class TechnicianQuickSwitchDialog : FullscreenDialog() {

    private lateinit var binding: DialogTechnicianQuickSwitchBinding
    private var servers = listOf<Server>()
    private var selectedBackend: String = "production"
    private var selectedServer: Server? = null
    private var originalBackend: String = "production"
    private var initialSetup = true

    private val callback: Callback?
        get() = when {
            parentFragment is Callback -> parentFragment as Callback
            activity is Callback -> activity as Callback
            else -> null
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.dialog_technician_quick_switch, container, false)
        return binding.root
    }

    @Suppress("UNCHECKED_CAST")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val controlServerItems = arguments?.getStringArrayList(KEY_CONTROL_SERVERS) ?: arrayListOf()
        val currentBackendIndex = arguments?.getInt(KEY_CURRENT_BACKEND_INDEX, 0) ?: 0
        val currentServerUuid = arguments?.getString(KEY_CURRENT_SERVER_UUID)

        originalBackend = if (currentBackendIndex == 1) "test" else "production"
        selectedBackend = originalBackend

        val rawSerializable = arguments?.getSerializable(KEY_MEASUREMENT_SERVERS)
        Timber.d("TechnicianDialog: raw serializable type=${rawSerializable?.javaClass?.name}, value=$rawSerializable")
        rawSerializable?.let {
            servers = it as List<Server>
        }
        Timber.d("TechnicianDialog: deserialized ${servers.size} servers: ${servers.map { it.name }}")

        // Restore selected server from current state
        if (currentServerUuid != null) {
            selectedServer = servers.firstOrNull { it.uuid == currentServerUuid }
        }

        // Control server spinner
        val controlAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_dark_text, controlServerItems)
        controlAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerControlServer.adapter = controlAdapter
        binding.spinnerControlServer.setSelection(currentBackendIndex)

        binding.spinnerControlServer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val backend = if (position == 1) "test" else "production"
                if (initialSetup) {
                    initialSetup = false
                    return
                }
                if (backend != selectedBackend) {
                    selectedBackend = backend
                    selectedServer = null
                    callback?.onControlServerPreview(backend)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Measurement server spinner
        updateMeasurementSpinner(servers, currentServerUuid)

        binding.buttonConfirm.setOnClickListener {
            callback?.onTechnicianSettingsConfirmed(selectedBackend, selectedServer)
            dismiss()
        }

        binding.buttonCancel.setOnClickListener {
            if (selectedBackend != originalBackend) {
                callback?.onControlServerPreview(originalBackend)
            }
            dismiss()
        }
    }

    fun updateMeasurementServers(newServers: List<Server>) {
        servers = newServers
        updateMeasurementSpinner(servers, null)
    }

    private fun updateMeasurementSpinner(serverList: List<Server>, selectedUuid: String?) {
        if (!isAdded) return
        val serverNames = serverList.map { it.name ?: it.uuid ?: "Unknown" }
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item_dark_text, serverNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMeasurementServer.adapter = adapter

        if (selectedUuid != null) {
            val index = serverList.indexOfFirst { it.uuid == selectedUuid }
            if (index >= 0) {
                binding.spinnerMeasurementServer.setSelection(index)
            }
        }

        binding.spinnerMeasurementServer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < serverList.size) {
                    selectedServer = serverList[position]
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedServer = null
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            it.window?.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.CENTER)
            it.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    companion object {
        private const val KEY_CONTROL_SERVERS = "key_control_servers"
        private const val KEY_CURRENT_BACKEND_INDEX = "key_current_backend_index"
        private const val KEY_MEASUREMENT_SERVERS = "key_measurement_servers"
        private const val KEY_CURRENT_SERVER_UUID = "key_current_server_uuid"

        fun instance(
            controlServerItems: ArrayList<String>,
            currentBackendIndex: Int,
            measurementServers: List<Server>,
            currentServerUuid: String?
        ): TechnicianQuickSwitchDialog {
            val dialog = TechnicianQuickSwitchDialog()
            dialog.args {
                putStringArrayList(KEY_CONTROL_SERVERS, controlServerItems)
                putInt(KEY_CURRENT_BACKEND_INDEX, currentBackendIndex)
                putSerializable(KEY_MEASUREMENT_SERVERS, measurementServers as Serializable)
                putString(KEY_CURRENT_SERVER_UUID, currentServerUuid)
            }
            return dialog
        }
    }

    interface Callback {
        fun onControlServerPreview(backend: String)
        fun onTechnicianSettingsConfirmed(backend: String, server: Server?)
    }
}
