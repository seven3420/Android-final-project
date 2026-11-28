package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.DialogPayBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.RetrofitClient
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.payOrder
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiCode
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatMoney
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 模拟支付弹窗。
 * 支付方式（支付宝/微信/云闪付）只是本地 UI 单选，按 A 的接口文档 payOrder 只传 orderId，
 * 支付方式不会进入请求体。
 */
class PayDialog(
    context: Context,
    private val orderId: Int,
    amount: Double
) : BottomSheetDialog(context) {

    /** 支付成功后回调，参数是订单号。 */
    var onPaid: ((Int) -> Unit)? = null

    private val binding = DialogPayBinding.inflate(LayoutInflater.from(context))
    private val uiScope = MainScope()

    init {
        setContentView(binding.root)
        binding.tvPayAmount.text = "¥${formatMoney(amount)}"
        binding.rgPayType.check(binding.rbAlipay.id)
        binding.btnPay.setOnClickListener { pay() }
        setOnDismissListener { uiScope.cancel() }
    }

    private fun pay() {
        binding.btnPay.isEnabled = false
        binding.btnPay.text = "支付中…"
        uiScope.launch {
            try {
                val resp = RetrofitClient.orderApi.payOrder(orderId)
                if (resp.isSuccess) {
                    toast("支付成功")
                    dismiss()
                    onPaid?.invoke(orderId)
                } else {
                    toast(resp.msgText.ifBlank { ApiCode.tip(resp.code) })
                    resetButton()
                }
            } catch (e: Exception) {
                toast("网络异常，请稍后重试")
                resetButton()
            }
        }
    }

    private fun resetButton() {
        binding.btnPay.isEnabled = true
        binding.btnPay.text = "立即支付"
    }

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
