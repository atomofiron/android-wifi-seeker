package ru.raslav.wirelessscan.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.R

class HeaderDropdownLayout : ConstraintLayout {

    private var animator = ValueAnimator.ofFloat(0f)
    private val expandedHeight get() = paddingTop + paddingBottom + (getChildAt(0)?.height?.toFloat() ?: 0f)
    private var callback: ((px: Float) -> Unit)? = null
    private var toExpanded = false
    private val padding = Rect()

    val scrollListener: RecyclerView.OnScrollListener = ScrollListener()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int, defStyleAttr: Int) : super(context, attrs, defStyleRes, defStyleAttr)

    init {
        animator.duration = 256
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener(Listener())
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        super.setPadding(0, 0, 0, 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!toExpanded && !animator.isRunning) {
            translationY = -expandedHeight
        }
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        padding.set(left, top, right, bottom)
        updatePadding()
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        val left = if (isRtl) end else start
        val right = if (isRtl) start else end
        padding.set(left, top, right, bottom)
        updatePadding()
    }

    private fun updatePadding() {
        (getChildAt(0) as? ViewGroup)
            ?.getChildAt(0)
            ?.setPadding(padding.left, padding.top, padding.right, padding.bottom)
    }

    fun withHorizontalLinearLayout(
        start: Boolean = false,
        top: Boolean = false,
        end: Boolean = false,
        bottom: Boolean = false,
        padding: Int = 0,
    ): LinearLayout {
        removeAllViews()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.HORIZONTAL
        val scrollView = HorizontalScrollView(context)
        scrollView.clipToPadding = false
        scrollView.horizontalScrollbarThumbDrawable = ContextCompat.getDrawable(context, R.drawable.scroll_horizontal)
        scrollView.setPadding(padding, 0, padding, 0)
        scrollView.addView(layout)
        addView(scrollView)
        updatePadding()
        scrollView.updateLayoutParams<LayoutParams> {
            startToStart = if (start || !end) PARENT_ID else NO_ID
            endToEnd = if (end || !start) PARENT_ID else NO_ID
            topToTop = if (top || !bottom) PARENT_ID else NO_ID
            bottomToBottom = if (bottom || !top) PARENT_ID else NO_ID
        }
        return layout
    }

    fun onAnim(offset: (px: Float) -> Unit) {
        callback = offset
    }

    fun toggle() = when {
        toExpanded -> collapse()
        else -> expand()
    }

    fun expand() {
        if (toExpanded) return
        toExpanded = true
        val start = -translationY / expandedHeight
        animator.cancel()
        animator.setFloatValues(start, 0f)
        animator.start()
    }

    fun collapse() {
        if (!toExpanded) return
        toExpanded = false
        val start = -translationY / expandedHeight
        animator.cancel()
        animator.setFloatValues(start, 1f)
        animator.start()
    }

    private inner class Listener : ValueAnimator.AnimatorUpdateListener {

        override fun onAnimationUpdate(animation: ValueAnimator) {
            val value = animation.animatedValue as Float
            translationY = -expandedHeight * value
            getChildAt(0).translationY = -translationY
            callback?.invoke(expandedHeight + translationY)
        }
    }

    private inner class ScrollListener : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                collapse()
            }
        }
    }
}