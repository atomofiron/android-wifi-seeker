package ru.raslav.wirelessscan.ui.preference

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.chip.Chip
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.colorAttr
import ru.raslav.wirelessscan.completeChildren
import ru.raslav.wirelessscan.updateMarginLayoutParams
import ru.raslav.wirelessscan.utils.FrameLayoutParams
import ru.raslav.wirelessscan.utils.LinearLayoutParams
import ru.raslav.wirelessscan.utils.MaterialAttr
import ru.raslav.wirelessscan.utils.PreferenceId
import ru.raslav.wirelessscan.utils.RelativeLayoutParams

class ChipPreference : ListPreference {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int, defStyleAttr: Int) : super(context, attrs, defStyleRes, defStyleAttr)

    private val scrollView = HorizontalScrollView(context)
    private val layout = LinearLayout(context)
    private val chipMargin = context.resources.getDimensionPixelSize(R.dimen.padding_half)

    init {
        layout.orientation = LinearLayout.HORIZONTAL
        scrollView.isHorizontalScrollBarEnabled = false
        scrollView.isHorizontalFadingEdgeEnabled = true
        scrollView.addView(layout)
        layout.updateLayoutParams<FrameLayoutParams> {
            gravity = Gravity.END
        }
    }

    override fun getSummary(): CharSequence? = null

    override fun onGetDefaultValue(array: TypedArray, index: Int): String? = array.getString(index)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.background = null
        holder.itemView.setOnClickListener(null)

        scrollView.parent ?: holder.init()
        layout.bind()
    }

    private fun PreferenceViewHolder.init() {
        val body = (itemView as ViewGroup).getChildAt(1)
        val defaultPadding = body.paddingTop
        body.updatePaddingRelative(top = defaultPadding / 2, bottom = defaultPadding / 2)
        val title = body.findViewById<TextView>(android.R.id.title)
        val iconFrame = itemView.findViewById<LinearLayout>(PreferenceId.icon_frame)
        iconFrame.run {
            val icon = getChildAt(0) as ImageView
            val iconCenter = paddingTop + icon.drawable.intrinsicHeight / 2
            val titleCenter = body.paddingTop + title.lineHeight / 2
            updateLayoutParams<LinearLayoutParams> {
                topMargin = titleCenter - iconCenter
                bottomMargin = topMargin
                gravity = Gravity.TOP
            }
        }
        val summary = itemView.findViewById<View>(android.R.id.summary)
        val container = summary.parent as ViewGroup
        container.removeView(summary)
        container.addView(scrollView)
        var vertical: Boolean? = null
        itemView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val new = body.width < title.width + layout.width
            if (new == vertical) {
                return@addOnLayoutChangeListener
            }
            vertical = new
            //body.updatePaddingRelative(top = if (vertical) defaultPadding else defaultPadding / 2)
            iconFrame.updateLayoutParams<LinearLayoutParams> {
                gravity = if (vertical) Gravity.TOP else Gravity.CENTER_VERTICAL
            }
            title.updateLayoutParams<RelativeLayoutParams> {
                removeRule(RelativeLayout.ALIGN_PARENT_TOP)
                removeRule(RelativeLayout.CENTER_VERTICAL)
                when {
                    vertical -> addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    else -> addRule(RelativeLayout.CENTER_VERTICAL)
                }
            }
            scrollView.updateLayoutParams<RelativeLayoutParams> {
                width = MATCH_PARENT
                removeRule(RelativeLayout.BELOW)
                removeRule(RelativeLayout.END_OF)
                when {
                    vertical -> addRule(RelativeLayout.BELOW, android.R.id.title)
                    else -> addRule(RelativeLayout.END_OF, android.R.id.title)
                }
            }
        }
    }

    private fun LinearLayout.bind() {
        val selected = entryValues.indexOf(value)
        completeChildren(entries, entryValues)
        if (selected < 0) return
        children.forEachIndexed { index, view ->
            view as Chip
            view.isSelected = index == selected
        }
        if (width == 0) post {
            children.find { it.isSelected }?.let {
                val offset = chipMargin * 2
                val itemLeft = it.left - offset - scrollView.scrollX
                val itemRight = it.right + offset - scrollView.scrollX
                when {
                    itemLeft < 0 -> scrollView.scrollBy(itemLeft, 0)
                    itemRight > scrollView.width -> scrollView.scrollBy(itemRight - scrollView.width, 0)
                }
            }
        }
    }

    private fun LinearLayout.completeChildren(
        entries: Array<out CharSequence>,
        entryValues: Array<out CharSequence>,
    ) = completeChildren(
        entries.size,
        factory = { Chip(context) },
        init = { index ->
            chipStrokeColor = ColorStateList.valueOf(context.colorAttr(MaterialAttr.colorOutlineVariant))
            text = entries[index]
            setOnClickListener { setValue(entryValues[index].toString()) }
            updateMarginLayoutParams {
                marginStart = if (index == 0) 0 else chipMargin
            }
        },
    )
}