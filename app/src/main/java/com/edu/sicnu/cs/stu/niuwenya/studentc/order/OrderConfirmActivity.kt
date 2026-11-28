package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.CActivityOrderConfirmBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.RetrofitClient
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.createOrder
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiCode
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CartItem
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.Seat
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatMoney
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 确认订单页（成员 C）。
 * 页面内容按说明书 3.1：影片与场次信息、座位清单、卖品清单、总价、提交订单。
 * 卖品通过弹窗加购；提交走 createOrder，成功后打开支付弹窗，支付成功进订单详情。
 *
 * 预览分支：由 B 的选座页通过 Intent 传入场次与座位；不带参时退回演示数据。
 */
class OrderConfirmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_SEAT_ROWS = "extra_seat_rows"
        const val EXTRA_SEAT_COLS = "extra_seat_cols"
        const val EXTRA_TICKET_PRICE = "extra_ticket_price"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
        const val EXTRA_CINEMA_HALL = "extra_cinema_hall"
        const val EXTRA_SHOW_TIME = "extra_show_time"

        private const val DEFAULT_CINEMA_ID = 1
        private const val DEFAULT_SCHEDULE_ID = 1
        private const val DEFAULT_TICKET_PRICE = 42.0
    }

    private lateinit var binding: CActivityOrderConfirmBinding
    private val goodsAdapter = ProductAdapter()
    private val uiScope = MainScope()

    private var cinemaId = DEFAULT_CINEMA_ID
    private var scheduleId = DEFAULT_SCHEDULE_ID
    private var ticketPrice = DEFAULT_TICKET_PRICE
    private var movieTitle = "星际远征"
    private var cinemaHall = "星汇影城（春熙路店） · 1号厅"
    private var showTime = "2026-11-27 19:30 · 国语 2D"
    private var seats: List<Seat> = listOf(Seat(5, 8), Seat(5, 9))

    private var cartItems: List<CartItem> = emptyList()
    private var submitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CActivityOrderConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        readIntent()

        bindMovieInfo()

        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = goodsAdapter

        binding.tvMyOrders.setOnClickListener {
            startActivity(Intent(this, OrderListActivity::class.java))
        }
        binding.tvAddGoods.setOnClickListener { openGoodsDialog() }
        binding.btnSubmitOrder.setOnClickListener { submitOrder() }

        refreshTotal()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun readIntent() {
        scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, DEFAULT_SCHEDULE_ID)
        ticketPrice = intent.getDoubleExtra(EXTRA_TICKET_PRICE, DEFAULT_TICKET_PRICE)
        intent.getStringExtra(EXTRA_MOVIE_TITLE)?.takeIf { it.isNotBlank() }?.let { movieTitle = it }
        intent.getStringExtra(EXTRA_CINEMA_HALL)?.takeIf { it.isNotBlank() }?.let { cinemaHall = it }
        intent.getStringExtra(EXTRA_SHOW_TIME)?.takeIf { it.isNotBlank() }?.let { showTime = it }

        val rows = intent.getIntArrayExtra(EXTRA_SEAT_ROWS)
        val cols = intent.getIntArrayExtra(EXTRA_SEAT_COLS)
        if (rows != null && cols != null && rows.isNotEmpty() && rows.size == cols.size) {
            seats = rows.indices.map { Seat(rows[it], cols[it]) }
        }
    }

    private fun bindMovieInfo() {
        binding.tvMovieTitle.text = movieTitle
        binding.tvCinemaHall.text = cinemaHall
        binding.tvShowTime.text = showTime
        binding.tvSeatInfo.text = "座位：${seats.joinToString("、") { it.label }}（共 ${seats.size} 张）"
    }

    /** 合计 = 票款 + 卖品小计；最终结算金额仍以服务端重算为准。 */
    private fun orderTotal(): Double = ticketPrice * seats.size + cartItems.sumOf { it.amount }

    private fun refreshTotal() {
        binding.tvTotalPrice.text = "¥${formatMoney(orderTotal())}"
    }

    private fun openGoodsDialog() {
        GoodsSelectDialog(this, cinemaId, cartItems).apply {
            onConfirm = { selected ->
                cartItems = selected
                goodsAdapter.submit(selected)
                refreshTotal()
            }
        }.show()
    }

    private fun submitOrder() {
        if (submitting) return
        submitting = true
        binding.btnSubmitOrder.isEnabled = false
        binding.btnSubmitOrder.text = "提交中…"

        uiScope.launch {
            try {
                val resp = RetrofitClient.orderApi.createOrder(
                    scheduleId = scheduleId,
                    seats = seats,
                    cartItems = cartItems,
                    clientTotal = orderTotal()
                )
                val result = resp.data
                if (resp.isSuccess && result != null) {
                    showPayDialog(result.orderId, result.totalAmount)
                } else {
                    toast(resp.msgText.ifBlank { ApiCode.tip(resp.code) })
                }
            } catch (e: Exception) {
                toast("网络异常，请先确认服务端地址")
            } finally {
                submitting = false
                binding.btnSubmitOrder.isEnabled = true
                binding.btnSubmitOrder.text = "提交订单"
            }
        }
    }

    private fun showPayDialog(orderId: Int, amount: Double) {
        PayDialog(this, orderId, amount).apply {
            onPaid = { paidOrderId ->
                startActivity(
                    Intent(this@OrderConfirmActivity, OrderDetailActivity::class.java)
                        .putExtra(OrderDetailActivity.EXTRA_ORDER_ID, paidOrderId)
                )
                finish()
            }
        }.show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
