package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ItemGoodsSelectBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CartItem
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatMoney

/**
 * 卖品弹窗里的列表适配器：只负责展示与改数量，合计金额由弹窗取 [totalAmount] 汇总。
 */
class GoodsSelectAdapter : RecyclerView.Adapter<GoodsSelectAdapter.Holder>() {

    private val items = mutableListOf<CartItem>()

    /** 任意一行数量变化后回调，用来刷新弹窗底部合计。 */
    var onQuantityChanged: (() -> Unit)? = null

    fun submit(list: List<CartItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun selectedItems(): List<CartItem> = items.filter { it.quantity > 0 }

    fun selectedCount(): Int = items.sumOf { it.quantity }

    fun totalAmount(): Double = items.sumOf { it.amount }

    class Holder(val binding: ItemGoodsSelectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemGoodsSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val binding = holder.binding
        val item = items[position]

        binding.tvGoodsName.text = item.name
        binding.tvGoodsPrice.text = "¥${formatMoney(item.price)}"

        // 原价与立省只做展示，不参与结算
        binding.tvGoodsOrigin.isVisible = item.hasDiscount
        if (item.hasDiscount) {
            binding.tvGoodsOrigin.text = "¥${formatMoney(item.originPrice)}"
            binding.tvGoodsOrigin.paintFlags = binding.tvGoodsOrigin.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        binding.tvGoodsSave.isVisible = item.hasDiscount
        if (item.hasDiscount) {
            binding.tvGoodsSave.text = "立省 ${formatMoney(item.saveAmount)} 元"
        }

        binding.tvGoodsStock.text = if (item.stock <= 0) "已售完" else "库存 ${item.stock}"
        binding.tvGoodsStock.setTextColor(if (item.stock <= 0) 0xFFFF4D4F.toInt() else 0xFF999999.toInt())

        binding.tvQty.text = item.quantity.toString()
        setStepperEnabled(binding.tvMinus, item.canDecrease)
        setStepperEnabled(binding.tvPlus, item.canIncrease)

        binding.tvMinus.setOnClickListener { changeQuantity(holder, -1) }
        binding.tvPlus.setOnClickListener { changeQuantity(holder, 1) }
    }

    override fun getItemCount(): Int = items.size

    /** 按当前位置增减数量，上限与库存判断都在 [CartItem] 上做。 */
    private fun changeQuantity(holder: Holder, delta: Int) {
        val pos = holder.adapterPosition
        if (pos == RecyclerView.NO_POSITION) return
        val current = items[pos]
        val next = if (delta > 0) current.increased() else current.decreased()
        if (next.quantity == current.quantity) return
        items[pos] = next
        notifyItemChanged(pos)
        onQuantityChanged?.invoke()
    }

    private fun setStepperEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.35f
    }
}
