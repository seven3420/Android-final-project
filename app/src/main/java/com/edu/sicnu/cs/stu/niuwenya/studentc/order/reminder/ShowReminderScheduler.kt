package com.edu.sicnu.cs.stu.niuwenya.studentc.order.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 开场提醒调度（成员 C 的 P1 功能）。
 *
 * 口径：订单支付成功后默认开启，在电影开场前 30 分钟用 [AlarmManager] 精确闹钟拉起
 * [ShowReminderReceiver] 发通知；用户可在订单详情页手动关闭。
 * 开关状态按 orderId 存在 SharedPreferences 里（key = order_{orderId}），
 * 不与订单服务端数据耦合——A 的接口没有提醒字段，这属于纯客户端功能。
 */
object ShowReminderScheduler {

    /** 提前量：开场前 30 分钟。 */
    const val LEAD_MILLIS: Long = 30 * 60 * 1000L

    /** 通知渠道 ID，Receiver 发通知时复用。 */
    const val CHANNEL_ID = "show_reminder"

    private const val PREFS_NAME = "show_reminder_prefs"
    private const val KEY_PREFIX = "order_"

    private val TIME_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm",
        "yyyy/MM/dd HH:mm:ss"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(orderId: Int) = "$KEY_PREFIX$orderId"

    /** 该订单是否已有用户选择（含关闭），用于判断是否需要在支付成功后默认开启。 */
    fun hasSetting(context: Context, orderId: Int): Boolean =
        prefs(context).contains(key(orderId))

    /** 读取提醒开关，默认关闭；开启与否只由本地记录决定，不依赖订单状态。 */
    fun isEnabled(context: Context, orderId: Int): Boolean =
        prefs(context).getBoolean(key(orderId), false)

    /**
     * 设置提醒开关。传入完整场次信息以便闹钟触发时组装通知文案。
     * @return 开启时返回是否成功排入闹钟（开场时间已过或解析失败会返回 false）。
     */
    fun setEnabled(
        context: Context,
        orderId: Int,
        enabled: Boolean,
        movieTitle: String = "",
        cinemaName: String = "",
        startTime: String = ""
    ): Boolean {
        prefs(context).edit().putBoolean(key(orderId), enabled).apply()
        return if (enabled) {
            schedule(context, orderId, movieTitle, cinemaName, startTime)
        } else {
            cancel(context, orderId)
            true
        }
    }

    /** 排入开场前 30 分钟的闹钟；无法精确闹钟时降级为 setAndAllowWhileIdle。 */
    fun schedule(
        context: Context,
        orderId: Int,
        movieTitle: String,
        cinemaName: String,
        startTime: String
    ): Boolean {
        val showAt = parseShowTime(startTime) ?: return false
        val triggerAt = showAt - LEAD_MILLIS
        if (triggerAt <= System.currentTimeMillis()) return false

        ensureChannel(context)

        val intent = Intent(context, ShowReminderReceiver::class.java).apply {
            putExtra(ShowReminderReceiver.EXTRA_ORDER_ID, orderId)
            putExtra(ShowReminderReceiver.EXTRA_MOVIE_TITLE, movieTitle)
            putExtra(ShowReminderReceiver.EXTRA_CINEMA_NAME, cinemaName)
            putExtra(ShowReminderReceiver.EXTRA_START_TIME, startTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(orderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        return try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                // 未授予精确闹钟权限时退化为非精确提醒，误差在系统允许范围内，功能仍可用
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            true
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            true
        }
    }

    /** 取消该订单已排的提醒。 */
    fun cancel(context: Context, orderId: Int) {
        val intent = Intent(context, ShowReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(orderId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /** 创建通知渠道；Android 8.0 以下没有渠道概念，直接跳过。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "开场提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "电影开场前 30 分钟提醒取票入场"
        }
        manager.createNotificationChannel(channel)
    }

    private fun requestCode(orderId: Int): Int = orderId and 0xFFFF

    /** 服务端场次时间可能是 "yyyy-MM-dd HH:mm" 或带秒格式，两种都兼容。 */
    private fun parseShowTime(text: String): Long? {
        val value = text.trim()
        if (value.isEmpty()) return null
        for (pattern in TIME_PATTERNS) {
            try {
                val time = SimpleDateFormat(pattern, Locale.CHINA).parse(value)?.time
                if (time != null) return time
            } catch (_: Exception) {
                // 换下一个格式继续尝试
            }
        }
        return null
    }
}
