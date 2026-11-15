package com.nepalindrom.fielddispatch.data.repository

import com.nepalindrom.fielddispatch.data.model.ApiResult
import com.nepalindrom.fielddispatch.data.model.DemoHintResponse
import com.nepalindrom.fielddispatch.data.model.Event
import com.nepalindrom.fielddispatch.data.model.EventResult
import com.nepalindrom.fielddispatch.data.model.Health
import com.nepalindrom.fielddispatch.data.model.LoginResponse
import com.nepalindrom.fielddispatch.data.model.LogoutResponse
import com.nepalindrom.fielddispatch.data.model.Plan
import com.nepalindrom.fielddispatch.data.model.StopStatus
import com.nepalindrom.fielddispatch.data.model.UiDayOrders
import com.nepalindrom.fielddispatch.data.model.UiEmployeeStaffResponse
import com.nepalindrom.fielddispatch.data.model.UiReference
import com.nepalindrom.fielddispatch.data.model.UserPrincipal
import com.nepalindrom.fielddispatch.data.remote.ApiClient

interface DispatchRepository {
    suspend fun getHealth(baseUrl: String = ApiClient.getBaseUrl()): ApiResult<Health>
    suspend fun login(login: String, password: String, baseUrl: String = ApiClient.getBaseUrl()): ApiResult<LoginResponse>
    suspend fun logout(baseUrl: String = ApiClient.getBaseUrl()): ApiResult<LogoutResponse>
    suspend fun getMe(baseUrl: String = ApiClient.getBaseUrl()): ApiResult<UserPrincipal>
    suspend fun getDemoHint(baseUrl: String = ApiClient.getBaseUrl()): ApiResult<DemoHintResponse>

    suspend fun getPlan(date: String, baseUrl: String = ApiClient.getBaseUrl()): ApiResult<Plan>
    suspend fun getReference(baseUrl: String = ApiClient.getBaseUrl()): ApiResult<UiReference>
    suspend fun getDayOrders(date: String, baseUrl: String = ApiClient.getBaseUrl()): ApiResult<UiDayOrders>
    suspend fun getEmployeeStaff(date: String? = null, baseUrl: String = ApiClient.getBaseUrl()): ApiResult<UiEmployeeStaffResponse>
    suspend fun postEvent(date: String, event: Event, baseUrl: String = ApiClient.getBaseUrl()): ApiResult<EventResult>

    // Step check-in state management
    suspend fun markCall(date: String, staffId: String, orderId: String)
    suspend fun markArrival(date: String, staffId: String, orderId: String)
    suspend fun completeVisit(date: String, staffId: String, orderId: String)

    fun getCallTimestamp(date: String, staffId: String, orderId: String): String?
    fun getArrivalTimestamp(date: String, staffId: String, orderId: String): String?
    fun getVisitStatus(date: String, staffId: String, orderId: String): StopStatus?
}
