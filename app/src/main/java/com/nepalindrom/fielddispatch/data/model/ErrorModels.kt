package com.nepalindrom.fielddispatch.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val status: String,
    val team: String? = null,
    val version: String? = null
)

@Serializable
data class LoadResult(
    val status: String,
    val counts: Map<String, Int> = emptyMap()
)

@Serializable
data class ErrorDetail(
    val field: String? = null,
    val message: String
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: List<ErrorDetail> = emptyList()
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody
)

sealed interface ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>
    data class Error(
        val httpCode: Int,
        val errorResponse: ErrorResponse? = null,
        val rawMessage: String = "Неизвестная ошибка"
    ) : ApiResult<Nothing>
    object Loading : ApiResult<Nothing>
}
