package com.canhub.cropper.sample

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.canhub.cropper.CropImageView
import com.canhub.cropper.compose.CropResult
import com.canhub.cropper.compose.ImageCropper
import timber.log.Timber

/**
 * Sample Activity demonstrating the Compose ImageCropper
 */
class SampleComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ImageCropperSample()
                }
            }
        }
    }
}

@Composable
fun ImageCropperSample() {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var cropResult by remember { mutableStateOf<CropResult?>(null) }
    var cropShape by remember { mutableStateOf(CropImageView.CropShape.RECTANGLE) }
    var aspectRatio by remember { mutableStateOf<Float?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        cropResult = null // Reset previous crop result
        Timber.tag("ImageCropperSample").d("Selected image: $uri")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "Compose Image Cropper Sample",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Select Image")
            }
            
            Button(
                onClick = {
                    cropShape = if (cropShape == CropImageView.CropShape.RECTANGLE) {
                        CropImageView.CropShape.OVAL
                    } else {
                        CropImageView.CropShape.RECTANGLE
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (cropShape == CropImageView.CropShape.RECTANGLE) "Rectangle" else "Oval")
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { aspectRatio = null },
                modifier = Modifier.weight(1f)
            ) {
                Text("Free")
            }
            
            Button(
                onClick = { aspectRatio = 1f },
                modifier = Modifier.weight(1f)
            ) {
                Text("1:1")
            }
            
            Button(
                onClick = { aspectRatio = 16f / 9f },
                modifier = Modifier.weight(1f)
            ) {
                Text("16:9")
            }
        }
        
        // Image Cropper
        selectedImageUri?.let { uri ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ImageCropper(
                    imageUri = uri,
                    modifier = Modifier.fillMaxSize(),
                    cropShape = cropShape,
                    aspectRatio = aspectRatio,
                    onImageLoad = { bitmap ->
                        Timber.tag("ImageCropperSample").d("Image loaded: ${bitmap?.width}x${bitmap?.height}")
                    },
                    onCropComplete = { result ->
                        cropResult = result
                        when (result) {
                            is CropResult.Success -> {
                                Timber.tag("ImageCropperSample").d("Crop successful: ${result.bitmap.width}x${result.bitmap.height}")
                            }
                            is CropResult.Error -> {
                                Timber.tag("ImageCropperSample").e(result.exception, "Crop failed")
                            }
                        }
                    }
                )
            }
        } ?: run {
            // Placeholder when no image is selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select an image to start cropping",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Crop result info
        cropResult?.let { result ->
            when (result) {
                is CropResult.Success -> {
                    Text(
                        text = "Cropped image: ${result.bitmap.width} x ${result.bitmap.height}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is CropResult.Error -> {
                    Text(
                        text = "Crop error: ${result.exception.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Instructions
        Text(
            text = "Instructions:\n• Double-tap crop window to crop\n• Drag corners/edges to resize\n• Drag center to move",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ImageCropperSamplePreview() {
    MaterialTheme {
        ImageCropperSample()
    }
}