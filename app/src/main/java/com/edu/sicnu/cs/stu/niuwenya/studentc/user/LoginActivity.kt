package com.edu.sicnu.cs.stu.niuwenya.studentc.user

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.MainActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ActivityLoginBinding

/**
 * 简洁登录页（课程设计简化实现）。
 *
 * 未接成员 B 的登录接口前，采用"任意账号 + 6 位以上密码"的本地校验，
 * 登录成功写本地会话并进主页；接入真实接口时只需把 [login] 里的本地校验替换成请求。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { login() }
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                login()
                true
            } else {
                false
            }
        }
    }

    private fun login() {
        val account = binding.etAccount.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (account.isBlank()) {
            binding.tilAccount.error = "请输入手机号或用户名"
            return
        }
        if (password.length < 6) {
            binding.tilPassword.error = "密码至少 6 位"
            return
        }
        binding.tilAccount.error = null
        binding.tilPassword.error = null

        UserSession.login(this, account)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
