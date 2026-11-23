package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.cinema

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ItemScheduleBinding
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Schedule
import java.util.Locale

class ScheduleAdapter(
    private val onSelect: (Schedule) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.Holder>() {

    private val items = mutableListOf<Schedule>()

    fun submit(schedules: List<Schedule>) {
        if (items == schedules) return
        items.clear()
        items.addAll(schedules)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(
        private val binding: ItemScheduleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(schedule: Schedule) {
            val soldOut = schedule.isSoldOut

            binding.tvScheduleTime.text = if (schedule.endTime.isNotBlank()) {
                "${schedule.startTime} - ${schedule.endTime}"
            } else {
                schedule.startTime.ifBlank { "时间待定" }
            }
            binding.tvScheduleHall.text = schedule.hallName.ifBlank { "影厅待定" }
            binding.tvSchedulePrice.text = if (schedule.price > 0) {
                String.format(Locale.CHINA, "¥%.1f", schedule.price)
            } else {
                "价格待定"
            }

            when {
                soldOut -> {
                    binding.tvScheduleSeats.text = "已售罄"
                    binding.tvScheduleSeats.setTextColor(Color.GRAY)
                }
                schedule.remainingSeats >= 0 -> {
                    binding.tvScheduleSeats.text = "余票 ${schedule.remainingSeats}"
                    binding.tvScheduleSeats.setTextColor(Color.parseColor("#FF9800"))
                }
                else -> {
                    binding.tvScheduleSeats.text = "可售"
                    binding.tvScheduleSeats.setTextColor(Color.parseColor("#FF9800"))
                }
            }

            binding.root.alpha = if (soldOut) 0.45f else 1f
            binding.root.isClickable = !soldOut
            binding.root.setOnClickListener {
                if (!soldOut) onSelect(schedule)
            }
        }
    }
}
