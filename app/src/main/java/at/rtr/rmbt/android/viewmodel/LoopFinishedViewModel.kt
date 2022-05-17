package at.rtr.rmbt.android.viewmodel

import android.net.Uri
import at.rmbt.client.control.ControlEndpointProvider
import at.rmbt.client.control.ControlServerClient
import at.rmbt.client.control.ExportPdfResponse
import at.rmbt.client.control.ExportRequestBody
import at.rtr.rmbt.android.config.AppConfig
import at.rtr.rmbt.android.ui.viewstate.LoopFinishedState
import at.rtr.rmbt.android.util.getDownloadCertPdfUri
import retrofit2.Callback
import javax.inject.Inject


class LoopFinishedViewModel @Inject constructor(
    val client: ControlServerClient,
    private val config: AppConfig,
    private val endpointProvider: ControlEndpointProvider): BaseViewModel() {

    val state = LoopFinishedState(config)

    init {
        addStateSaveHandler(state)
    }

    fun getExportPdf(body: ExportRequestBody, callback: Callback<ExportPdfResponse>) {
        client.getExportPdf(body, callback)


//        if(state.exportPdfFileName.get().isNullOrBlank()) {
//            return viewModelScope.launch {
//                client.getExportPdf(body, object : Callback<ExportPdfResponse> {
//                    override fun onResponse(
//                        call: Call<ExportPdfResponse>,
//                        response: Response<ExportPdfResponse>
//                    ) {
//                        if (response.isSuccessful) {
//                            Timber.d("Export response OK, file: %s", response.body()?.file)
//                            state.exportPdfFileName.set(response.body()?.file)
//                        } else {
//                            Timber.d("Export response failed, msg: %s", response.message())
//                        }
//
//                    }
//
//                    override fun onFailure(call: Call<ExportPdfResponse>, t: Throwable) {
//                        Timber.d("Export response failed, exception: %s", t.message)
//                        Timber.d(t)
//                    }
//
//                })
//            }

//        }
//        return null
    }

    fun getDownloadFileUrl(filename: String): Uri {
        return Uri.parse("${endpointProvider.getExportPdfUrl}/${filename}")
    }

    fun genDownloadUrl(loopUUID: String): Uri {
        return getDownloadCertPdfUri(endpointProvider, loopUUID)
    }

    fun resetLoopMode() {
        config.loopModeNumberOfTests = config.savedLoopModeNumberOfTests
        config.loopModeWaitingTimeMin = config.savedLoopModeWaitingTimeMin
        config.loopModeDistanceMeters = config.savedLoopModeDistanceMeters
    }
}