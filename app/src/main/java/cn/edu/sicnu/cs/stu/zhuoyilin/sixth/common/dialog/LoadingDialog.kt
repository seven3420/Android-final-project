package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.dialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.DialogLoadingBinding

class LoadingDialog(context: Context) : Dialog(context) {

    private val binding = DialogLoadingBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
