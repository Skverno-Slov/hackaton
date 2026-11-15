package com.nepalindrom.fielddispatch.ui.engineer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nepalindrom.fielddispatch.data.model.ApiResult
import com.nepalindrom.fielddispatch.data.model.Event
import com.nepalindrom.fielddispatch.data.model.EventResult
import com.nepalindrom.fielddispatch.data.model.OrderTransferredEvent
import com.nepalindrom.fielddispatch.data.model.Plan
import com.nepalindrom.fielddispatch.data.model.Staff
import com.nepalindrom.fielddispatch.data.model.StaffUnavailableEvent
import com.nepalindrom.fielddispatch.data.model.Stop
import com.nepalindrom.fielddispatch.data.model.TransferredTo
import com.nepalindrom.fielddispatch.data.model.UiEmployeeStaffItem
import com.nepalindrom.fielddispatch.data.model.UiReference
import com.nepalindrom.fielddispatch.data.model.UnavailableReason
import com.nepalindrom.fielddispatch.data.model.VisitDelayedEvent
import com.nepalindrom.fielddispatch.data.repository.DispatchRepository
import com.nepalindrom.fielddispatch.data.repository.RemoteDispatchRepository
import com.nepalindrom.fielddispatch.util.AppPreferences
import com.nepalindrom.fielddispatch.util.YekaterinburgTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StaffProfile(
    val staffId: String,
    val displayName: String,
    val serviceCenterId: String,
    val serviceCenterName: String,
    val profiles: List<String>,
    val shift: String
)

class EngineerViewModel(
    private val repository: DispatchRepository = RemoteDispatchRepository()
) : ViewModel() {

    private val _selectedStaffId = MutableStateFlow(AppPreferences.getStaffId() ?: "")
    val selectedStaffId: StateFlow<String> = _selectedStaffId.asStateFlow()

    private val _staffProfile = MutableStateFlow<StaffProfile?>(null)
    val staffProfile: StateFlow<StaffProfile?> = _staffProfile.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(AppPreferences.hasAuthToken())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _selectedStop = MutableStateFlow<Stop?>(null)
    val selectedStop: StateFlow<Stop?> = _selectedStop.asStateFlow()

    private val _referenceState = MutableStateFlow<ApiResult<UiReference>>(ApiResult.Loading)
    val referenceState: StateFlow<ApiResult<UiReference>> = _referenceState.asStateFlow()

    private val _planState = MutableStateFlow<ApiResult<Plan>>(ApiResult.Loading)
    val planState: StateFlow<ApiResult<Plan>> = _planState.asStateFlow()

    private val _eventResult = MutableStateFlow<ApiResult<EventResult>?>(null)
    val eventResult: StateFlow<ApiResult<EventResult>?> = _eventResult.asStateFlow()

    // Cached live staff from /ui/employee/staff or /ui/reference
    private var cachedEmployeeStaff: List<UiEmployeeStaffItem> = emptyList()
    private var cachedStaffList: List<Staff> = emptyList()
    private var cachedServiceCenterNames: Map<String, String> = emptyMap()

    init {
        loadReference()
        checkAutoLogin()
    }

    private fun checkAutoLogin() {
        val savedToken = AppPreferences.getAuthToken()
        val savedStaffId = AppPreferences.getStaffId()
        val savedName = AppPreferences.getDisplayName()

        if (!savedToken.isNullOrBlank()) {
            _isAuthenticated.value = true
            // Instant offline/cached display
            if (!savedStaffId.isNullOrBlank()) {
                _selectedStaffId.value = savedStaffId
                _staffProfile.value = StaffProfile(
                    staffId = savedStaffId,
                    displayName = savedName ?: savedStaffId,
                    serviceCenterId = "",
                    serviceCenterName = "—",
                    profiles = emptyList(),
                    shift = "—"
                )
            }

            // Verify and refresh session in background
            viewModelScope.launch {
                val meResult = repository.getMe()
                if (meResult is ApiResult.Success) {
                    val principal = meResult.data
                    val staffId = principal.staff_id ?: savedStaffId ?: principal.login
                    val dispName = principal.display_name ?: savedName ?: principal.login

                    AppPreferences.saveLogin(principal.login)
                    AppPreferences.saveRole(principal.role)
                    AppPreferences.saveStaffId(staffId)
                    AppPreferences.saveDisplayName(dispName)

                    _selectedStaffId.value = staffId
                    _isAuthenticated.value = true
                    resolveAndSetProfile(staffId, dispName)
                } else if (meResult is ApiResult.Error && meResult.httpCode == 401) {
                    // Session expired or revoked
                    logout(callServer = false)
                    _loginError.value = "Сессия истекла. Пожалуйста, выполните вход"
                } else {
                    // Offline or network error: retain cached credentials
                    if (!savedStaffId.isNullOrBlank()) {
                        resolveAndSetProfile(savedStaffId, savedName ?: savedStaffId)
                    }
                }
            }
        } else if (!savedStaffId.isNullOrBlank()) {
            loginWithCode(savedStaffId)
        }
    }

    fun setSelectedStaffId(id: String) {
        _selectedStaffId.value = id
        _selectedStop.value = null
        AppPreferences.saveStaffId(id)
    }

    fun setSelectedStop(stop: Stop?) {
        _selectedStop.value = stop
    }

    /** Login via username and password per /ui/auth/login */
    fun login(login: String, password: String, onDate: String = YekaterinburgTime.currentDateString()) {
        val cleanLogin = login.trim()
        if (cleanLogin.isBlank()) {
            _loginError.value = "Введите логин"
            return
        }
        if (password.isBlank()) {
            _loginError.value = "Введите пароль"
            return
        }

        _isLoggingIn.value = true
        _loginError.value = null

        viewModelScope.launch {
            val res = repository.login(login = cleanLogin, password = password)
            _isLoggingIn.value = false

            when (res) {
                is ApiResult.Success -> {
                    val data = res.data
                    AppPreferences.saveAuthToken(data.token)
                    AppPreferences.saveLogin(data.login)
                    AppPreferences.saveRole(data.role)
                    val staffId = data.staff_id ?: data.login
                    val dispName = data.display_name ?: data.login

                    AppPreferences.saveStaffId(staffId)
                    AppPreferences.saveDisplayName(dispName)

                    _selectedStaffId.value = staffId
                    _isAuthenticated.value = true

                    resolveAndSetProfile(staffId, dispName)
                    _loginError.value = null

                    loadPlan(onDate)
                }
                is ApiResult.Error -> {
                    val errorMsg = when (res.httpCode) {
                        401 -> "Неверный логин или пароль"
                        409 -> "Авторизация отключена на сервере"
                        429 -> "Слишком много неудачных попыток. Попробуйте через минуту"
                        else -> res.rawMessage
                    }
                    _loginError.value = errorMsg
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    /** Normalize input like "1", "001", "ST-001" -> "ST-001" */
    private fun normalizeStaffCode(input: String): String {
        val trimmed = input.trim().uppercase()
        if (trimmed.matches(Regex("^ST-[0-9]{3}$"))) return trimmed
        val digits = trimmed.removePrefix("ST-").removePrefix("ST")
        val num = digits.toIntOrNull() ?: return trimmed
        return "ST-%03d".format(num)
    }

    fun loginWithCode(rawInput: String) {
        val staffId = normalizeStaffCode(rawInput)
        if (!staffId.matches(Regex("^ST-[0-9]{3}$"))) {
            _loginError.value = "Неверный формат кода. Введите код вида ST-001"
            return
        }
        _loginError.value = null

        val empItem = cachedEmployeeStaff.firstOrNull { it.staff_id == staffId }
        if (empItem != null) {
            applyEmployeeLogin(staffId, empItem)
            return
        }

        val refStaff = cachedStaffList.firstOrNull { it.staff_id == staffId }
        if (refStaff != null) {
            applyRefLogin(staffId, refStaff)
            return
        }

        viewModelScope.launch {
            val empRes = repository.getEmployeeStaff()
            if (empRes is ApiResult.Success) {
                cachedEmployeeStaff = empRes.data.staff
                val match = empRes.data.staff.firstOrNull { it.staff_id == staffId }
                if (match != null) {
                    applyEmployeeLogin(staffId, match)
                    return@launch
                }
            }

            val refRes = repository.getReference()
            if (refRes is ApiResult.Success) {
                _referenceState.value = refRes
                cachedStaffList = refRes.data.staff
                cachedServiceCenterNames = refRes.data.service_centers.associate { it.service_center_id to it.name }

                val found = refRes.data.staff.firstOrNull { it.staff_id == staffId }
                if (found != null) {
                    applyRefLogin(staffId, found)
                } else {
                    _loginError.value = "Сотрудник $staffId не найден на сервере"
                }
            } else {
                _selectedStaffId.value = staffId
                AppPreferences.saveStaffId(staffId)
                _staffProfile.value = StaffProfile(
                    staffId = staffId,
                    displayName = staffId,
                    serviceCenterId = "",
                    serviceCenterName = "—",
                    profiles = emptyList(),
                    shift = "—"
                )
            }
        }
    }

    private fun resolveAndSetProfile(staffId: String, fallbackDisplayName: String) {
        val empMatch = cachedEmployeeStaff.firstOrNull { it.staff_id == staffId }
        if (empMatch != null) {
            applyEmployeeLogin(staffId, empMatch)
            return
        }

        val refMatch = cachedStaffList.firstOrNull { it.staff_id == staffId }
        if (refMatch != null) {
            applyRefLogin(staffId, refMatch)
            return
        }

        _staffProfile.value = StaffProfile(
            staffId = staffId,
            displayName = fallbackDisplayName,
            serviceCenterId = "",
            serviceCenterName = "—",
            profiles = emptyList(),
            shift = "—"
        )
    }

    private fun applyEmployeeLogin(staffId: String, item: UiEmployeeStaffItem) {
        _selectedStaffId.value = staffId
        AppPreferences.saveStaffId(staffId)
        val shiftStr = if (item.shift != null && item.shift.start.isNotBlank()) {
            "${item.shift.start} – ${item.shift.end}"
        } else "—"

        _staffProfile.value = StaffProfile(
            staffId = staffId,
            displayName = item.display_name ?: staffId,
            serviceCenterId = item.service_center_id,
            serviceCenterName = item.service_center_name ?: cachedServiceCenterNames[item.service_center_id] ?: item.service_center_id,
            profiles = item.profiles,
            shift = shiftStr
        )
        _loginError.value = null
    }

    private fun applyRefLogin(staffId: String, staff: Staff) {
        _selectedStaffId.value = staffId
        AppPreferences.saveStaffId(staffId)
        _staffProfile.value = StaffProfile(
            staffId = staffId,
            displayName = staff.display_name ?: staffId,
            serviceCenterId = staff.service_center_id,
            serviceCenterName = cachedServiceCenterNames[staff.service_center_id] ?: staff.service_center_id,
            profiles = staff.profiles.map { it.name },
            shift = "${staff.shift.start} – ${staff.shift.end}"
        )
        _loginError.value = null
    }

    fun logout(callServer: Boolean = true) {
        if (callServer) {
            viewModelScope.launch {
                try {
                    repository.logout()
                } catch (_: Exception) {}
            }
        }
        _selectedStaffId.value = ""
        _staffProfile.value = null
        _isAuthenticated.value = false
        _selectedStop.value = null
        _loginError.value = null
        _planState.value = ApiResult.Loading
        AppPreferences.clearAuth()
    }

    fun loadReference() {
        viewModelScope.launch {
            _referenceState.value = ApiResult.Loading

            // Fetch live employee staff (/ui/employee/staff)
            val empRes = repository.getEmployeeStaff()
            if (empRes is ApiResult.Success) {
                cachedEmployeeStaff = empRes.data.staff
            }

            // Fetch reference (/ui/reference)
            val res = repository.getReference()
            _referenceState.value = res

            if (res is ApiResult.Success) {
                cachedStaffList = res.data.staff
                cachedServiceCenterNames = res.data.service_centers.associate { it.service_center_id to it.name }
            }

            // Re-validate current login if logged in
            val currentId = _selectedStaffId.value
            if (currentId.isNotBlank()) {
                val empMatch = cachedEmployeeStaff.firstOrNull { it.staff_id == currentId }
                if (empMatch != null) {
                    applyEmployeeLogin(currentId, empMatch)
                } else {
                    val refMatch = cachedStaffList.firstOrNull { it.staff_id == currentId }
                    if (refMatch != null) {
                        applyRefLogin(currentId, refMatch)
                    }
                }
            }
        }
    }

    fun loadPlan(date: String = YekaterinburgTime.currentDateString()) {
        viewModelScope.launch {
            _planState.value = ApiResult.Loading
            val res = repository.getPlan(date)
            _planState.value = res
        }
    }

    fun clearEventResult() {
        _eventResult.value = null
    }

    fun markCall(date: String, orderId: String) {
        viewModelScope.launch {
            repository.markCall(date, _selectedStaffId.value, orderId)
            loadPlan(date)
        }
    }

    fun markArrival(date: String, orderId: String) {
        viewModelScope.launch {
            repository.markArrival(date, _selectedStaffId.value, orderId)
            loadPlan(date)
        }
    }

    fun completeVisit(date: String, orderId: String) {
        viewModelScope.launch {
            repository.completeVisit(date, _selectedStaffId.value, orderId)
            loadPlan(date)
        }
    }

    fun postVisitDelayed(date: String, orderId: String, expectedEndIso: String) {
        val staffId = _selectedStaffId.value
        val event = VisitDelayedEvent(
            event_id = "EVT-${System.currentTimeMillis()}",
            timestamp = YekaterinburgTime.nowIsoString(),
            staff_id = staffId,
            order_id = orderId,
            expected_end = expectedEndIso
        )
        sendEvent(date, event)
    }

    fun postOrderTransferred(date: String, orderId: String, actualEndIso: String, transferredTo: TransferredTo) {
        val staffId = _selectedStaffId.value
        val event = OrderTransferredEvent(
            event_id = "EVT-${System.currentTimeMillis()}",
            timestamp = YekaterinburgTime.nowIsoString(),
            staff_id = staffId,
            order_id = orderId,
            actual_end = actualEndIso,
            transferred_to = transferredTo
        )
        sendEvent(date, event)
    }

    fun postStaffUnavailable(date: String, reason: UnavailableReason, untilIso: String?) {
        val staffId = _selectedStaffId.value
        val event = StaffUnavailableEvent(
            event_id = "EVT-${System.currentTimeMillis()}",
            timestamp = YekaterinburgTime.nowIsoString(),
            staff_id = staffId,
            until = untilIso,
            reason = reason
        )
        sendEvent(date, event)
    }

    private fun sendEvent(date: String, event: Event) {
        viewModelScope.launch {
            _eventResult.value = ApiResult.Loading
            val res = repository.postEvent(date, event)
            _eventResult.value = res
            if (res is ApiResult.Success) {
                loadPlan(date)
            }
        }
    }
}
