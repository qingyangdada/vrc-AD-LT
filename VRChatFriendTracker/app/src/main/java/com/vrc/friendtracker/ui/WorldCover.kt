package com.vrc.friendtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage

/** Panel whose left edge is a diagonal slope. */
internal class SlantedCoverShape(
    private val start: Float,
    private val slope: Float = 0.14f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path().apply {
            moveTo(size.width * (start + slope), 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width * start, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * World cover at the right edge of a card: the picture fills the panel
 * edge-to-edge (no letterboxing), its left edge is a diagonal cut and a
 * horizontal dissolve blends it into [cardColor] so the text side of the card
 * stays readable. Place it inside a [Box] before the card content.
 */
@Composable
internal fun BoxScope.WorldCoverBackdrop(
    url: String?,
    cardColor: Color,
    /** Start of the dissolve, as a fraction of the card width. */
    startFraction: Float = 0.73f,
    /** Width of the picture panel, as a fraction of the card width. */
    bandFraction: Float = 0.45f,
) {
    if (url.isNullOrBlank()) return
    Box(Modifier.matchParentSize()) {
        val bandLeft = 1f - bandFraction
        val shapeStart = ((startFraction - bandLeft) / bandFraction).coerceIn(0f, 0.6f)
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(bandFraction)
                .fillMaxHeight()
                .clip(SlantedCoverShape(shapeStart)),
        )
    }
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to cardColor,
                        startFraction to cardColor,
                        0.91f to cardColor.copy(alpha = 0.25f),
                        1f to Color.Transparent,
                    ),
                )
            )
    )
}
