package com.alive.alive.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 所有时间锚点都以北京时间（UTC+8）为基准。
 * 这样即使设备时区被改到海外，监测窗口仍然对齐国内用户。
 */
object BeijingTime {

    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun now(): ZonedDateTime = ZonedDateTime.now(zone)

    fun today(): LocalDate = now().toLocalDate()

    fun startOfToday(): ZonedDateTime = today().atStartOfDay(zone)

    /** 今日 H:mm (24h) 的 epoch millis，用于 AlarmManager 精准闹钟。 */
    fun todayAt(hour: Int, minute: Int): Long {
        val ldt = LocalDateTime.of(today(), LocalTime.of(hour, minute))
        return ldt.atZone(zone).toInstant().toEpochMilli()
    }

    /** 明日 0:00 的 epoch millis。 */
    fun startOfTomorrow(): Long =
        today().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun epochSecondOfDay(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    fun isToday(epochMillis: Long): Boolean =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate() == today()
}
