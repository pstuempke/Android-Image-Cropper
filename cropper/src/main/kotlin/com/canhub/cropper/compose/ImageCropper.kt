@file:OptIn(ExperimentalFoundationApi::class)

package com.canhub.cropper.compose

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.canhub.cropper.BitmapUtils
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A Composable version of the image cropper that provides core cropping functionality.
 * 
 * @param imageUri The URI of the image to be cropped
 * @param modifier Modifier to be applied to the cropper
 * @param cropShape The shape of the crop window (rectangle or oval)
 * @param aspectRatio Optional fixed aspect ratio for the crop window (width to height)
 * @param initialCropRect Optional initial crop rectangle
 * @param onImageLoad Callback when image loading is complete
 * @param onCropComplete Callback when cropping is complete with the result
 */
@Composable
fun ImageCropper(
    imageUri: Uri,
    modifier: Modifier = Modifier,
    cropShape: CropImageView.CropShape = CropImageView.CropShape.RECTANGLE,
    aspectRatio: Float? = null,
    initialCropRect: Rect? = null,
    onImageLoad: ((Bitmap?) -> Unit)? = null,
    onCropComplete: ((CropResult) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(true) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageWidth by remember { mutableFloatStateOf(0f) }
    var imageHeight by remember { mutableFloatStateOf(0f) }
    
    // Crop window state
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(0f) }
    var cropBottom by remember { mutableFloatStateOf(0f) }
    
    // Handle initial crop rect
    LaunchedEffect(initialCropRect, imageWidth, imageHeight) {
        if (initialCropRect != null && imageWidth > 0 && imageHeight > 0) {
            cropLeft = initialCropRect.left.toFloat()
            cropTop = initialCropRect.top.toFloat()
            cropRight = initialCropRect.right.toFloat()
            cropBottom = initialCropRect.bottom.toFloat()
        }
    }

    BoxWithConstraints(
        modifier = modifier
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        
        // Image display
        AsyncImage(
            model = imageUri,
            contentDescription = "Image to crop",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Loading -> {
                        isLoading = true
                    }
                    is AsyncImagePainter.State.Success -> {
                        isLoading = false
                        val drawable = state.result.drawable
                        val bitmap = BitmapUtils.getBitmap(drawable)
                        loadedBitmap = bitmap
                        
                        // Calculate image dimensions within the container
                        val intrinsicWidth = drawable.intrinsicWidth.toFloat()
                        val intrinsicHeight = drawable.intrinsicHeight.toFloat()
                        val containerWidthPx = with(density) { containerWidth.toPx() }
                        val containerHeightPx = with(density) { containerHeight.toPx() }
                        
                        val scale = minOf(
                            containerWidthPx / intrinsicWidth,
                            containerHeightPx / intrinsicHeight
                        )
                        
                        imageWidth = intrinsicWidth * scale
                        imageHeight = intrinsicHeight * scale
                        
                        // Set initial crop window to cover the entire image if not set
                        if (cropLeft == 0f && cropTop == 0f && cropRight == 0f && cropBottom == 0f) {
                            val imageOffsetX = (containerWidthPx - imageWidth) / 2
                            val imageOffsetY = (containerHeightPx - imageHeight) / 2
                            
                            if (aspectRatio != null) {
                                // Calculate crop size based on aspect ratio
                                val cropWidth = minOf(imageWidth, imageHeight * aspectRatio)
                                val cropHeight = cropWidth / aspectRatio
                                
                                cropLeft = imageOffsetX + (imageWidth - cropWidth) / 2
                                cropTop = imageOffsetY + (imageHeight - cropHeight) / 2
                                cropRight = cropLeft + cropWidth
                                cropBottom = cropTop + cropHeight
                            } else {
                                cropLeft = imageOffsetX
                                cropTop = imageOffsetY
                                cropRight = imageOffsetX + imageWidth
                                cropBottom = imageOffsetY + imageHeight
                            }
                        }
                        
                        onImageLoad?.invoke(bitmap)
                    }
                    is AsyncImagePainter.State.Error -> {
                        isLoading = false
                        onImageLoad?.invoke(null)
                    }
                    else -> {}
                }
            }
        )
        
        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Crop overlay when image is loaded
        if (!isLoading && imageWidth > 0 && imageHeight > 0) {
            CropOverlay(
                cropLeft = cropLeft,
                cropTop = cropTop,
                cropRight = cropRight,
                cropBottom = cropBottom,
                cropShape = cropShape,
                containerWidth = with(density) { containerWidth.toPx() },
                containerHeight = with(density) { containerHeight.toPx() },
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                aspectRatio = aspectRatio,
                onCropChange = { left, top, right, bottom ->
                    cropLeft = left
                    cropTop = top
                    cropRight = right
                    cropBottom = bottom
                },
                onCrop = {
                    coroutineScope.launch {
                        loadedBitmap?.let { bitmap ->
                            val result = cropBitmap(
                                bitmap = bitmap,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                containerWidth = with(density) { containerWidth.toPx() },
                                containerHeight = with(density) { containerHeight.toPx() }
                            )
                            onCropComplete?.invoke(result)
                        }
                    }
                }
            )
        }
    }
}

/**
 * Crops the bitmap based on the crop window coordinates
 */
private suspend fun cropBitmap(
    bitmap: Bitmap,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    imageWidth: Float,
    imageHeight: Float,
    containerWidth: Float,
    containerHeight: Float
): CropResult = withContext(Dispatchers.Default) {
    try {
        // Calculate the image offset within the container
        val imageOffsetX = (containerWidth - imageWidth) / 2
        val imageOffsetY = (containerHeight - imageHeight) / 2
        
        // Convert crop coordinates to image coordinates
        val cropImageLeft = ((cropLeft - imageOffsetX) / imageWidth * bitmap.width).coerceIn(0f, bitmap.width.toFloat())
        val cropImageTop = ((cropTop - imageOffsetY) / imageHeight * bitmap.height).coerceIn(0f, bitmap.height.toFloat())
        val cropImageRight = ((cropRight - imageOffsetX) / imageWidth * bitmap.width).coerceIn(0f, bitmap.width.toFloat())
        val cropImageBottom = ((cropBottom - imageOffsetY) / imageHeight * bitmap.height).coerceIn(0f, bitmap.height.toFloat())
        
        val cropWidth = (cropImageRight - cropImageLeft).roundToInt()
        val cropHeight = (cropImageBottom - cropImageTop).roundToInt()
        
        if (cropWidth > 0 && cropHeight > 0) {
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                cropImageLeft.roundToInt(),
                cropImageTop.roundToInt(),
                cropWidth,
                cropHeight
            )
            CropResult.Success(croppedBitmap)
        } else {
            CropResult.Error(Exception("Invalid crop dimensions"))
        }
    } catch (e: Exception) {
        CropResult.Error(e)
    }
}

/**
 * Result of cropping operation
 */
sealed class CropResult {
    data class Success(val bitmap: Bitmap) : CropResult()
    data class Error(val exception: Exception) : CropResult()
}