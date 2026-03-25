package at.rtr.rmbt.android.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import at.rmbt.client.control.ControlEndpointProvider
import at.rmbt.client.control.ControlServerClient
import at.rmbt.client.control.ExportPdfResponse
import at.rmbt.client.control.ExportRequestBody
import at.rtr.rmbt.android.config.AppConfig
import at.rtr.rmbt.android.ui.viewstate.LoopFinishedState
import at.rtr.rmbt.android.util.getDownloadCertPdfUri
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject

enum class PdfDownloadState {
    IDLE, REQUESTING, DOWNLOADING, READY, ERROR
}

class LoopFinishedViewModel @Inject constructor(
    val client: ControlServerClient,
    private val config: AppConfig,
    private val endpointProvider: ControlEndpointProvider
) : BaseViewModel() {

    val state = LoopFinishedState(config)

    private val _pdfDownloadState = MutableLiveData(PdfDownloadState.IDLE)
    val pdfDownloadState: LiveData<PdfDownloadState> = _pdfDownloadState

    var downloadedFileId: Long = -1L
        private set

    init {
        addStateSaveHandler(state)
    }

    fun requestExportPdf(loopUUID: String) {
        _pdfDownloadState.value = PdfDownloadState.REQUESTING
        client.getExportPdf(ExportRequestBody(loopUUID = loopUUID), object : Callback<ExportPdfResponse> {
            override fun onResponse(call: Call<ExportPdfResponse>, response: Response<ExportPdfResponse>) {
                if (response.isSuccessful) {
                    val filename = response.body()?.file
                    if (filename.isNullOrBlank()) {
                        Timber.d("Export response OK but empty filename")
                        _pdfDownloadState.postValue(PdfDownloadState.ERROR)
                    } else {
                        Timber.d("Export response OK, file: %s", filename)
                        _pdfDownloadState.postValue(PdfDownloadState.DOWNLOADING)
                        _downloadFilename.postValue(filename)
                    }
                } else {
                    Timber.d("Export response failed, msg: %s", response.message())
                    _pdfDownloadState.postValue(PdfDownloadState.ERROR)
                }
            }

            override fun onFailure(call: Call<ExportPdfResponse>, t: Throwable) {
                Timber.d(t, "Export response failed")
                _pdfDownloadState.postValue(PdfDownloadState.ERROR)
            }
        })
    }

    private val _downloadFilename = MutableLiveData<String>()
    val downloadFilename: LiveData<String> = _downloadFilename

    fun onDownloadComplete(downloadId: Long) {
        downloadedFileId = downloadId
        _pdfDownloadState.postValue(PdfDownloadState.READY)
    }

    fun onDownloadFailed() {
        _pdfDownloadState.postValue(PdfDownloadState.ERROR)
    }

    fun resetDownloadState() {
        _pdfDownloadState.value = PdfDownloadState.IDLE
    }

    fun getDownloadFileUrl(filename: String): Uri {
        return Uri.parse("${endpointProvider.getExportPdfUrl}/${filename}")
    }

    fun genDownloadUrl(loopUUID: String): Uri {
        return getDownloadCertPdfUri(endpointProvider, loopUUID)
    }
}
