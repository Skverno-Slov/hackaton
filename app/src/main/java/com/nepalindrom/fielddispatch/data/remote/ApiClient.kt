package com.nepalindrom.fielddispatch.data.remote

import com.nepalindrom.fielddispatch.util.AppPreferences
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_BASE_URL = "http://26.24.226.0:8080/"

    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var apiInstance: FieldDispatchApi? = null

    val jsonConfig = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun getApi(baseUrl: String = currentBaseUrl): FieldDispatchApi {
        val sanitized = sanitizeUrl(baseUrl)
        if (apiInstance != null && currentBaseUrl == sanitized) {
            return apiInstance!!
        }

        currentBaseUrl = sanitized

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()

            val token = AppPreferences.getAuthToken()
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
                val savedCookies = AppPreferences.getCookies()
                if (!savedCookies.isNullOrBlank()) {
                    builder.header("Cookie", savedCookies)
                } else {
                    builder.header("Cookie", "fd_token=$token; token=$token")
                }
            }

            val response = chain.proceed(builder.build())

            // Persist session cookies returned by the server
            val setCookieHeaders = response.headers("Set-Cookie")
            if (setCookieHeaders.isNotEmpty()) {
                val cookieStr = setCookieHeaders.joinToString("; ") { it.substringBefore(";") }
                AppPreferences.saveCookies(cookieStr)
            }

            response
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(sanitized)
            .client(okHttpClient)
            .addConverterFactory(jsonConfig.asConverterFactory(contentType))
            .build()

        val api = retrofit.create(FieldDispatchApi::class.java)
        apiInstance = api
        return api
    }

    private fun sanitizeUrl(url: String): String {
        var trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        if (!trimmed.endsWith("/")) {
            trimmed = "$trimmed/"
        }
        return trimmed
    }
}
