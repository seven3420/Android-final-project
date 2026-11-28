package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.app.Activity
import android.content.Intent
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.MainActivity

/**
 * 从订单/个人中心等二级页面直接回到首页（复用栈中已有的 MainActivity）。
 */
fun Activity.goHome() {
    startActivity(
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
}
