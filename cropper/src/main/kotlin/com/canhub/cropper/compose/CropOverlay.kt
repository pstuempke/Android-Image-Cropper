@file:OptIn(ExperimentalFoundationApi::class)

package com.canhub.cropper.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.canhub.cropper.CropImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Crop overlay that shows the crop window and handles user interactions
 */
@Composable
internal fun CropOverlay(
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    cropShape: CropImageView.CropShape,
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    aspectRatio: Float?,
    onCropChange: (Float, Float, Float, Float) -> Unit,
    onCrop: () -> Unit
) {
    val density = LocalDensity.current
    
    var isDragging by remember { mutableFloatStateOf(-1f) } // -1 = not dragging, 0-7 = corner/edge indices
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Determine which handle or area was touched
                        isDragging = getHandleIndex(
                            offset.x, offset.y,
                            cropLeft, cropTop, cropRight, cropBottom,
                            density.density
                        ).toFloat()
                    },
                    onDragEnd = {
                        isDragging = -1f
                    }
                ) { _, dragAmount ->
                    if (isDragging >= 0) {
                        val newBounds = calculateNewBounds(
                            isDragging.toInt(),
                            dragAmount.x, dragAmount.y,
                            cropLeft, cropTop, cropRight, cropBottom,
                            containerWidth, containerHeight,
                            imageWidth, imageHeight,
                            aspectRatio
                        )
                        onCropChange(newBounds.left, newBounds.top, newBounds.right, newBounds.bottom)
                    }
                }
            }
    ) {
        // Draw overlay with crop window
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCropOverlay(
                cropLeft = cropLeft,
                cropTop = cropTop,
                cropRight = cropRight,
                cropBottom = cropBottom,
                cropShape = cropShape,
                containerWidth = containerWidth,
                containerHeight = containerHeight
            )
        }
        
        // Corner handles
        if (cropShape == CropImageView.CropShape.RECTANGLE) {
            CropHandle(cropLeft, cropTop, 0)
            CropHandle(cropRight, cropTop, 1)
            CropHandle(cropRight, cropBottom, 2)
            CropHandle(cropLeft, cropBottom, 3)
        }
        
        // Center for double tap to crop
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        ((cropLeft + cropRight) / 2 - with(density) { 24.dp.toPx() } / 2).roundToInt(),
                        ((cropTop + cropBottom) / 2 - with(density) { 24.dp.toPx() } / 2).roundToInt()
                    )
                }
                .size(24.dp)
                .combinedClickable(
                    onDoubleClick = { onCrop() }
                ) { }
        )
    }
}

/**
 * Individual crop handle for resizing the crop window
 */
@Composable
private fun CropHandle(x: Float, y: Float, index: Int) {
    val density = LocalDensity.current
    val handleSize = 20.dp
    
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (x - with(density) { handleSize.toPx() } / 2).roundToInt(),
                    (y - with(density) { handleSize.toPx() } / 2).roundToInt()
                )
            }
            .size(handleSize)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color.Black, CircleShape)
    )
}

/**
 * Draws the crop overlay with dimmed area outside the crop window
 */
private fun DrawScope.drawCropOverlay(
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    cropShape: CropImageView.CropShape,
    containerWidth: Float,
    containerHeight: Float
) {
    // Draw dimmed background
    val overlayColor = Color.Black.copy(alpha = 0.5f)
    
    when (cropShape) {
        CropImageView.CropShape.RECTANGLE -> {
            // Draw background everywhere except crop rectangle
            val cropRect = Rect(
                offset = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop)
            )
            
            val path = Path().apply {
                addRect(Rect(Offset.Zero, Size(containerWidth, containerHeight)))
                addRect(cropRect)
            }
            
            clipPath(path, clipOp = ClipOp.Difference) {
                drawRect(overlayColor)
            }
            
            // Draw crop window border
            drawRect(
                color = Color.White,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
            
            // Draw grid lines
            val cropWidth = cropRight - cropLeft
            val cropHeight = cropBottom - cropTop
            
            // Vertical lines
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(cropLeft + cropWidth / 3, cropTop),
                end = Offset(cropLeft + cropWidth / 3, cropBottom),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(cropLeft + 2 * cropWidth / 3, cropTop),
                end = Offset(cropLeft + 2 * cropWidth / 3, cropBottom),
                strokeWidth = 1.dp.toPx()
            )
            
            // Horizontal lines
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(cropLeft, cropTop + cropHeight / 3),
                end = Offset(cropRight, cropTop + cropHeight / 3),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(cropLeft, cropTop + 2 * cropHeight / 3),
                end = Offset(cropRight, cropTop + 2 * cropHeight / 3),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        CropImageView.CropShape.OVAL -> {
            // Draw background everywhere except crop oval
            val centerX = (cropLeft + cropRight) / 2
            val centerY = (cropTop + cropBottom) / 2
            val radiusX = (cropRight - cropLeft) / 2
            val radiusY = (cropBottom - cropTop) / 2
            
            val path = Path().apply {
                addRect(Rect(Offset.Zero, Size(containerWidth, containerHeight)))
                addOval(Rect(
                    offset = Offset(cropLeft, cropTop),
                    size = Size(cropRight - cropLeft, cropBottom - cropTop)
                ))
            }
            
            clipPath(path, clipOp = ClipOp.Difference) {
                drawRect(overlayColor)
            }
            
            // Draw crop oval border
            drawOval(
                color = Color.White,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }
        
        else -> {
            // Default to rectangle for other shapes
            val cropRect = Rect(
                offset = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop)
            )
            
            val path = Path().apply {
                addRect(Rect(Offset.Zero, Size(containerWidth, containerHeight)))
                addRect(cropRect)
            }
            
            clipPath(path, clipOp = ClipOp.Difference) {
                drawRect(overlayColor)
            }
            
            drawRect(
                color = Color.White,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }
    }
}

/**
 * Determines which handle or area was touched
 * Returns: 0-3 for corners (TL, TR, BR, BL), 4-7 for edges (T, R, B, L), 8 for center
 */
private fun getHandleIndex(
    touchX: Float,
    touchY: Float,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    density: Float
): Int {
    val handleSize = 20 * density // 20dp in pixels
    val touchThreshold = handleSize / 2
    
    // Check corners first
    if (abs(touchX - cropLeft) <= touchThreshold && abs(touchY - cropTop) <= touchThreshold) return 0 // TL
    if (abs(touchX - cropRight) <= touchThreshold && abs(touchY - cropTop) <= touchThreshold) return 1 // TR
    if (abs(touchX - cropRight) <= touchThreshold && abs(touchY - cropBottom) <= touchThreshold) return 2 // BR
    if (abs(touchX - cropLeft) <= touchThreshold && abs(touchY - cropBottom) <= touchThreshold) return 3 // BL
    
    // Check edges
    if (abs(touchY - cropTop) <= touchThreshold && touchX >= cropLeft && touchX <= cropRight) return 4 // Top
    if (abs(touchX - cropRight) <= touchThreshold && touchY >= cropTop && touchY <= cropBottom) return 5 // Right
    if (abs(touchY - cropBottom) <= touchThreshold && touchX >= cropLeft && touchX <= cropRight) return 6 // Bottom
    if (abs(touchX - cropLeft) <= touchThreshold && touchY >= cropTop && touchY <= cropBottom) return 7 // Left
    
    // Check center area
    if (touchX >= cropLeft && touchX <= cropRight && touchY >= cropTop && touchY <= cropBottom) return 8 // Center
    
    return -1 // Outside crop area
}

/**
 * Calculates new crop bounds based on drag gesture
 */
private fun calculateNewBounds(
    handleIndex: Int,
    dragX: Float,
    dragY: Float,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    aspectRatio: Float?
): Rect {
    val imageOffsetX = (containerWidth - imageWidth) / 2
    val imageOffsetY = (containerHeight - imageHeight) / 2
    
    val minLeft = imageOffsetX
    val minTop = imageOffsetY
    val maxRight = imageOffsetX + imageWidth
    val maxBottom = imageOffsetY + imageHeight
    
    var newLeft = cropLeft
    var newTop = cropTop
    var newRight = cropRight
    var newBottom = cropBottom
    
    when (handleIndex) {
        0 -> { // Top-left corner
            newLeft = (cropLeft + dragX).coerceIn(minLeft, cropRight - 50)
            newTop = (cropTop + dragY).coerceIn(minTop, cropBottom - 50)
        }
        1 -> { // Top-right corner
            newRight = (cropRight + dragX).coerceIn(cropLeft + 50, maxRight)
            newTop = (cropTop + dragY).coerceIn(minTop, cropBottom - 50)
        }
        2 -> { // Bottom-right corner
            newRight = (cropRight + dragX).coerceIn(cropLeft + 50, maxRight)
            newBottom = (cropBottom + dragY).coerceIn(cropTop + 50, maxBottom)
        }
        3 -> { // Bottom-left corner
            newLeft = (cropLeft + dragX).coerceIn(minLeft, cropRight - 50)
            newBottom = (cropBottom + dragY).coerceIn(cropTop + 50, maxBottom)
        }
        4 -> { // Top edge
            newTop = (cropTop + dragY).coerceIn(minTop, cropBottom - 50)
        }
        5 -> { // Right edge
            newRight = (cropRight + dragX).coerceIn(cropLeft + 50, maxRight)
        }
        6 -> { // Bottom edge
            newBottom = (cropBottom + dragY).coerceIn(cropTop + 50, maxBottom)
        }
        7 -> { // Left edge
            newLeft = (cropLeft + dragX).coerceIn(minLeft, cropRight - 50)
        }
        8 -> { // Center - move entire crop window
            val deltaX = dragX
            val deltaY = dragY
            val cropWidth = cropRight - cropLeft
            val cropHeight = cropBottom - cropTop
            
            newLeft = (cropLeft + deltaX).coerceIn(minLeft, maxRight - cropWidth)
            newTop = (cropTop + deltaY).coerceIn(minTop, maxBottom - cropHeight)
            newRight = newLeft + cropWidth
            newBottom = newTop + cropHeight
        }
    }
    
    // Apply aspect ratio constraint if specified
    if (aspectRatio != null && handleIndex in 0..3) {
        val cropWidth = newRight - newLeft
        val cropHeight = newBottom - newTop
        val currentRatio = cropWidth / cropHeight
        
        if (currentRatio != aspectRatio) {
            when (handleIndex) {
                0, 3 -> { // Left side corners - adjust left or top
                    val newWidth = cropHeight * aspectRatio
                    newLeft = newRight - newWidth
                    if (newLeft < minLeft) {
                        newLeft = minLeft
                        newRight = newLeft + newWidth
                        val newHeight = (newRight - newLeft) / aspectRatio
                        if (handleIndex == 0) newTop = newBottom - newHeight
                        else newBottom = newTop + newHeight
                    }
                }
                1, 2 -> { // Right side corners - adjust right or bottom
                    val newWidth = cropHeight * aspectRatio
                    newRight = newLeft + newWidth
                    if (newRight > maxRight) {
                        newRight = maxRight
                        newLeft = newRight - newWidth
                        val newHeight = (newRight - newLeft) / aspectRatio
                        if (handleIndex == 1) newTop = newBottom - newHeight
                        else newBottom = newTop + newHeight
                    }
                }
            }
        }
    }
    
    return Rect(
        offset = Offset(newLeft, newTop),
        size = Size(newRight - newLeft, newBottom - newTop)
    )
}