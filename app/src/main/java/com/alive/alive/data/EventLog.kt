package com.alive.alive.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 所有事件日志统一记录在此。
 *
 * eventType 取值：
 *  - "A"                  事件 a 触发：解锁 + 应用前台
 *  - "B"                  事件 b 触发：充电
 *  - "C"                  事件 c 触发：数据流量开关变化
 *  - "PASSIVE_CHECKIN"    被动签到完成
 *  - "ACTIVE_CHECKIN"     主动签到完成
 *  - "CARE_NOTIF"         12:00 关怀通知已推送
 *  - "EMAIL_SENT"         邮件已发送
 *  - "EMAIL_FAILED"       邮件发送失败
 *  - "RESET"              0:00 当日重置
 *  - "SERVICE_START"      守护服务启动
 *  - "SERVICE_STOP"       守护服务停止
 */
@Entity(
    tableName = "event_log",
    indices = [Index("dayKey"), Index("timestamp")]
)
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** 北京时间 yyyy-MM-dd，便于按天筛选。 */
    val dayKey: String,
    val eventType: String,
    val detail: String = ""
)
