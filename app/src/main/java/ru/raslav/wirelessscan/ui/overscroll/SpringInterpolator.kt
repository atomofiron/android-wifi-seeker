package ru.raslav.wirelessscan.ui.overscroll

import android.view.animation.Interpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class SpringInterpolator(
    private val dampingRatio: Float = 0.55f,
    private val cycles: Float = 2.0f,
) : Interpolator {

    override fun getInterpolation(input: Float): Float {
        val x = input.coerceIn(0f, 1f)
        when (x) {
            0f -> return 0f
            1f -> return 1f
        }
        val ratio = dampingRatio.coerceIn(0.001f, 5f)
        val natural = 2 * cycles * Math.PI.toFloat() // angular frequency
        return if (ratio < 1f) {
            val sqrt = sqrt(1f - ratio.pow(2))
            val damped = natural * sqrt // angular frequency
            val exp = exp(-ratio * natural * x)
            val cos = cos(damped * x)
            val sin = sin(damped * x)
            1f - exp * (cos + sin * ratio / sqrt)
        } else { // without hesitation
            val exp = exp(-natural * x)
            1f - exp * (1f + natural * x)
        }
    }
}

