package ru.raslav.wirelessscan.ui.drawable

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.animation.LinearInterpolator
import ru.raslav.wirelessscan.half
import ru.raslav.wirelessscan.sqr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class ScanDrawable(
    color: Int,
    scanColor: Int,
    cornerRadius: Float,
) : GradientDrawable(Orientation.BOTTOM_TOP, intArrayOf(color, color)) {

    private val animator = ValueAnimator.ofFloat(0f, 3f)
    private val paint = Paint()
    private val path = Path()
    private var out = 0f
    private var inn = 0f
    private var radius = 0f
    private var show = false

    init {
        this.cornerRadius = cornerRadius

        animator.duration = 1000
        animator.interpolator = LinearInterpolator()
        animator.repeatMode = ValueAnimator.RESTART
        animator.addUpdateListener(AnimatorListener())

        paint.isAntiAlias = true
        paint.color = scanColor
        paint.style = Paint.Style.FILL
    }

    fun showAnimation(show: Boolean) {
        if (show == this.show) return
        this.show = show
        if (show && !animator.isRunning) {
            animator.start()
        }
    }

    override fun onBoundsChange(r: Rect) {
        super.onBoundsChange(r)
        radius = sqrt((bounds.width().half().sqr() + bounds.height().half().sqr()).toDouble()).toFloat()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        val x = bounds.width() / 2f
        val y = bounds.height() / 2f
        val outRadius = radius * sin(out * PI / 2).toFloat()
        val innRadius = radius * (1f - cos(inn * PI / 2).toFloat())
        path.reset()
        path.addCircle(x, y, innRadius, Path.Direction.CW)
        path.addCircle(x, y, outRadius, Path.Direction.CCW)
        canvas.drawPath(path, paint)
        if (show && !animator.isRunning) {
            animator.start()
        }
    }

    private inner class AnimatorListener : ValueAnimator.AnimatorUpdateListener {

        override fun onAnimationUpdate(animation: ValueAnimator) {
            val value = animation.animatedValue as Float
            out = min(value, 2f) / 2
            inn = value / 3f
            invalidateSelf()
        }
    }
}