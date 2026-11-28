package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.DialogGoodsBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.RetrofitClient
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiCode
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CartItem
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.Product
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatMoney
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 卖品加购弹窗：从 getProductList 取该影院的卖品，数量步进受库存与单品 5 份上限约束。
 * 点"确定加购"把数量大于 0 的条目回传给确认订单页，弹窗自身不提交订单。
 */
class GoodsSelectDialog(
    context: Context,
    private val cinemaId: Int,
    selectedItems: List<CartItem>
) : BottomSheetDialog(context) {

    /** 确定加购回调，参数只包含数量大于 0 的卖品。 */
    var onConfirm: ((List<CartItem>) -> Unit)? = null

    private val binding = DialogGoodsBinding.inflate(LayoutInflater.from(context))
    private val adapter = GoodsSelectAdapter()
    private val uiScope = MainScope()

    /** 打开弹窗前已在购物车里的数量，按卖品 ID 记住，重新打开时回显。 */
    private val selectedQuantities = selectedItems.associate { it.productId to it.quantity }

    init {
        setContentView(binding.root)
        binding.rvGoods.layoutManager = LinearLayoutManager(context)
        binding.rvGoods.adapter = adapter
        adapter.onQuantityChanged = { updateFooter() }
        binding.tvSelected.text = footerText()
        binding.btnConfirm.setOnClickListener {
            onConfirm?.invoke(adapter.selectedItems())
            dismiss()
        }
        setOnDismissListener { uiScope.cancel() }
        loadProducts()
    }

    private fun footerText(): String =
        "已选 ${adapter.selectedCount()} 件  合计 ¥${formatMoney(adapter.totalAmount())}"

    private fun updateFooter() {
        binding.tvSelected.text = footerText()
    }

    private fun loadProducts() {
        binding.btnConfirm.isEnabled = false
        uiScope.launch {
            try {
                val resp = RetrofitClient.orderApi.getProductList(cinemaId)
                if (!resp.isSuccess) {
                    toast(resp.msgText.ifBlank { ApiCode.tip(resp.code) })
                    dismiss()
                    return@launch
                }
                adapter.submit(resp.data.orEmpty().map { it.toCartItem() })
                binding.btnConfirm.isEnabled = true
                updateFooter()
            } catch (e: Exception) {
                toast("卖品加载失败，请检查服务端地址")
                dismiss()
            }
        }
    }

    /** 回显之前加购的数量，同时不超过库存与单品上限。 */
    private fun Product.toCartItem(): CartItem {
        val max = minOf(Product.MAX_QUANTITY_PER_PRODUCT, stock)
        val saved = (selectedQuantities[id] ?: 0).coerceIn(0, max.coerceAtLeast(0))
        return CartItem.from(this, saved)
    }

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
