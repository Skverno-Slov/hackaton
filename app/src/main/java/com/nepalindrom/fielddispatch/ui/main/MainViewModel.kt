package com.nepalindrom.fielddispatch.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nepalindrom.fielddispatch.data.model.ApiResult
import com.nepalindrom.fielddispatch.data.remote.ApiClient
import com.nepalindrom.fielddispatch.data.repository.DispatchRepository
import com.nepalindrom.fielddispatch.data.repository.RemoteDispatchRepository
import com.nepalindrom.fielddispatch.util.AppPreferences
import com.nepalindrom.fielddispatch.util.YekaterinburgTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: DispatchRepository = RemoteDispatchRepository()
) : ViewModel() {

    private val _currentDate = MutableStateFlow(
        AppPreferences.getDate() ?: YekaterinburgTime.currentDateString()
    )
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    private val _serverUrl = MutableStateFlow(
        AppPreferences.getServerUrl() ?: ApiClient.getBaseUrl()
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        val savedUrl = AppPreferences.getServerUrl()
        if (!savedUrl.isNullOrBlank()) {
            ApiClient.getApi(savedUrl)
        }
        syncServerActiveDate()
    }

    fun syncServerActiveDate(onSynced: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.getEmployeeStaff()
            if (res is ApiResult.Success && res.data.date.isNotBlank()) {
                val serverDate = res.data.date
                _currentDate.value = serverDate
                AppPreferences.saveDate(serverDate)
                onSynced?.invoke(serverDate)
            }
        }
    }

    fun setDate(date: String) {
        _currentDate.value = date
        AppPreferences.saveDate(date)
    }

    fun updateServerUrl(newUrl: String) {
        ApiClient.getApi(newUrl)
        _serverUrl.value = ApiClient.getBaseUrl()
        AppPreferences.saveServerUrl(ApiClient.getBaseUrl())
        syncServerActiveDate()
    }
}
