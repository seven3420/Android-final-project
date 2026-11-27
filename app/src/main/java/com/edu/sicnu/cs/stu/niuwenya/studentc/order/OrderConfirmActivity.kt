package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ActivityOrderConfirmBinding
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
 */
class OrderConfirmActivity : AppCompatActivity() {

    companion object {
        // 选座页（成员 B）尚未接入，影院 / 场次 / 座位先用演示数据；
        // 等选座页完成后改为 Intent 传参，这里只需要替换这三个值。
        private const val CINEMA_ID = 1
        private const val SCHEDULE_ID = 1
        private const val TICKET_PRICE = 42.0
    }

    private lateinit var binding: ActivityOrderConfirmBinding
    private val goodsAdapter = ProductAdapter()
    private val uiScope = MainScope()

    private val seats = listOf(Seat(5, 8), Seat(5, 9))
    private var cartItems: List<CartItem> = emptyList()
    private var submitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun bindMovieInfo() {
        binding.tvMovieTitle.text = "星际远征"
        binding.tvCinemaHall.text = "星汇影城（春熙路店） · 1号厅"
        binding.tvShowTime.text = "2026-11-27 19:30 · 国语 2D"
        binding.tvSeatInfo.text = "座位：${seats.joinToString("、") { it.label }}（共 ${seats.size} 张）"
    }

    /** 合计 = 票款 + 卖品小计；最终结算金额仍以服务端重算为准。 */
    private fun orderTotal(): Double = TICKET_PRICE * seats.size + cartItems.sumOf { it.amount }

    private fun refreshTotal() {
        binding.tvTotalPrice.text = "¥${formatMoney(orderTotal())}"
    }

    private fun openGoodsDialog() {
        GoodsSelectDialog(this, CINEMA_ID, cartItems).apply {
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
                    scheduleId = SCHEDULE_ID,
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
