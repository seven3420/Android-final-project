package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util

import android.content.Context
import android.widget.Toast

object ToastUtil {
    fun showShort(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
