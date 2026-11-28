package com.edu.sicnu.cs.stu.niuwenya.studentc.order.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.random.Random

/**
 * 取票码 / 取餐码的二维码生成（zxing core，无第三方封装）。
 * 只负责把一段文本编成黑白位图，尺寸由调用方决定。
 */
object QrCodeUtil {

    fun encode(text: String, size: Int = 360): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 已使用 / 已过期的取票码：在正常二维码上做虚化 + 破碎处理，并在中心盖状态文字，
     * 避免用户拿旧码去检票。仅做展示，码文本本身不变，服务端仍以订单状态为准。
     *
     * @param label "已使用" 或 "已过期"
     */
    fun encodeInvalid(text: String, label: String, size: Int = 360): Bitmap? {
        val source = encode(text, size) ?: return null
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. 虚化：大幅缩小再带滤波放大，形成马赛克模糊
        val tiny = Bitmap.createScaledBitmap(source, size / 10, size / 10, true)
        val blurred = Bitmap.createScaledBitmap(tiny, size, size, true)
        paint.alpha = 215
        canvas.drawBitmap(blurred, 0f, 0f, paint)
        paint.alpha = 255

        // 2. 破碎：按码文本固定随机种子，随机挖掉色块，保证每次展示一致
        val random = Random(text.hashCode())
        paint.color = Color.WHITE
        val block = size / 12
        repeat(16) {
            val left = random.nextInt(size - block).toFloat()
            val top = random.nextInt(size - block).toFloat()
            val width = block * random.nextInt(1, 3)
            canvas.drawRect(
                left, top,
                (left + width).coerceAtMost(size.toFloat()), (top + block).toFloat(), paint
            )
        }

        // 3. 两道裂纹，强化"码已作废"的视觉
        val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 80, 80, 80)
            style = Paint.Style.STROKE
            strokeWidth = size / 90f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(
            Path().apply {
                moveTo(size * 0.12f, 0f)
                lineTo(size * 0.40f, size * 0.34f)
                lineTo(size * 0.28f, size * 0.56f)
                lineTo(size * 0.58f, size * 0.86f)
                lineTo(size * 0.52f, size.toFloat())
            },
            crackPaint
        )
        canvas.drawPath(
            Path().apply {
                moveTo(size.toFloat(), size * 0.18f)
                lineTo(size * 0.68f, size * 0.44f)
                lineTo(size * 0.82f, size * 0.66f)
            },
            crackPaint
        )

        // 4. 压暗一层，再盖状态标签
        paint.color = Color.argb(115, 0, 0, 0)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#945F50")
            textSize = size / 6.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val boxWidth = textPaint.measureText(label) + size / 6f
        val boxHeight = textPaint.textSize + size / 9f
        val cx = size / 2f
        val cy = size / 2f
        val box = RectF(
            cx - boxWidth / 2, cy - boxHeight / 2,
            cx + boxWidth / 2, cy + boxHeight / 2
        )
        paint.color = Color.argb(240, 255, 255, 255)
        canvas.drawRoundRect(box, boxHeight / 2, boxHeight / 2, paint)
        val metrics = textPaint.fontMetrics
        canvas.drawText(label, cx, cy - (metrics.ascent + metrics.descent) / 2, textPaint)

        return output
    }
}
