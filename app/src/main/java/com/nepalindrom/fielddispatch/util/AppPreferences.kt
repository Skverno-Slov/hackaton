package com.nepalindrom.fielddispatch.util

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "field_dispatch_prefs"
    private const val KEY_AUTH_TOKEN = "saved_auth_token"
    private const val KEY_STAFF_ID = "saved_staff_id"
    private const val KEY_DISPLAY_NAME = "saved_display_name"
    private const val KEY_LOGIN = "saved_login"
    private const val KEY_ROLE = "saved_role"
    private const val KEY_COOKIES = "saved_cookies"
    private const val KEY_SERVER_URL = "saved_server_url"
    private const val KEY_DATE = "saved_date"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getAuthToken(): String? = prefs?.getString(KEY_AUTH_TOKEN, null)

    fun saveAuthToken(token: String) {
        prefs?.edit()?.putString(KEY_AUTH_TOKEN, token)?.apply()
    }

    fun clearAuthToken() {
        prefs?.edit()?.remove(KEY_AUTH_TOKEN)?.apply()
    }

    fun hasAuthToken(): Boolean = !getAuthToken().isNullOrBlank()

    fun getStaffId(): String? = prefs?.getString(KEY_STAFF_ID, null)

    fun saveStaffId(staffId: String) {
        prefs?.edit()?.putString(KEY_STAFF_ID, staffId)?.apply()
    }

    fun clearStaffId() {
        prefs?.edit()?.remove(KEY_STAFF_ID)?.apply()
    }

    fun getDisplayName(): String? = prefs?.getString(KEY_DISPLAY_NAME, null)

    fun saveDisplayName(name: String) {
        prefs?.edit()?.putString(KEY_DISPLAY_NAME, name)?.apply()
    }

    fun getLogin(): String? = prefs?.getString(KEY_LOGIN, null)

    fun saveLogin(login: String) {
        prefs?.edit()?.putString(KEY_LOGIN, login)?.apply()
    }

    fun getRole(): String? = prefs?.getString(KEY_ROLE, null)

    fun saveRole(role: String) {
        prefs?.edit()?.putString(KEY_ROLE, role)?.apply()
    }

    fun getCookies(): String? = prefs?.getString(KEY_COOKIES, null)

    fun saveCookies(cookies: String) {
        prefs?.edit()?.putString(KEY_COOKIES, cookies)?.apply()
    }

    fun clearAuth() {
        prefs?.edit()
            ?.remove(KEY_AUTH_TOKEN)
            ?.remove(KEY_STAFF_ID)
            ?.remove(KEY_DISPLAY_NAME)
            ?.remove(KEY_LOGIN)
            ?.remove(KEY_ROLE)
            ?.remove(KEY_COOKIES)
            ?.apply()
    }

    fun getServerUrl(): String? = prefs?.getString(KEY_SERVER_URL, null)

    fun saveServerUrl(url: String) {
        prefs?.edit()?.putString(KEY_SERVER_URL, url)?.apply()
    }

    fun getDate(): String? = prefs?.getString(KEY_DATE, null)

    fun saveDate(date: String) {
        prefs?.edit()?.putString(KEY_DATE, date)?.apply()
    }
}
