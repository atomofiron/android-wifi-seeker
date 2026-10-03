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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import ru.raslav.wirelessscan.utils.Orientation as LayoutOrientation

class ScanDrawable(
    color: Int,
    scanColor: Int,
    cornerRadius: Float,
    private val anchor: Int,
) : GradientDrawable(Orientation.BOTTOM_TOP, intArrayOf(color, color)) {

    private val animator = ValueAnimator.ofFloat(0f, 3f)
    private val paint = Paint()
    private val path = Path()
    private var out = 0f
    private var inn = 0f
    private var x = 0
    private var y = 0
    private var radius = 0f
    private var show = false
    private var orientation: LayoutOrientation = LayoutOrientation.Bottom

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

    fun showOrientation(new: LayoutOrientation) {
        orientation = new
        onBoundsChange(bounds)
    }

    fun showAnimation(show: Boolean) {
        if (show == this.show) return
        this.show = show
        if (show && !animator.isRunning) {
            animator.start()
        }
    }

    override fun onBoundsChange(rect: Rect) {
        super.onBoundsChange(rect)
        val (x, y) = when {
            orientation.left -> anchor to rect.height().half()
            orientation.right -> (rect.width() - anchor) to rect.height().half()
            else -> rect.width().half() to (rect.height() - anchor)
        }
        this.x = x
        this.y = y
        val horizontal = max(rect.width() - x, x).sqr()
        val vertical = max(rect.height() - y, y).sqr()
        radius = sqrt((horizontal + vertical).toDouble()).toFloat()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        val outRadius = radius * sin(out * PI / 2).toFloat()
        val innRadius = radius * (1f - cos(inn * PI / 2).toFloat())
        path.reset()
        path.addCircle(x.toFloat(), y.toFloat(), innRadius, Path.Direction.CW)
        path.addCircle(x.toFloat(), y.toFloat(), outRadius, Path.Direction.CCW)
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