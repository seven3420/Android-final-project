package com.edu.sicnu.cs.stu.niuwenya.studentc.user

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.MainActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ActivityLoginBinding

/**
 * 简洁登录页（课程设计简化实现）。
 *
 * 账号密码校验走本地用户库 [LocalUserStore]：首次使用预置演示账号，
 * 支持跳转注册、忘记密码；登录成功写本地会话并进主页。
 * 接入成员 B 的真实登录接口时，只需替换 [login] 中的校验部分。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val registerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                fillAccount(result.data?.getStringExtra(RegisterActivity.EXTRA_ACCOUNT))
            }
        }

    private val forgotLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                fillAccount(result.data?.getStringExtra(ForgotPasswordActivity.EXTRA_ACCOUNT))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        LocalUserStore.seedDemoAccount(this)

        binding.btnLogin.setOnClickListener { login() }
        binding.tvRegister.setOnClickListener {
            registerLauncher.launch(Intent(this, RegisterActivity::class.java))
        }
        binding.tvForgot.setOnClickListener {
            forgotLauncher.launch(Intent(this, ForgotPasswordActivity::class.java))
        }
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

        binding.tilAccount.error = null
        binding.tilPassword.error = null

        if (account.isBlank()) {
            binding.tilAccount.error = "请输入手机号或用户名"
            return
        }
        if (password.length < 6) {
            binding.tilPassword.error = "密码至少 6 位"
            return
        }
        if (!LocalUserStore.exists(this, account)) {
            binding.tilAccount.error = "账号不存在，请先注册"
            return
        }
        if (!LocalUserStore.verify(this, account, password)) {
            binding.tilPassword.error = "密码错误"
            return
        }

        UserSession.login(this, account)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    /** 注册 / 重置密码返回后回填账号，方便直接输入密码登录。 */
    private fun fillAccount(account: String?) {
        if (account.isNullOrBlank()) return
        binding.etAccount.setText(account)
        binding.etPassword.setText("")
        binding.etPassword.requestFocus()
    }
}
