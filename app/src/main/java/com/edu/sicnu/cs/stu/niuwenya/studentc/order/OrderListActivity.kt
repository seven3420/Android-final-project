package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ActivityOrderListBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.RetrofitClient
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.cancelOrder
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiCode
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderListItem
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderStatus
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 订单列表页（成员 C）。
 * 三个 Tab 的传参按 A 的接口文档：全部不传 status，待支付 0，已完成 2；
 * 待支付行展示倒计时，可取消或去支付。
 */
class OrderListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderListBinding
    private lateinit var adapter: OrderListAdapter
    private val uiScope = MainScope()
    private val ticker = Handler(Looper.getMainLooper())

    /** null 表示"全部"Tab，不传 status。 */
    private var currentStatus: Int? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            adapter.tickCountdown()
            ticker.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = OrderListAdapter(
            onItemClick = { openDetail(it.orderId) },
            onPay = { showPay(it) },
            onCancel = { confirmCancel(it) }
        )
        binding.rvOrders.layoutManager = LinearLayoutManager(this)
        binding.rvOrders.adapter = adapter
        binding.tvBack.setOnClickListener { finish() }

        setupTabs()
    }

    override fun onResume() {
        super.onResume()
        loadOrders()
        ticker.removeCallbacks(tickRunnable)
        ticker.post(tickRunnable)
    }

    override fun onPause() {
        ticker.removeCallbacks(tickRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        uiScope.cancel()
        ticker.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("全部"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("待支付"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("已完成"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentStatus = when (tab.position) {
                    1 -> OrderStatus.UNPAID
                    2 -> OrderStatus.FINISHED
                    else -> null
                }
                loadOrders()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) {
                loadOrders()
            }
        })
    }

    private fun loadOrders() {
        binding.progress.isVisible = true
        binding.tvEmpty.isVisible = false
        uiScope.launch {
            try {
                val resp = RetrofitClient.orderApi.getOrderList(currentStatus)
                if (resp.isSuccess) {
                    val list = resp.data?.items.orEmpty()
                    adapter.submit(list)
                    binding.tvEmpty.isVisible = list.isEmpty()
                } else {
                    toast(resp.msgText.ifBlank { ApiCode.tip(resp.code) })
                }
            } catch (e: Exception) {
                toast("网络异常，请先确认服务端地址")
            } finally {
                binding.progress.isVisible = false
            }
        }
    }

    private fun openDetail(orderId: Int) {
        startActivity(
            Intent(this, OrderDetailActivity::class.java)
                .putExtra(OrderDetailActivity.EXTRA_ORDER_ID, orderId)
        )
    }

    private fun showPay(item: OrderListItem) {
        PayDialog(this, item.orderId, item.totalAmount).apply {
            onPaid = { openDetail(it) }
        }.show()
    }

    private fun confirmCancel(item: OrderListItem) {
        AlertDialog.Builder(this)
            .setTitle("取消订单")
            .setMessage("确定取消这笔订单吗？座位与卖品库存会一并释放。")
            .setNegativeButton("再想想", null)
            .setPositiveButton("确定取消") { _, _ -> cancelOrder(item.orderId) }
            .show()
    }

    private fun cancelOrder(orderId: Int) {
        uiScope.launch {
            try {
                val resp = RetrofitClient.orderApi.cancelOrder(orderId)
                if (resp.isSuccess) {
                    toast("订单已取消")
                    loadOrders()
                } else {
                    toast(resp.msgText.ifBlank { ApiCode.tip(resp.code) })
                }
            } catch (e: Exception) {
                toast("网络异常，请稍后重试")
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
