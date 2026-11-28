package com.edu.sicnu.cs.stu.niuwenya.studentc.order.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.edu.sicnu.cs.stu.niuwenya.studentc.R
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.OrderDetailActivity

/**
 * 开场提醒的闹钟接收器：到点后发一条本地通知，点击进入该订单详情页。
 * 不申请任何后台服务，闹钟触发即结束，符合课程设计"轻量实现"的要求。
 */
class ShowReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_reminder_order_id"
        const val EXTRA_MOVIE_TITLE = "extra_reminder_movie_title"
        const val EXTRA_CINEMA_NAME = "extra_reminder_cinema_name"
        const val EXTRA_START_TIME = "extra_reminder_start_time"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val orderId = intent.getIntExtra(EXTRA_ORDER_ID, 0)
        val movieTitle = intent.getStringExtra(EXTRA_MOVIE_TITLE).orEmpty()
        val cinemaName = intent.getStringExtra(EXTRA_CINEMA_NAME).orEmpty()
        val startTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty()

        // Android 13 起没有通知权限，notify 会被静默丢弃，这里提前判断避免无效调用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ShowReminderScheduler.ensureChannel(context)

        val detailIntent = Intent(context, OrderDetailActivity::class.java).apply {
            putExtra(OrderDetailActivity.EXTRA_ORDER_ID, orderId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            orderId,
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (movieTitle.isBlank()) "您购买的电影即将开场" else "《$movieTitle》即将开场"
        val description = listOf(
            if (startTime.isBlank()) "" else "$startTime 开场",
            cinemaName
        ).filter { it.isNotBlank() }.joinToString(" · ")

        val notification = NotificationCompat.Builder(context, ShowReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(
                if (description.isBlank()) "距离开场还有 30 分钟，请提前取票入场"
                else "$description，请提前取票入场"
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(orderId, notification)
    }
}
