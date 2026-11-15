package com.nepalindrom.fielddispatch.data.remote

import com.nepalindrom.fielddispatch.data.model.DemoHintResponse
import com.nepalindrom.fielddispatch.data.model.Event
import com.nepalindrom.fielddispatch.data.model.EventResult
import com.nepalindrom.fielddispatch.data.model.Health
import com.nepalindrom.fielddispatch.data.model.LoginRequest
import com.nepalindrom.fielddispatch.data.model.LoginResponse
import com.nepalindrom.fielddispatch.data.model.LogoutResponse
import com.nepalindrom.fielddispatch.data.model.Plan
import com.nepalindrom.fielddispatch.data.model.UiDayOrders
import com.nepalindrom.fielddispatch.data.model.UiEmployeeStaffResponse
import com.nepalindrom.fielddispatch.data.model.UiReference
import com.nepalindrom.fielddispatch.data.model.UserPrincipal
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FieldDispatchApi {
    @GET("api/v1/health")
    suspend fun getHealth(): Response<Health>

    @POST("ui/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("ui/auth/logout")
    suspend fun logout(): Response<LogoutResponse>

    @GET("ui/auth/me")
    suspend fun getMe(): Response<UserPrincipal>

    @GET("ui/auth/demo-hint")
    suspend fun getDemoHint(): Response<DemoHintResponse>

    @GET("api/v1/days/{date}/plan")
    suspend fun getPlan(
        @Path("date") date: String
    ): Response<Plan>

    @POST("api/v1/days/{date}/events")
    suspend fun postEvent(
        @Path("date") date: String,
        @Body event: Event
    ): Response<EventResult>

    @GET("ui/reference")
    suspend fun getReference(): Response<UiReference>

    @GET("ui/day/{date}/orders")
    suspend fun getDayOrders(
        @Path("date") date: String
    ): Response<UiDayOrders>

    @GET("ui/employee/staff")
    suspend fun getEmployeeStaff(
        @Query("date") date: String? = null
    ): Response<UiEmployeeStaffResponse>
}
