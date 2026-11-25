package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ActivityOrderConfirmBinding

class OrderConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderConfirmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置 RecyclerView 布局管理器（第4天填 Adapter）
        binding.rvProducts.layoutManager = LinearLayoutManager(this)

        // 标题栏文字
        supportActionBar?.title = "确认订单"
    }
}