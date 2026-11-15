package com.nepalindrom.fielddispatch.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val role: String,
    val staff_id: String? = null,
    val display_name: String? = null,
    val must_change_password: Boolean = false,
    val login: String,
    val expires_in: Long = 0
)

@Serializable
data class UserPrincipal(
    val login: String,
    val role: String,
    val staff_id: String? = null,
    val display_name: String? = null,
    val must_change_password: Boolean = false,
    val auth_enabled: Boolean = true
)

@Serializable
data class LogoutResponse(
    val ok: Boolean = true
)

@Serializable
data class DemoHintResponse(
    val enabled: Boolean = false,
    val dispatcher_login: String? = null,
    val employee_sample_logins: List<String> = emptyList()
)
