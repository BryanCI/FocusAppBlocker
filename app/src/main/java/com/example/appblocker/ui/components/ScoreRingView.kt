package com.example.appblocker.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.appblocker.R

class ScoreRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private val strokeWidth = 18f // Approx 6dp

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2A2A2A")
        this.strokeWidth = this@ScoreRingView.strokeWidth
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = this@ScoreRingView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val rect = RectF()

    private var animator: android.animation.ValueAnimator? = null
    private var accentColor: Int = Color.parseColor("#BF00FF")

    fun setAccentColor(color: Int) {
        accentColor = color
        invalidate()
    }

    fun setProgress(value: Int) {
        val targetProgress = value.coerceIn(0, 100).toFloat()
        animator?.cancel()
        animator = android.animation.ValueAnimator.ofFloat(progress, targetProgress).apply {
            duration = 1000
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = width / 2f
        val radius = (width - strokeWidth) / 2f
        rect.set(center - radius, center - radius, center + radius, center + radius)

        // Draw track
        canvas.drawOval(rect, trackPaint)

        // Draw progress
        if (progress > 0) {
            val startColor: Int
            val endColor: Int

            when {
                progress >= 100 -> {
                    startColor = Color.parseColor("#10B981")
                    endColor = Color.parseColor("#059669")
                }
                progress <= 20 -> {
                    startColor = Color.parseColor("#EAB308")
                    endColor = Color.parseColor("#D97706")
                }
                else -> {
                    startColor = accentColor
                    // Calculate a darker version for gradient or use ThemeManager
                    val hsv = FloatArray(3)
                    Color.colorToHSV(accentColor, hsv)
                    hsv[2] *= 0.8f // Darken by 20%
                    endColor = Color.HSVToColor(hsv)
                }
            }

            progressPaint.shader = SweepGradient(center, center, intArrayOf(startColor, endColor, startColor), floatArrayOf(0f, progress / 100f, 1f)).apply {
                val matrix = Matrix()
                matrix.postRotate(-90f, center, center)
                setLocalMatrix(matrix)
            }

            canvas.drawArc(rect, -90f, (progress / 100f) * 360f, false, progressPaint)
        }
    }
}
