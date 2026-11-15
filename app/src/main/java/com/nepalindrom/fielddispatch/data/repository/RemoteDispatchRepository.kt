package com.nepalindrom.fielddispatch.data.repository

import com.nepalindrom.fielddispatch.data.model.ApiResult
import com.nepalindrom.fielddispatch.data.model.DemoHintResponse
import com.nepalindrom.fielddispatch.data.model.ErrorResponse
import com.nepalindrom.fielddispatch.data.model.Event
import com.nepalindrom.fielddispatch.data.model.EventResult
import com.nepalindrom.fielddispatch.data.model.Health
import com.nepalindrom.fielddispatch.data.model.LoginRequest
import com.nepalindrom.fielddispatch.data.model.LoginResponse
import com.nepalindrom.fielddispatch.data.model.LogoutResponse
import com.nepalindrom.fielddispatch.data.model.Plan
import com.nepalindrom.fielddispatch.data.model.StopStatus
import com.nepalindrom.fielddispatch.data.model.StopType
import com.nepalindrom.fielddispatch.data.model.UiDayOrders
import com.nepalindrom.fielddispatch.data.model.UiEmployeeStaffResponse
import com.nepalindrom.fielddispatch.data.model.UiReference
import com.nepalindrom.fielddispatch.data.model.UserPrincipal
import com.nepalindrom.fielddispatch.data.remote.ApiClient
import com.nepalindrom.fielddispatch.util.YekaterinburgTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap

class RemoteDispatchRepository : DispatchRepository {

    // Concurrent check-in stores keyed by "date:staffId:orderId"
    private val callMap = ConcurrentHashMap<String, String>()
    private val arrivalMap = ConcurrentHashMap<String, String>()
    private val statusMap = ConcurrentHashMap<String, StopStatus>()

    override suspend fun getHealth(baseUrl: String): ApiResult<Health> {
        return safeApiCall { ApiClient.getApi(baseUrl).getHealth() }
    }

    override suspend fun login(login: String, password: String, baseUrl: String): ApiResult<LoginResponse> {
        return safeApiCall {
            ApiClient.getApi(baseUrl).login(LoginRequest(login = login, password = password))
        }
    }

    override suspend fun logout(baseUrl: String): ApiResult<LogoutResponse> {
        return safeApiCall {
            ApiClient.getApi(baseUrl).logout()
        }
    }

    override suspend fun getMe(baseUrl: String): ApiResult<UserPrincipal> {
        return safeApiCall {
            ApiClient.getApi(baseUrl).getMe()
        }
    }

    override suspend fun getDemoHint(baseUrl: String): ApiResult<DemoHintResponse> {
        return safeApiCall {
            ApiClient.getApi(baseUrl).getDemoHint()
        }
    }

    override suspend fun getReference(baseUrl: String): ApiResult<UiReference> {
        return safeApiCall { ApiClient.getApi(baseUrl).getReference() }
    }

    override suspend fun getDayOrders(date: String, baseUrl: String): ApiResult<UiDayOrders> {
        return safeApiCall { ApiClient.getApi(baseUrl).getDayOrders(date) }
    }

    override suspend fun getEmployeeStaff(date: String?, baseUrl: String): ApiResult<UiEmployeeStaffResponse> {
        return safeApiCall { ApiClient.getApi(baseUrl).getEmployeeStaff(date) }
    }

    override suspend fun getPlan(date: String, baseUrl: String): ApiResult<Plan> {
        val planResult = safeApiCall { ApiClient.getApi(baseUrl).getPlan(date) }

        return if (planResult is ApiResult.Success) {
            enrichPlan(date, planResult.data, baseUrl)
        } else {
            // For 404 errors, return a clean user-friendly message
            if (planResult is ApiResult.Error && planResult.httpCode == 404) {
                ApiResult.Error(
                    httpCode = 404,
                    rawMessage = "Маршрут ещё не сформирован"
                )
            } else {
                planResult
            }
        }
    }

    override suspend fun postEvent(date: String, event: Event, baseUrl: String): ApiResult<EventResult> {
        return safeApiCall { ApiClient.getApi(baseUrl).postEvent(date, event) }
    }

    override suspend fun markCall(date: String, staffId: String, orderId: String) {
        val key = key(date, staffId, orderId)
        callMap[key] = YekaterinburgTime.nowIsoString()
    }

    override suspend fun markArrival(date: String, staffId: String, orderId: String) {
        val key = key(date, staffId, orderId)
        arrivalMap[key] = YekaterinburgTime.nowIsoString()
        statusMap[key] = StopStatus.IN_PROGRESS
    }

    override suspend fun completeVisit(date: String, staffId: String, orderId: String) {
        val key = key(date, staffId, orderId)
        statusMap[key] = StopStatus.DONE
    }

    override fun getCallTimestamp(date: String, staffId: String, orderId: String): String? {
        return callMap[key(date, staffId, orderId)]
    }

    override fun getArrivalTimestamp(date: String, staffId: String, orderId: String): String? {
        return arrivalMap[key(date, staffId, orderId)]
    }

    override fun getVisitStatus(date: String, staffId: String, orderId: String): StopStatus? {
        return statusMap[key(date, staffId, orderId)]
    }

    private suspend fun enrichPlan(date: String, plan: Plan, baseUrl: String): ApiResult<Plan> {
        val refRes = getReference(baseUrl)
        val ordersRes = getDayOrders(date, baseUrl)

        val serviceCenterMap = (refRes as? ApiResult.Success)?.data?.service_centers?.associateBy { it.service_center_id } ?: emptyMap()
        val dayOrdersMap = (ordersRes as? ApiResult.Success)?.data?.orders?.associateBy { it.order_id } ?: emptyMap()

        val enrichedRoutes = plan.routes.map { route ->
            val staffId = route.staff_id
            val sc = serviceCenterMap[route.service_center_id]

            val enrichedStops = route.stops.map { stop ->
                val key = stop.order_id?.let { key(date, staffId, it) }
                val localCall = key?.let { callMap[it] }
                val localArrival = key?.let { arrivalMap[it] }
                val localStatus = key?.let { statusMap[it] }

                when (stop.type) {
                    StopType.DEPOT_START, StopType.RESTOCK, StopType.DEPOT_END -> {
                        stop.copy(
                            address = sc?.address ?: sc?.name ?: "Сервисный центр ${stop.location_id}",
                            lat = sc?.lat ?: 56.8350,
                            lon = sc?.lon ?: 60.6020
                        )
                    }
                    StopType.VISIT -> {
                        val uiOrder = stop.order_id?.let { dayOrdersMap[it] }
                        stop.copy(
                            address = uiOrder?.address ?: "г. Екатеринбург, ${stop.location_id}",
                            lat = uiOrder?.lat ?: 56.8389,
                            lon = uiOrder?.lon ?: 60.6057,
                            work_type_name = uiOrder?.work_type_name ?: "Выездной визит",
                            technology = uiOrder?.technology,
                            requires_height = uiOrder?.requires_height ?: false,
                            customer_phone = uiOrder?.customer_phone ?: "+7 (343) 200-00-00",
                            called_at = localCall ?: stop.called_at,
                            arrived_at = localArrival ?: stop.arrived_at,
                            status = localStatus ?: stop.status ?: StopStatus.PLANNED
                        )
                    }
                    StopType.BREAK -> {
                        stop.copy(
                            address = "Обед / Перерыв на смене",
                            lat = sc?.lat ?: 56.8350,
                            lon = sc?.lon ?: 60.6020
                        )
                    }
                }
            }
            route.copy(stops = enrichedStops)
        }

        return ApiResult.Success(plan.copy(routes = enrichedRoutes))
    }

    private fun key(date: String, staffId: String, orderId: String): String = "$date:$staffId:$orderId"

    private suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): ApiResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiCall()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        ApiResult.Success(body)
                    } else {
                        ApiResult.Error(response.code(), rawMessage = "Сервер вернул пустой ответ")
                    }
                } else {
                    val errorBodyStr = response.errorBody()?.string()
                    val parsedError = errorBodyStr?.let {
                        try {
                            ApiClient.jsonConfig.decodeFromString<ErrorResponse>(it)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    val msg = parsedError?.error?.message ?: "Ошибка HTTP ${response.code()}: ${response.message()}"
                    ApiResult.Error(
                        httpCode = response.code(),
                        errorResponse = parsedError,
                        rawMessage = msg
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error(
                    httpCode = -1,
                    rawMessage = "Не удалось соединиться с сервером (проверьте URL и сеть)"
                )
            }
        }
    }
}
