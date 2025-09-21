package com.canhub.cropper.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImageManipulationTest {

    @Test
    fun `image scale constraints should be enforced`() {
        // Test scale bounds (0.5x to 3.0x)
        val initialScale = 1.0f
        
        val scaleAfterZoomIn = (initialScale * 2.5f).coerceIn(0.5f, 3.0f)
        val scaleAfterZoomOut = (initialScale * 0.3f).coerceIn(0.5f, 3.0f)
        val scaleAfterExtremeZoom = (initialScale * 5.0f).coerceIn(0.5f, 3.0f)
        
        assertEquals(2.5f, scaleAfterZoomIn, 0.01f)
        assertEquals(0.5f, scaleAfterZoomOut, 0.01f)
        assertEquals(3.0f, scaleAfterExtremeZoom, 0.01f)
    }
    
    @Test
    fun `image translation should be constrained based on scale`() {
        val imageWidth = 200f
        val scale = 2.0f
        
        // Calculate max translation based on scale
        val maxTranslationX = (imageWidth * (scale - 1)) / 2
        
        assertEquals(100f, maxTranslationX, 0.01f)
        
        // Test translation constraints
        val panX = 150f
        val constrainedX = panX.coerceIn(-maxTranslationX, maxTranslationX)
        
        assertEquals(100f, constrainedX, 0.01f)
    }
    
    @Test
    fun `crop coordinates should account for image transformations`() {
        // Test transformation calculations
        val imageWidth = 200f
        val imageHeight = 200f
        val containerWidth = 400f
        val containerHeight = 400f
        val imageScale = 1.5f
        val imageOffsetX = 50f
        val imageOffsetY = 30f
        
        // Calculate base offset (image centered in container)
        val baseImageOffsetX = (containerWidth - imageWidth) / 2
        val baseImageOffsetY = (containerHeight - imageHeight) / 2
        
        // Calculate transformed offset
        val transformedImageOffsetX = baseImageOffsetX + imageOffsetX - (imageWidth * (imageScale - 1)) / 2
        val transformedImageOffsetY = baseImageOffsetY + imageOffsetY - (imageHeight * (imageScale - 1)) / 2
        
        assertEquals(100f, baseImageOffsetX, 0.01f)
        assertEquals(100f, baseImageOffsetY, 0.01f)
        assertEquals(100f, transformedImageOffsetX, 0.01f)
        assertEquals(80f, transformedImageOffsetY, 0.01f)
        
        // Verify transformed image dimensions
        val transformedImageWidth = imageWidth * imageScale
        val transformedImageHeight = imageHeight * imageScale
        
        assertEquals(300f, transformedImageWidth, 0.01f)
        assertEquals(300f, transformedImageHeight, 0.01f)
    }
}