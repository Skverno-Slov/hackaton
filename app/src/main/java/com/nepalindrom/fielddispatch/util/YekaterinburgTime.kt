package com.nepalindrom.fielddispatch.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object YekaterinburgTime {
    val ZONE_ID: ZoneId = ZoneId.of("Asia/Yekaterinburg")
    val ZONE_OFFSET: ZoneOffset = ZoneOffset.ofHours(5)

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    private val DISPLAY_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun currentDateString(): String {
        return LocalDate.now(ZONE_ID).format(DATE_FORMATTER)
    }

    fun nowIsoString(): String {
        return ZonedDateTime.now(ZONE_ID).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    fun parseOffsetDateTime(isoString: String?): OffsetDateTime? {
        if (isoString.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(isoString)
        } catch (_: Exception) {
            try {
                ZonedDateTime.parse(isoString).toOffsetDateTime()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun formatIso(odt: OffsetDateTime): String {
        return odt.withOffsetSameInstant(ZONE_OFFSET).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    fun parseDateStartOfDay(dateStr: String): OffsetDateTime {
        return try {
            val ld = LocalDate.parse(dateStr)
            ld.atTime(LocalTime.of(8, 30)).atOffset(ZONE_OFFSET)
        } catch (_: Exception) {
            LocalDate.now(ZONE_ID).atTime(LocalTime.of(8, 30)).atOffset(ZONE_OFFSET)
        }
    }

    fun formatTime(isoString: String?): String {
        if (isoString.isNullOrEmpty()) return "--:--"
        val s = isoString!!
        return try {
            val odt = OffsetDateTime.parse(s)
            odt.withOffsetSameInstant(ZONE_OFFSET).format(DISPLAY_TIME_FORMATTER)
        } catch (_: Exception) {
            try {
                LocalTime.parse(s).format(DISPLAY_TIME_FORMATTER)
            } catch (_: Exception) {
                s
            }
        }
    }

    fun formatDate(isoString: String?): String {
        if (isoString.isNullOrEmpty()) return "--.--.----"
        val s = isoString!!
        return try {
            val odt = OffsetDateTime.parse(s)
            odt.withOffsetSameInstant(ZONE_OFFSET).format(DISPLAY_DATE_FORMATTER)
        } catch (_: Exception) {
            try {
                LocalDate.parse(s).format(DISPLAY_DATE_FORMATTER)
            } catch (_: Exception) {
                s
            }
        }
    }

    fun formatDateTime(isoString: String?): String {
        if (isoString.isNullOrEmpty()) return "--"
        val s = isoString!!
        return try {
            val odt = OffsetDateTime.parse(s)
            odt.withOffsetSameInstant(ZONE_OFFSET).format(DISPLAY_DATETIME_FORMATTER)
        } catch (_: Exception) {
            s
        }
    }

    fun formatDurationMin(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "0 мин"
        val min = Math.round(seconds / 60.0).toInt()
        return if (min <= 0) "< 1 мин"
        else if (min < 60) "$min мин"
        else {
            val h = min / 60
            val m = min % 60
            if (m == 0) "$h ч" else "$h ч $m мин"
        }
    }

    fun formatDistanceKm(meters: Int?): String {
        if (meters == null || meters <= 0) return "0 км"
        val km = meters / 1000.0
        return String.format(Locale.US, "%.1f км", km)
    }
}
