package com.edu.sicnu.cs.stu.niuwenya.studentc.order

import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ItemOrderBinding
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderListItem
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderStatus
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatCountdown
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatMoney

/**
 * 订单列表适配器：按状态决定是否展示倒计时、取消与去支付。
 * 状态值只来自服务端，本地不另设状态变量。
 */
class OrderListAdapter(
    private val onItemClick: (OrderListItem) -> Unit,
    private val onPay: (OrderListItem) -> Unit,
    private val onCancel: (OrderListItem) -> Unit
) : RecyclerView.Adapter<OrderListAdapter.Holder>() {

    private val items = mutableListOf<OrderListItem>()

    fun submit(list: List<OrderListItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** 每秒只刷新待支付行，让倒计时走起来。 */
    fun tickCountdown() {
        items.forEachIndexed { index, item ->
            if (item.status == OrderStatus.UNPAID) notifyItemChanged(index)
        }
    }

    class Holder(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val binding = holder.binding
        val item = items[position]

        binding.tvMovieTitle.text = item.movieTitleText
        binding.tvStatus.text = item.statusText
        binding.tvStatus.setTextColor(statusColor(item.status))

        binding.tvCinema.text = listOf(item.cinemaNameText, item.hallNameText)
            .filter { it.isNotEmpty() }.joinToString(" · ")
        binding.tvShowTime.text = item.startTimeText
        binding.tvSeats.text = "共 ${item.seatCount} 张 · ${item.seatList.joinToString("、")}"
        binding.tvAmount.text = "合计 ¥${formatMoney(item.totalAmount)}"

        loadPoster(binding, item)

        val unpaid = item.status == OrderStatus.UNPAID
        binding.layoutActions.isVisible = unpaid
        binding.tvCountdown.isVisible = unpaid
        if (unpaid) {
            val remain = item.remainMillis()
            binding.tvCountdown.text = if (remain > 0) {
                "剩余 ${formatCountdown(remain)}"
            } else {
                "已超时"
            }
        }

        binding.root.setOnClickListener { onItemClick(item) }
        binding.btnPay.setOnClickListener { onPay(item) }
        binding.btnCancel.setOnClickListener { onCancel(item) }
    }

    override fun getItemCount(): Int = items.size

    private fun loadPoster(holder: ItemOrderBinding, item: OrderListItem) {
        if (item.posterUrl.isBlank()) {
            holder.ivPoster.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }
        Glide.with(holder.ivPoster)
            .load(item.posterUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .into(holder.ivPoster)
    }

    private fun statusColor(status: Int): Int = when (status) {
        OrderStatus.UNPAID -> 0xFFFF4D4F.toInt()
        OrderStatus.PAID -> 0xFF52C41A.toInt()
        else -> 0xFF999999.toInt()
    }
}
