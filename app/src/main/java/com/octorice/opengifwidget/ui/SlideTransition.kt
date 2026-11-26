package com.octorice.opengifwidget.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.octorice.opengifwidget.data.Transition

/** The widget's transitions last 500 ms (res/anim/slideshow_*). */
private const val TRANSITION_MS = 500

// Android's decelerate_quad and decelerate_cubic, as used by the widget's animations.
private val DecelerateQuad = CubicBezierEasing(0.33f, 0.67f, 0.67f, 1f)
private val DecelerateCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/** The same change from one picture to the next as the slideshow widget, for in-app previews. */
private fun spec(transition: Transition): ContentTransform = when (transition) {
    Transition.NONE -> EnterTransition.None togetherWith ExitTransition.None
    // Like the widget: the new picture fades in on top, then the old one fades out.
    Transition.FADE -> fadeIn(tween(TRANSITION_MS, easing = DecelerateQuad)) togetherWith
        fadeOut(tween(TRANSITION_MS, delayMillis = TRANSITION_MS))
    // The new picture comes in from the right and pushes the old one out to the left.
    Transition.SLIDE -> slideInHorizontally(tween(TRANSITION_MS, easing = DecelerateCubic)) { it } togetherWith
        slideOutHorizontally(tween(TRANSITION_MS, easing = DecelerateCubic)) { -it }
}

/**
 * Shows [target] with [content]; whenever [target] changes, the old one goes away with [transition]
 * (exactly like the slideshow widget on the home screen).
 */
@Composable
fun <T> SlideTransition(
    target: T,
    transition: Transition,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = { spec(transition) },
        label = "slide"
    ) { content(it) }
}
