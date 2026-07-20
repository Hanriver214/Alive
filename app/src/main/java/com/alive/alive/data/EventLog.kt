package com.alive.alive.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 所有事件日志统一记录在此。
 *
 * eventType 取值（计分制下的行为记录）：
 *  - "SCORE_UNLOCK"       解锁 +2
 *  - "SCORE_SCREEN_LOCKED" 未解锁亮屏累计 ≥2 分钟 +1
 *  - "SCORE_SCREEN_UNLOCKED" 解锁后亮屏累计 ≥30 分钟 +1
 *  - "SCORE_FOREGROUND"   前台应用切换 +1
 *  - "SCORE_POWER"        充电/拔电 +2
 *  - "SCORE_MOBILE_DATA"  移动数据开关变化 +1
 *  - "SCORE_FLIP"         手机翻转 +1
 *  - "PASSIVE_CHECKIN"    被动签到完成（分数 ≥4）
 *  - "ACTIVE_CHECKIN"     主动签到完成
 *  - "CARE_NOTIF_12"      12:00 关怀通知已推送
 *  - "CARE_NOTIF_18"      18:00 二次提醒已推送
 *  - "EMAIL_SENT_20"      20:00 首封邮件已发送
 *  - "EMAIL_SENT_22"      22:00 第二封邮件已发送
 *  - "EMAIL_SENT"         周期重发邮件成功
 *  - "EMAIL_FAILED"       邮件发送失败
 *  - "RESET"              0:00 当日重置
 *  - "SERVICE_START"      守护服务启动
 *  - "SERVICE_STOP"       守护服务停止
 *
 * dayKey 以手机系统当前时区的自然日 yyyy-MM-dd 表示。
 */
@Entity(
    tableName = "event_log",
    indices = [Index("dayKey"), Index("timestamp")]
)
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** 系统时区 yyyy-MM-dd，便于按天筛选。 */
    val dayKey: String,
    val eventType: String,
    val detail: String = ""
)
