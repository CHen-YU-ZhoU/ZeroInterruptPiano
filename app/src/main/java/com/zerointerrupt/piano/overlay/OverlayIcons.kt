package com.zerointerrupt.piano.overlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/**
 * Crisp Canvas-drawn Material Design 3 vector icon drawables for WindowManager overlays.
 * Eliminates inconsistent system font glyphs and colored emoji rendering (e.g. orange pause box).
 */
object OverlayIcons {

    private abstract class BaseVectorDrawable(
        protected val density: Float,
        private val widthDp: Float,
        private val heightDp: Float
    ) : Drawable() {
        protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        protected fun dp(v: Float): Float = v * density

        override fun getIntrinsicWidth(): Int = (widthDp * density).roundToInt().coerceAtLeast(1)
        override fun getIntrinsicHeight(): Int = (heightDp * density).roundToInt().coerceAtLeast(1)

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** 2x3 compact dot-grid drag handle (native MD3 drag indicator). */
    fun dragHandle(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 14f, 20f) {
            init {
                paint.style = Paint.Style.FILL
                paint.color = color
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val r = dp(1.35f)
                val dx = dp(2.4f)
                val dy = dp(4.2f)
                for (row in -1..1) {
                    val y = cy + row * dy
                    canvas.drawCircle(cx - dx, y, r, paint)
                    canvas.drawCircle(cx + dx, y, r, paint)
                }
            }
        }

    /** Smooth rounded MD3 dropdown / collapse chevron. */
    fun chevron(color: Int, density: Float, pointUp: Boolean): Drawable =
        object : BaseVectorDrawable(density, 16f, 16f) {
            private val path = Path()

            init {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.85f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = color
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val halfW = dp(3.8f)
                val halfH = dp(2.0f)
                path.reset()
                if (pointUp) {
                    path.moveTo(cx - halfW, cy + halfH)
                    path.lineTo(cx, cy - halfH)
                    path.lineTo(cx + halfW, cy + halfH)
                } else {
                    path.moveTo(cx - halfW, cy - halfH)
                    path.lineTo(cx, cy + halfH)
                    path.lineTo(cx + halfW, cy - halfH)
                }
                canvas.drawPath(path, paint)
            }
        }

    /** Rounded Material 3 play triangle. */
    fun play(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 14f, 14f) {
            private val path = Path()

            init {
                paint.style = Paint.Style.FILL
                paint.color = color
                paint.pathEffect = CornerPathEffect(dp(1.5f))
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                path.reset()
                path.moveTo(cx - dp(3.6f), cy - dp(5.0f))
                path.lineTo(cx + dp(5.0f), cy)
                path.lineTo(cx - dp(3.6f), cy + dp(5.0f))
                path.close()
                canvas.drawPath(path, paint)
            }
        }

    /** Crisp dual rounded-bar pause icon (replaces system orange pause emoji). */
    fun pause(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 14f, 14f) {
            private val leftBar = RectF()
            private val rightBar = RectF()

            init {
                paint.style = Paint.Style.FILL
                paint.color = color
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val barW = dp(3.0f)
                val halfH = dp(5.0f)
                val gapHalf = dp(1.6f)
                val rx = dp(1.2f)
                leftBar.set(cx - gapHalf - barW, cy - halfH, cx - gapHalf, cy + halfH)
                rightBar.set(cx + gapHalf, cy - halfH, cx + gapHalf + barW, cy + halfH)
                canvas.drawRoundRect(leftBar, rx, rx, paint)
                canvas.drawRoundRect(rightBar, rx, rx, paint)
            }
        }

    /** Crisp rounded square stop icon. */
    fun stop(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 14f, 14f) {
            private val rect = RectF()

            init {
                paint.style = Paint.Style.FILL
                paint.color = color
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val half = dp(4.5f)
                val rx = dp(2.0f)
                rect.set(cx - half, cy - half, cx + half, cy + half)
                canvas.drawRoundRect(rect, rx, rx, paint)
            }
        }

    /** Crisp rounded X close icon. */
    fun close(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 14f, 14f) {
            init {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.85f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = color
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val d = dp(3.8f)
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint)
            }
        }

    /** Crisp minus icon for speed stepper. */
    fun minus(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 14f, 14f) {
            init {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.85f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = color
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val half = dp(4.0f)
                canvas.drawLine(cx - half, cy, cx + half, cy, paint)
            }
        }

    /** Crisp plus icon for speed stepper. */
    fun plus(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 14f, 14f) {
            init {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.85f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = color
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val half = dp(4.0f)
                canvas.drawLine(cx - half, cy, cx + half, cy, paint)
                canvas.drawLine(cx, cy - half, cx, cy + half, paint)
            }
        }

    /** Crisp music note icon for collapsed idle pill. */
    fun musicNote(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 14f, 14f) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1.65f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                this.color = color
            }

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val stemX = cx + dp(0.6f)
                canvas.drawLine(stemX, cy + dp(3.0f), stemX, cy - dp(4.4f), strokePaint)
                canvas.drawLine(stemX, cy - dp(4.4f), stemX + dp(3.8f), cy - dp(3.1f), strokePaint)
                canvas.drawCircle(stemX - dp(2.0f), cy + dp(3.2f), dp(2.2f), fillPaint)
            }
        }

    /** Active dot indicator for collapsed playing pill. */
    fun activeDot(color: Int, density: Float): Drawable =
        object : BaseVectorDrawable(density, 12f, 12f) {
            init {
                paint.style = Paint.Style.FILL
                paint.color = color
            }

            override fun draw(canvas: Canvas) {
                canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), dp(3.5f), paint)
            }
        }
}
