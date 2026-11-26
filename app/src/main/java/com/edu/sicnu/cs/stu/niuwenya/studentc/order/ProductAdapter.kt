package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.edu.sicnu.cs.stu.niuwenya.studentc.databinding.ItemOrderProductBinding

/**
 * 确认订单页的卖品清单适配器。
 * 目前只做展示（名称 / 单价 / 数量 / 小计），数量步进器与加购放在卖品弹窗里实现。
 */
class ProductAdapter(private val productList: List<Product>) :
    RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    // ViewHolder 持有 Binding 实例
    class ProductViewHolder(val binding: ItemOrderProductBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        // 使用 View Binding 加载 item 布局
        val binding = ItemOrderProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]

        // 将数据绑定到视图上
        holder.binding.tvProductName.text = product.name
        holder.binding.tvProductPrice.text = "¥%.2f".format(product.price)
        holder.binding.tvProductCount.text = "x${product.count}"
        holder.binding.tvProductAmount.text = "¥%.2f".format(product.amount)
    }

    override fun getItemCount(): Int = productList.size
}
