package com.alive.alive.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 所有时间锚点都以手机系统当前时区为基准。
 *
 * 这样用户无论身处哪个时区，监测窗口都按本机时区的自然日 0:00 切换。
 * 若设备时区被修改，[com.alive.alive.state.DailyEventManager.resetIfNewDay]
 * 仍会以"当前系统日期 != 上次记录日期"为准触发重置。
 */
object SystemTime {

    val zone: ZoneId = ZoneId.systemDefault()

    fun now(): ZonedDateTime = ZonedDateTime.now(zone)

    fun today(): LocalDate = now().toLocalDate()

    /** 明日 0:00 的 epoch millis。 */
    fun startOfTomorrow(): Long =
        today().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** 指定日期 H:mm (24h) 的 epoch millis，用于 AlarmManager 精准闹钟。 */
    fun epochSecondOfDay(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()
}
