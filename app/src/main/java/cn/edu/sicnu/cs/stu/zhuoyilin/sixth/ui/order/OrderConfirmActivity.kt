package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.order

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivityOrderConfirmBinding
import java.util.Locale

/**
 * 确认订单占位页。B-5 用它验证场次与座位参数完整传递，C 组可在此基础上实现正式下单。
 */
class OrderConfirmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityOrderConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val scheduleId = intent.getIntExtra(OrderConfirmContract.EXTRA_SCHEDULE_ID, 0)
        val seatIds = intent.getIntArrayExtra(OrderConfirmContract.EXTRA_SEAT_IDS)
            ?.joinToString("、") ?: "无"
        val labels = intent.getStringArrayListExtra(OrderConfirmContract.EXTRA_SEAT_LABELS)
            ?.joinToString("、") ?: "无"
        val totalPrice = intent.getDoubleExtra(OrderConfirmContract.EXTRA_TOTAL_PRICE, 0.0)
        val movieTitle = intent.getStringExtra(OrderConfirmContract.EXTRA_MOVIE_TITLE).orEmpty()
        val cinemaName = intent.getStringExtra(OrderConfirmContract.EXTRA_CINEMA_NAME).orEmpty()
        val hallName = intent.getStringExtra(OrderConfirmContract.EXTRA_HALL_NAME).orEmpty()
        val startTime = intent.getStringExtra(OrderConfirmContract.EXTRA_START_TIME).orEmpty()

        binding.tvOrderInfo.text = buildString {
            append("影片：").append(movieTitle.ifBlank { "未知" })
            append("\n影院：").append(cinemaName.ifBlank { "未知" })
            append("\n影厅：").append(hallName.ifBlank { "未知" })
            append("\n场次：").append(startTime.ifBlank { "未知" })
            append("\n场次 ID：").append(scheduleId)
            append("\n座位：").append(labels)
            append("\n座位 ID：").append(seatIds)
            append("\n总价：").append(String.format(Locale.CHINA, "¥%.1f", totalPrice))
        }
    }
}
