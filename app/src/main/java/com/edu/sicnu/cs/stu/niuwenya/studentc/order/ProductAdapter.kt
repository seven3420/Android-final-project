package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ItemOrderProductBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CartItem
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatMoney

/**
 * 确认订单页的卖品清单适配器（只读展示）。
 * 数量增减在卖品弹窗里完成，本页只展示弹窗确定后的结果。
 */
class ProductAdapter(private var productList: List<CartItem> = emptyList()) :
    RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(val binding: ItemOrderProductBinding) :
        RecyclerView.ViewHolder(binding.root)

    /** 卖品弹窗确定加购后整体替换清单。 */
    fun submit(list: List<CartItem>) {
        productList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemOrderProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val item = productList[position]

        holder.binding.tvProductName.text = item.name
        holder.binding.tvProductPrice.text = "¥${formatMoney(item.price)}"
        holder.binding.tvProductCount.text = "x${item.quantity}"
        holder.binding.tvProductAmount.text = "¥${formatMoney(item.amount)}"
    }

    override fun getItemCount(): Int = productList.size
}
