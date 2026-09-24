package com.example.appblocker.ui.scheduler

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.appblocker.data.InternetSchedule

class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E1E1E.toInt()
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFBF00FF.toInt()
    }

    private var schedules: List<InternetSchedule> = emptyList()

    fun setSchedules(newSchedules: List<InternetSchedule>) {
        schedules = newSchedules
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2

        // Draw track
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, trackPaint)

        // Draw active intervals
        schedules.filter { it.isEnabled }.forEach { schedule ->
            val startMin = schedule.startHour * 60 + schedule.startMinute
            var endMin = schedule.endHour * 60 + schedule.endMinute
            
            if (endMin < startMin) endMin += 1440 // Overnight

            val startX = (startMin.toFloat() / 1440f) * w
            var endX = (endMin.toFloat() / 1440f) * w

            if (endX > w) {
                // Wraps around, draw part 1
                canvas.drawRect(startX, 0f, w, h, activePaint)
                // Part 2 (at the beginning)
                canvas.drawRect(0f, 0f, endX - w, h, activePaint)
            } else {
                canvas.drawRect(startX, 0f, endX, h, activePaint)
            }
        }
    }
}
