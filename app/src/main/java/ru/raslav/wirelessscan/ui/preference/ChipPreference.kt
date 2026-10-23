package ru.raslav.wirelessscan.ui.preference

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity.END
import android.view.Gravity.START
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.VERTICAL
import androidx.annotation.IdRes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.databinding.ChipBinding
import ru.raslav.wirelessscan.databinding.PreferenceChipBinding
import com.google.android.material.chip.Chip
import ru.raslav.wirelessscan.completeChildren
import ru.raslav.wirelessscan.inflater
import ru.raslav.wirelessscan.noClip
import ru.raslav.wirelessscan.updateMarginLayoutParams
import ru.raslav.wirelessscan.utils.PreferenceId

class ChipPreference : ListPreference {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int, defStyleAttr: Int) : super(context, attrs, defStyleRes, defStyleAttr)

    private lateinit var binding: PreferenceChipBinding
    private var clickListener: OnPreferenceClickListener? = null
    private val chipMargin = context.resources.getDimensionPixelSize(R.dimen.padding_half)

    override fun getSummary(): CharSequence? = null

    override fun onGetDefaultValue(array: TypedArray, index: Int): String? = array.getString(index)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        if (clickListener == null) {
            holder.itemView.background = Color.TRANSPARENT.toDrawable() // null ruins children's visibility with itemView.outlineProvider
            holder.itemView.setOnClickListener(null)
        } else {
            holder.itemView.setOnClickListener { clickListener?.onPreferenceClick(this) }
        }
        if (!::binding.isInitialized) {
            val itemView = holder.itemView as ViewGroup
            binding = PreferenceChipBinding.inflate(context.inflater(), itemView, false)
                .init(itemView)
        }
        binding.bind()
    }

    override fun setOnPreferenceClickListener(listener: OnPreferenceClickListener?) {
        clickListener = listener
    }

    override fun getOnPreferenceClickListener(): OnPreferenceClickListener? = clickListener

    override fun persistString(value: String?) = callChangeListener(value) && super.persistString(value)

    private fun PreferenceChipBinding.init(itemView: ViewGroup): PreferenceChipBinding {
        val iconFrame = root.replaceFrom(itemView, PreferenceId.icon_frame)
        root.replaceFrom(itemView, android.R.id.title)
        iconFrame.updatePaddingRelative(top = 0, bottom = 0)
        root.noClip()
        scrollView.noClip()
        chips.noClip()
        itemView.noClip()
        itemView.removeAllViews()
        itemView.addView(root)
        root.updateLayoutParams {
            width = MATCH_PARENT
            height = WRAP_CONTENT
        }
        return PreferenceChipBinding.bind(root)
            .apply { syncLayout(itemView) }
    }

    private fun PreferenceChipBinding.syncLayout(itemView: ViewGroup) {
        var vertical: Boolean? = null
        itemView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val new = root.width < iconAndTitle.width + chips.width
            if (new != vertical) {
                vertical = new
                root.orientation = if (vertical) VERTICAL else HORIZONTAL
                scrollView.updatePaddingRelative(start = if (vertical) iconFrame.width else 0)
                scrollView.isFillViewport = !vertical
                chips.gravity = if (vertical) START else END
            }
        }
    }

    private fun ViewGroup.replaceFrom(src: ViewGroup, @IdRes id: Int): View {
        val view = src.findViewById<View>(id)
        (view.parent as ViewGroup).removeView(view)
        val stub = findViewById<View>(id)
        stub.layoutParams.width = view.layoutParams.width
        stub.layoutParams.height = view.layoutParams.height
        view.layoutParams = stub.layoutParams
        (stub.parent as ViewGroup).run {
            val index = indexOfChild(stub)
            removeView(stub)
            addView(view, index)
        }
        return view
    }

    private fun PreferenceChipBinding.bind() {
        val selected = entryValues.indexOf(value)
        chips.completeChildren(entries, entryValues)
        chips.children.forEachIndexed { index, view ->
            view as Chip
            view.isSelected = index == selected
        }
        if (root.width == 0) root.post {
            chips.children.find { it.isSelected }?.let {
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
    ) {
        val inflater = context.inflater()
        completeChildren(
            count = entries.size,
            factory = { ChipBinding.inflate(inflater, this, false).root },
            init = { index ->
                text = entries[index]
                setOnClickListener { setValue(entryValues[index].toString()) }
                updateMarginLayoutParams {
                    marginStart = if (index == 0) 0 else chipMargin
                }
            },
        )
    }
}