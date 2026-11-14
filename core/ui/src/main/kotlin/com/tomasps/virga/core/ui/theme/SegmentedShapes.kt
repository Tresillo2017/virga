package com.tomasps.virga.core.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space between the segments of a grouped stack, such as the rows of a SectionCard. */
val SegmentedGroupGap: Dp = 2.dp

/** Corner on the edges where two segments meet. Small, so the stack reads as one connected shape. */
private val SegmentedInnerCorner: Dp = 4.dp

/**
 * Shape of one segment in a stack. The outer edges of the first and last segment round with [outer]; edges that
 * meet a neighbour round with [inner]. Pass a zero [inner] to make a continuous panel out of several segments.
 */
fun segmentedShape(
    isFirst: Boolean,
    isLast: Boolean,
    outer: CornerSize,
    inner: CornerSize = CornerSize(SegmentedInnerCorner),
): Shape = RoundedCornerShape(
    topStart = if (isFirst) outer else inner,
    topEnd = if (isFirst) outer else inner,
    bottomEnd = if (isLast) outer else inner,
    bottomStart = if (isLast) outer else inner,
)
