package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.seat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Seat
import kotlin.math.max
import kotlin.math.min

/**
 * 座位图：左侧行号、上方列号、中间过道虚线、三态座位，全部自绘。
 */
class SeatMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val seatPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seatStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.parseColor("#FFC8C8C8")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
        color = Color.parseColor("#FF757575")
    }
    private val aislePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#FFBDBDBD")
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(6f)), 0f)
    }

    private var seats: List<Seat> = emptyList()
    private var rows: Int = 0
    private var cols: Int = 0
    private var selectedKeys: Set<String> = emptySet()
    private val hitRects = mutableMapOf<String, RectF>()

    var onSeatClickListener: ((Seat) -> Unit)? = null

    init {
        isClickable = true
    }

    fun submit(newSeats: List<Seat>, newRows: Int, newCols: Int, newSelectedKeys: Set<String>) {
        if (seats == newSeats && rows == newRows && cols == newCols &&
            selectedKeys == newSelectedKeys
        ) {
            return
        }
        seats = newSeats
        rows = newRows
        cols = newCols
        selectedKeys = newSelectedKeys
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rows <= 0 || cols <= 0 || seats.isEmpty()) {
            hitRects.clear()
            return
        }

        val rowLabelWidth = dp(22f)
        val topLabelHeight = dp(22f)
        val aisleWidth = dp(18f)
        val leftPadding = paddingLeft + rowLabelWidth
        val availableWidth = width - leftPadding - paddingRight
        val availableHeight = height - paddingTop - paddingBottom - topLabelHeight

        val cellSize = min(
            (availableWidth - aisleWidth) / cols,
            availableHeight / rows
        )
        val leftCount = cols / 2
        val gridWidth = cols * cellSize + aisleWidth
        val startX = leftPadding + max(0f, (availableWidth - gridWidth) / 2f)
        val startY = paddingTop + topLabelHeight +
            max(0f, (availableHeight - rows * cellSize) / 2f)

        drawLabels(canvas, startX, startY, cellSize, leftCount, aisleWidth)
        drawAisle(canvas, startX, startY, cellSize, leftCount, aisleWidth)
        drawSeats(canvas, startX, startY, cellSize, leftCount, aisleWidth)
    }

    private fun drawLabels(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        cellSize: Float,
        leftCount: Int,
        aisleWidth: Float
    ) {
        val baseline = startY - dp(7f)
        for (col in 1..cols) {
            val centerX = seatLeft(col, startX, cellSize, leftCount, aisleWidth) + cellSize / 2
            canvas.drawText(col.toString(), centerX, baseline, labelPaint)
        }

        val textOffset = -(labelPaint.ascent() + labelPaint.descent()) / 2
        for (row in 1..rows) {
            val centerY = startY + (row - 1) * cellSize + cellSize / 2 + textOffset
            canvas.drawText(row.toString(), paddingLeft + dp(10f), centerY, labelPaint)
        }
    }

    private fun drawAisle(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        cellSize: Float,
        leftCount: Int,
        aisleWidth: Float
    ) {
        val aisleX = startX + leftCount * cellSize + aisleWidth / 2
        canvas.drawLine(aisleX, startY, aisleX, startY + rows * cellSize, aislePaint)
    }

    private fun drawSeats(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        cellSize: Float,
        leftCount: Int,
        aisleWidth: Float
    ) {
        hitRects.clear()
        val seatSize = cellSize * 0.78f
        val radius = dp(5f)

        seats.forEach { seat ->
            if (seat.row <= 0 || seat.col <= 0 || seat.row > rows || seat.col > cols) {
                return@forEach
            }

            val centerX = seatLeft(seat.col, startX, cellSize, leftCount, aisleWidth) +
                cellSize / 2
            val centerY = startY + (seat.row - 1) * cellSize + cellSize / 2
            val rect = RectF(
                centerX - seatSize / 2,
                centerY - seatSize / 2,
                centerX + seatSize / 2,
                centerY + seatSize / 2
            )
            hitRects[seat.key] = rect

            val selected = seat.key in selectedKeys
            when {
                selected -> {
                    seatPaint.color = Color.parseColor("#FF6200EE")
                    canvas.drawRoundRect(rect, radius, radius, seatPaint)
                }
                seat.isSold -> {
                    seatPaint.color = Color.parseColor("#FFBDBDBD")
                    canvas.drawRoundRect(rect, radius, radius, seatPaint)
                }
                seat.isLocked -> {
                    seatPaint.color = Color.parseColor("#FFE53935")
                    canvas.drawRoundRect(rect, radius, radius, seatPaint)
                }
                else -> {
                    seatPaint.color = Color.parseColor("#FFFAFAFA")
                    canvas.drawRoundRect(rect, radius, radius, seatPaint)
                    canvas.drawRoundRect(rect, radius, radius, seatStrokePaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val seat = findSeat(event.x, event.y)
                if (seat != null && seat.isAvailable) {
                    performClick()
                    onSeatClickListener?.invoke(seat)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    private fun findSeat(x: Float, y: Float): Seat? {
        val key = hitRects.entries.firstOrNull { it.value.contains(x, y) }?.key ?: return null
        return seats.firstOrNull { it.key == key }
    }

    private fun seatLeft(
        col: Int,
        startX: Float,
        cellSize: Float,
        leftCount: Int,
        aisleWidth: Float
    ): Float {
        return startX + (col - 1) * cellSize + if (col > leftCount) aisleWidth else 0f
    }

    private fun dp(value: Float): Float = value * density
}
