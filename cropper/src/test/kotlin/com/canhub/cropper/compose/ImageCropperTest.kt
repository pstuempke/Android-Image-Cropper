package com.canhub.cropper.compose

import android.graphics.Bitmap
import com.canhub.cropper.BitmapUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImageCropperTest {

    @Test
    fun `CropResult Success contains bitmap`() {
        // Given
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        // When
        val result = CropResult.Success(bitmap)
        
        // Then
        assertEquals(bitmap, result.bitmap)
    }

    @Test
    fun `CropResult Error contains exception`() {
        // Given
        val exception = Exception("Test error")
        
        // When
        val result = CropResult.Error(exception)
        
        // Then
        assertEquals(exception, result.exception)
    }

    @Test
    fun `BitmapUtils getBitmap handles bitmap drawable`() {
        // Given
        val originalBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val drawable = android.graphics.drawable.BitmapDrawable(null, originalBitmap)
        
        // When
        val result = BitmapUtils.getBitmap(drawable)
        
        // Then
        assertEquals(originalBitmap, result)
    }

    @Test
    fun `BitmapUtils getBitmap handles non-bitmap drawable`() {
        // Given
        val drawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.RED)
        drawable.setBounds(0, 0, 100, 100)
        
        // When
        val result = BitmapUtils.getBitmap(drawable)
        
        // Then
        assertTrue("Result should not be null", result != null)
        assertEquals(100, result?.width)
        assertEquals(100, result?.height)
    }
}