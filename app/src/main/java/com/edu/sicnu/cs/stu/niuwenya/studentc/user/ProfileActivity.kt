package com.edu.sicnu.cs.stu.niuwenya.studentc.user

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ActivityProfileBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.OrderListActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.RetrofitClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 个人中心页：登录用户信息、观影概览、我的订单等入口、退出登录。
 * 原"观影统计"页已合并到这里，统计数字只作概览，不再单独成页。
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val uiScope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvBack.setOnClickListener { finish() }
        binding.rowOrders.setOnClickListener {
            startActivity(Intent(this, OrderListActivity::class.java))
        }
        binding.rowSettings.setOnClickListener { toast("设置功能开发中") }
        binding.rowHelp.setOnClickListener { toast("帮助与反馈功能开发中") }
        binding.rowAbout.setOnClickListener { toast("电影票订购 App · 课程设计") }
        binding.btnLogout.setOnClickListener { confirmLogout() }

        bindUser()
    }

    override fun onResume() {
        super.onResume()
        loadOverview()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun bindUser() {
        val nickname = UserSession.nickname(this)
        binding.tvNickname.text = nickname
        binding.tvPhone.text = UserSession.maskedAccount(this)
        binding.tvAvatar.text = nickname.take(1).ifBlank { "影" }
    }

    /** 概览数字复用 getWatchStats；失败时保留占位，不打扰用户。 */
    private fun loadOverview() {
        uiScope.launch {
            try {
                val resp = RetrofitClient.orderApi.getWatchStats()
                val stats = resp.data
                if (resp.isSuccess && stats != null) {
                    binding.tvStatShows.text = stats.totalShows.toString()
                    binding.tvStatTickets.text = stats.totalTickets.toString()
                    binding.tvStatAmount.text = "¥${stats.amountText}"
                }
            } catch (_: Exception) {
                // 个人中心的核心是账号与入口，统计接口失败不弹错误
            }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("退出后需要重新登录才能查看订单，确定退出吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("退出登录") { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        UserSession.logout(this)
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
