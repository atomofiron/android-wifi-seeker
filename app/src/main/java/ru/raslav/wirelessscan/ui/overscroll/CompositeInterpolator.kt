package ru.raslav.wirelessscan.ui.overscroll

import android.view.animation.Interpolator
import com.google.android.material.math.MathUtils

class CompositeInterpolator private constructor(
    private val interpolators: List<Pair<Interpolator, Float>>,
) : Interpolator {

    init {
        val last = interpolators.lastOrNull()
        require(last == null || last.second >= 0f) { "end point ${last?.second} must be >= 1" }
    }

    constructor(provider: Interpolators.() -> List<Pair<Interpolator, Float>>) : this(Interpolators.provider())

    override fun getInterpolation(input: Float): Float {
        if (interpolators.isEmpty()) {
            return 1f
        }
        val input = input.coerceIn(0f, 1f)
        val index = interpolators.indexOfFirst { input <= it.second }
        val (interpolator, stop) = interpolators[index]
        val start = interpolators.getOrNull(index.dec())?.second ?: 0f
        val inner = inverseLerp(start, stop, input)
        val interpolated = interpolator.getInterpolation(inner)
        return MathUtils.lerp(start, stop, interpolated)
    }

    fun inverseLerp(start: Float, stop: Float, outer: Float): Float = when {
        outer < start -> 0f
        outer >= stop -> 1f
        else -> (outer - start) / (stop - start)
    }
}
