package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ActivityOrderDetailBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.RetrofitClient
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.cancelOrder
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiCode
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderDetail
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderStatus
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatCountdown
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatMoney
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.parseServerTime
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.reminder.ShowReminderScheduler
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.widget.QrCodeUtil
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 订单详情页（成员 C）：场次、座位、卖品、金额，已支付展示取票码与取餐码二维码，
 * 待支付可取消或去支付。所有字段由 getOrderDetail 一次返回，页面不做二次拼装。
 */
class OrderDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }

    private lateinit var binding: ActivityOrderDetailBinding
    private val uiScope = MainScope()
    private val ticker = Handler(Looper.getMainLooper())

    private var orderId = 0
    private var detail: OrderDetail? = null

    /** 程序化设置开关时置 true，避免触发用户操作的监听逻辑。 */
    private var bindingReminder = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast("未授予通知权限，开场提醒可能无法显示")
        }

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            ticker.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getIntExtra(EXTRA_ORDER_ID, 0)
        binding.tvBack.setOnClickListener { finish() }
        binding.btnPay.setOnClickListener { detail?.let { showPay(it) } }
        binding.btnCancel.setOnClickListener { confirmCancel() }

        loadDetail()
    }

    override fun onResume() {
        super.onResume()
        if (detail?.status == OrderStatus.UNPAID) startTicker()
    }

    override fun onPause() {
        stopTicker()
        super.onPause()
    }

    override fun onDestroy() {
        uiScope.cancel()
        ticker.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadDetail() {
        binding.progress.isVisible = true
        uiScope.launch {
            try {
                val resp = RetrofitClient.orderApi.getOrderDetail(orderId)
                val data = resp.data
                if (resp.isSuccess && data != null) {
                    detail = data
                    render(data)
                } else {
                    toast(resp.msgText.ifBlank { ApiCode.tip(resp.code) })
                }
            } catch (e: Exception) {
                toast("网络异常，请稍后重试")
            } finally {
                binding.progress.isVisible = false
            }
        }
    }

    private fun render(d: OrderDetail) {
        binding.tvStatus.text = d.statusText
        binding.tvStatus.setTextColor(statusColor(d.status))

        binding.tvMovieTitle.text = d.movieTitleText
        binding.tvCinema.text = listOf(d.cinemaNameText, d.hallNameText)
            .filter { it.isNotEmpty() }.joinToString(" · ")
        binding.tvShowTime.text = listOf(d.startTimeText, d.langVersionText)
            .filter { it.isNotEmpty() }.joinToString(" · ")
        binding.tvSeats.text = "座位：${d.seatList.joinToString("、")}（共 ${d.seatCount} 张）"
        binding.tvAddress.text = d.cinemaAddressText

        // 卖品行不多，直接动态添加文本行，不必再套一个 RecyclerView
        binding.layoutGoodsContainer.removeAllViews()
        d.goodsList.forEach { goods ->
            val row = TextView(this).apply {
                text = "${goods.nameText}  ×${goods.quantity}    ¥${formatMoney(goods.amount)}"
                textSize = 13f
                setTextColor(0xFF333333.toInt())
                setPadding(0, dp(6), 0, 0)
            }
            binding.layoutGoodsContainer.addView(row)
        }
        binding.tvNoGoods.isVisible = !d.hasGoods

        binding.tvTicketAmount.text = "¥${formatMoney(d.ticketAmount)}"
        binding.tvGoodsAmount.text = "¥${formatMoney(d.goodsAmount)}"
        binding.tvTotalAmount.text = "¥${formatMoney(d.totalAmount)}"

        val showCodes = OrderStatus.showCode(d.status) && d.hasTicketCode
        binding.layoutCodes.isVisible = showCodes
        if (showCodes) {
            val invalidLabel = ticketInvalidLabel(d)
            binding.tvTicketCode.text = d.ticketCodeText
            if (invalidLabel != null) {
                // 已使用 / 已过期：二维码虚化破碎并在图内盖状态文字，防止拿旧码检票
                binding.ivTicketQr.setImageBitmap(
                    QrCodeUtil.encodeInvalid(d.ticketCodeText, invalidLabel)
                )
                binding.tvTicketCodeState.text = invalidLabel
                binding.tvTicketCodeState.isVisible = true
                binding.tvTicketCode.setTextColor(0xFF999999.toInt())
                binding.tvTicketCode.paintFlags =
                    binding.tvTicketCode.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.ivTicketQr.setImageBitmap(QrCodeUtil.encode(d.ticketCodeText))
                binding.tvTicketCodeState.isVisible = false
                binding.tvTicketCode.setTextColor(0xFF222222.toInt())
                binding.tvTicketCode.paintFlags =
                    binding.tvTicketCode.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.layoutGoodsCode.isVisible = d.hasGoodsCode
            if (d.hasGoodsCode) {
                binding.tvGoodsCode.text = d.goodsCodeText
                binding.ivGoodsQr.setImageBitmap(QrCodeUtil.encode(d.goodsCodeText))
            }
        }

        renderReminder(d)

        val unpaid = d.status == OrderStatus.UNPAID
        binding.layoutActions.isVisible = unpaid
        if (unpaid) startTicker() else stopTicker()
    }

    /**
     * 已完成订单视为"已使用"；已支付但已过开场时间的视为"已过期"。
     * 接口没有单独的核销状态，这两个口径由订单状态 + 场次时间在本地推导。
     */
    private fun ticketInvalidLabel(d: OrderDetail): String? {
        if (d.status == OrderStatus.FINISHED) return "已使用"
        val showAt = parseServerTime(d.startTimeText)
        return if (showAt > 0L && System.currentTimeMillis() > showAt) "已过期" else null
    }

    private fun updateCountdown() {
        val d = detail ?: return
        val remain = d.remainMillis()
        binding.tvCountdown.text = if (remain > 0) {
            "剩余支付时间 ${formatCountdown(remain)}"
        } else {
            "已超过支付时限，订单将自动取消"
        }
    }

    private fun startTicker() {
        ticker.removeCallbacks(tickRunnable)
        ticker.post(tickRunnable)
    }

    private fun stopTicker() {
        ticker.removeCallbacks(tickRunnable)
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("取消订单")
            .setMessage("确定取消这笔订单吗？座位与卖品库存会一并释放。")
            .setNegativeButton("再想想", null)
            .setPositiveButton("确定取消") { _, _ -> cancelOrder() }
            .show()
    }

    private fun cancelOrder() {
        uiScope.launch {
            try {
                val resp = RetrofitClient.orderApi.cancelOrder(orderId)
                if (resp.isSuccess) {
                    toast("订单已取消")
                    loadDetail()
                } else {
                    toast(resp.msgText.ifBlank { ApiCode.tip(resp.code) })
                }
            } catch (e: Exception) {
                toast("网络异常，请稍后重试")
            }
        }
    }

    private fun showPay(d: OrderDetail) {
        PayDialog(this, d.orderId, d.totalAmount).apply {
            onPaid = { loadDetail() }
        }.show()
    }

    /**
     * 开场提醒卡片：只有已支付 / 已完成的订单、且拿到开场时间时才显示。
     * 支付成功后首次进入详情页默认开启（开场前 30 分钟通知），用户可随时关闭；
     * 订单取消时顺手把已排的闹钟取消掉，避免无效通知。
     */
    private fun renderReminder(d: OrderDetail) {
        // 已使用 / 已过期的场次不再提供开场提醒
        val showable = OrderStatus.showCode(d.status) &&
            d.startTimeText.isNotBlank() &&
            ticketInvalidLabel(d) == null
        if (!showable) {
            binding.layoutReminder.isVisible = false
            if (d.status == OrderStatus.CANCELLED) ShowReminderScheduler.cancel(this, d.orderId)
            return
        }
        binding.layoutReminder.isVisible = true

        if (!ShowReminderScheduler.hasSetting(this, d.orderId)) {
            val scheduled = ShowReminderScheduler.setEnabled(
                this, d.orderId, true,
                d.movieTitleText, d.cinemaNameText, d.startTimeText
            )
            if (scheduled) requestNotificationPermission()
        }

        val enabled = ShowReminderScheduler.isEnabled(this, d.orderId)
        bindingReminder = true
        binding.switchReminder.isChecked = enabled
        bindingReminder = false
        binding.tvReminderHint.text = reminderHint(d, enabled)

        binding.switchReminder.setOnCheckedChangeListener { _, checked ->
            if (bindingReminder) return@setOnCheckedChangeListener
            val ok = ShowReminderScheduler.setEnabled(
                this, d.orderId, checked,
                d.movieTitleText, d.cinemaNameText, d.startTimeText
            )
            if (checked && !ok) {
                // 开场时间已过或时间格式无法解析，回滚开关
                ShowReminderScheduler.setEnabled(this, d.orderId, false)
                bindingReminder = true
                binding.switchReminder.isChecked = false
                bindingReminder = false
                binding.tvReminderHint.text = reminderHint(d, false)
                toast("开场时间已过，无法设置提醒")
            } else {
                if (checked) {
                    requestNotificationPermission()
                    toast("已开启开场提醒")
                }
                binding.tvReminderHint.text = reminderHint(d, checked)
            }
        }
    }

    private fun reminderHint(d: OrderDetail, enabled: Boolean): String =
        if (enabled) "将在 ${d.startTimeText} 开场前 30 分钟提醒您取票入场"
        else "已关闭，不再提醒本场电影"

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun statusColor(status: Int): Int = when (status) {
        OrderStatus.UNPAID -> 0xFFFF4D4F.toInt()
        OrderStatus.PAID -> 0xFF52C41A.toInt()
        else -> 0xFF999999.toInt()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
