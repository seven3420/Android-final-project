package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.cinema

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.databinding.ItemDateBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DateAdapter(
    private val onSelect: (LocalDate) -> Unit
) : RecyclerView.Adapter<DateAdapter.Holder>() {

    companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
        private val WEEK_NAMES = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }

    private val dates: List<LocalDate> = (0L until 7L).map { LocalDate.now().plusDays(it) }
    private var selectedDate: LocalDate = dates.first()

    fun setSelected(date: LocalDate) {
        if (selectedDate == date) return
        val oldIndex = dates.indexOf(selectedDate)
        selectedDate = date
        val newIndex = dates.indexOf(date)
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    override fun getItemCount(): Int = dates.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(dates[position], dates[position] == selectedDate)
    }

    inner class Holder(
        private val binding: ItemDateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var date: LocalDate? = null

        init {
            binding.root.setOnClickListener {
                date?.let(onSelect)
            }
        }

        fun bind(date: LocalDate, selected: Boolean) {
            this.date = date
            val today = LocalDate.now()
            binding.tvDay.text = when (date) {
                today -> "今天"
                today.plusDays(1) -> "明天"
                else -> WEEK_NAMES[date.dayOfWeek.value - 1]
            }
            binding.tvDate.text = date.format(DATE_FORMAT)
            binding.root.isSelected = selected
        }
    }
}
