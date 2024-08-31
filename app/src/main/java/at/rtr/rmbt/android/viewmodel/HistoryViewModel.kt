package at.rtr.rmbt.android.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.PagedList
import at.rmbt.client.control.ControlEndpointProvider
import at.rmbt.util.exception.HandledException
import at.rtr.rmbt.android.ui.viewstate.HistoryViewState
import at.rtr.rmbt.android.util.getDownloadCertPdfUri
import at.specure.data.entity.HistoryContainer
import at.specure.data.repository.HistoryLoader
import at.specure.data.repository.HistoryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val loader: HistoryLoader,
    private val endpointProvider: ControlEndpointProvider
) : BaseViewModel() {

    private val _isLoadingLiveData = MutableLiveData<Boolean>()
    private var _historyLiveData: LiveData<PagedList<HistoryContainer>> = MutableLiveData<PagedList<HistoryContainer>>()

    val state = HistoryViewState()

    val isLoadingLiveData: LiveData<Boolean>
        get() = _isLoadingLiveData

    val historyLiveData: LiveData<PagedList<HistoryContainer>>
        get() {
            _historyLiveData = loader.historyLiveData
            return _historyLiveData
        }

    init {
        addStateSaveHandler(state)

        val isLoadingChannel = Channel<Boolean>()
        launch {
            isLoadingChannel.consumeAsFlow()
                .debounce(200)
                .collect {
                    _isLoadingLiveData.postValue(it)
                }
        }

        val errorChannel = Channel<HandledException>()
        launch {
            errorChannel.consumeAsFlow()
                .collect {
                    postError(it)
                }
        }

        loader.isLoadingChannel = isLoadingChannel
        loader.errorChannel = errorChannel
    }

    val activeFiltersLiveData = repository.appliedFiltersLiveData

    init {
        addStateSaveHandler(state)
    }

    fun refreshHistory() {
        loader.refresh()
    }

    fun removeFromFilters(value: String) {
        repository.removeFromFilters(value)
        refreshHistory()
    }

    fun genDownloadUrl(loopUUID: String): Uri {
        return getDownloadCertPdfUri(endpointProvider, loopUUID)
    }
}