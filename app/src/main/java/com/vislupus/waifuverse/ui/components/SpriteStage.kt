package com.vislupus.waifuverse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.config.ThemeConfig
import com.vislupus.waifuverse.sprite.SpriteLoader
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The character stage: background image, a scrim and the sprite.
 *
 * The sprite is drawn as ONE static body plus the current mouth patch, both
 * scaled with the same factor and anchored to the same origin, so only the
 * mouth can change between frames. The idle "breathing" animation moves the
 * whole canvas (body and mouth together) through graphicsLayer.
 */
@Composable
fun SpriteStage(
    sprite: SpriteLoader.Sprite?,
    background: ImageBitmap?,
    mouthFrame: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.background(accent.copy(alpha = 0.25f))) {
        if (background != null) {
            Image(
                bitmap = background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Soft scrim so labels stay readable on any background.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = ThemeConfig.STAGE_SCRIM_ALPHA_TOP),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = ThemeConfig.STAGE_SCRIM_ALPHA_BOTTOM),
                    ),
                ),
        )
        if (sprite != null) {
            SpriteCanvas(
                sprite = sprite,
                mouthFrame = mouthFrame,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxSize(),
            )
        }
        overlay()
    }
}

@Composable
private fun SpriteCanvas(sprite: SpriteLoader.Sprite, mouthFrame: Int, modifier: Modifier = Modifier) {
    // The phase is read inside graphicsLayer (draw phase), so the animation
    // never recomposes the stage; it only re-draws the layer.
    val transition = rememberInfiniteTransition(label = "breathing")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AppConfig.BREATHING_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val frameIndex = mouthFrame.coerceIn(0, sprite.mouths.size - 1)
    val mouth = sprite.mouths[frameIndex]

    Canvas(
        modifier = modifier.graphicsLayer {
            // Smooth 0..1 sine wave derived from the linear phase.
            val breathing = if (AppConfig.BREATHING_ENABLED) {
                0.5f - 0.5f * sin(phase.value * 2f * PI.toFloat() - PI.toFloat() / 2f)
            } else {
                0f
            }
            val s = 1f + AppConfig.BREATHING_SCALE_AMPLITUDE * breathing
            scaleX = s
            scaleY = s
            translationY = -AppConfig.BREATHING_TRANSLATE_PX * breathing
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    ) {
        val targetHeight = size.height * AppConfig.SPRITE_HEIGHT_FRACTION
        val scale = minOf(targetHeight / sprite.height, size.width / sprite.width)
        val drawW = (sprite.width * scale).roundToInt()
        val drawH = (sprite.height * scale).roundToInt()
        val left = ((size.width - drawW) / 2f).roundToInt()
        val top = (size.height - drawH).roundToInt()

        drawImage(
            image = sprite.body,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(drawW, drawH),
            filterQuality = FilterQuality.High,
        )
        val r = sprite.mouthRect
        // Same scale and origin as the body: the patch lands exactly on the mouth.
        val mx = left + (r.left * scale).roundToInt()
        val my = top + (r.top * scale).roundToInt()
        val mw = ((r.right * scale).roundToInt() - (r.left * scale).roundToInt()).coerceAtLeast(1)
        val mh = ((r.bottom * scale).roundToInt() - (r.top * scale).roundToInt()).coerceAtLeast(1)
        drawImage(
            image = mouth,
            dstOffset = IntOffset(mx, my),
            dstSize = IntSize(mw, mh),
            filterQuality = FilterQuality.High,
        )
    }
}

/** Placeholder stage while sprites load. */
@Composable
fun EmptyStage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(200.dp).fillMaxWidth().background(Color.DarkGray))
}
