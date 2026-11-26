package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ActivityOrderConfirmBinding

/**
 * 确认订单页（成员 C）。
 * 页面内容按说明书 3.1：影片与场次信息、座位清单、卖品清单、总价、提交订单。
 * D1 阶段先用假数据把静态页搭起来，D3 再接 createOrder。
 */
class OrderConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderConfirmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 影片与场次信息（选座页做完后改为 Intent 传参）
        binding.tvMovieTitle.text = "星际远征"
        binding.tvCinemaHall.text = "星汇影城（春熙路店） · 3 号厅"
        binding.tvShowTime.text = "2026-11-20 19:30 · 国语 2D"
        binding.tvSeatInfo.text = "座位：5 排 8 座、5 排 9 座（共 2 张）"

        // 2. 假数据：随票加购的卖品（D2 接 getProductList 后删掉）
        val mockGoods = listOf(
            Product(2001, "双人套餐", 58.00, 1),
            Product(2002, "大桶爆米花", 25.00, 2),
            Product(2003, "可乐大杯", 12.00, 2)
        )

        // 3. 设置 RecyclerView
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = ProductAdapter(mockGoods)

        // 4. 合计 = 票款 + 卖品小计（仅为展示，结算金额最终以服务端重算为准）
        val ticketAmount = 42.00 * 2
        val goodsAmount = mockGoods.sumOf { it.amount }
        binding.tvTotalPrice.text = "¥%.2f".format(ticketAmount + goodsAmount)

        // TODO(D2)：卖品弹窗（getProductList + 数量步进器 + 上限 5 份）、支付弹窗、createOrder / payOrder
        // TODO(D4)：提交成功后跳订单详情，展示取票码与取餐码
    }
}
