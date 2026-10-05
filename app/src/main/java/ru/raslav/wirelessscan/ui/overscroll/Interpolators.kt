package ru.raslav.wirelessscan.ui.overscroll

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object Interpolators{

    private const val HALF_PI = (PI / 2).toFloat()

    val Linear = LinearInterpolator()
    val In = AccelerateInterpolator(2f)
    val Out = DecelerateInterpolator(2f)
    val InOut = AccelerateDecelerateInterpolator()
    val Bounce = BounceInterpolator()
    val SpringOut = SpringInterpolator()
    val ElasticOut = Interpolator { input -> 1 + 2f.pow(-10 * input) * sin(2 * PI * (input - 0.075f)).toFloat() }
    val SinOut = Interpolator { sin(HALF_PI * it) }
    val CosIn = Interpolator { 1 - cos(HALF_PI * it) }

    fun of(
        vararg values: Pair<Interpolator, Float>,
        final: Interpolator,
    ): List<Pair<Interpolator, Float>> = buildList(values.size.inc()) {
        addAll(values)
        add(final to 1f)
    }
}
