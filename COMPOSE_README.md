# Compose ImageCropper

The Compose ImageCropper provides a modern Jetpack Compose version of the image cropping functionality.

## Usage

### Basic Usage

```kotlin
@Composable
fun MyCropScreen() {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var cropResult by remember { mutableStateOf<CropResult?>(null) }
    
    selectedImageUri?.let { uri ->
        ImageCropper(
            imageUri = uri,
            modifier = Modifier.fillMaxSize(),
            onCropComplete = { result ->
                cropResult = result
                when (result) {
                    is CropResult.Success -> {
                        // Handle successful crop
                        val croppedBitmap = result.bitmap
                    }
                    is CropResult.Error -> {
                        // Handle error
                        val exception = result.exception
                    }
                }
            }
        )
    }
}
```

### Advanced Usage

```kotlin
ImageCropper(
    imageUri = uri,
    modifier = Modifier.fillMaxSize(),
    cropShape = CropImageView.CropShape.OVAL, // Or RECTANGLE
    aspectRatio = 1f, // Fixed 1:1 aspect ratio
    initialCropRect = Rect(50, 50, 300, 300), // Optional initial crop area
    onImageLoad = { bitmap ->
        // Called when image is loaded
    },
    onCropComplete = { result ->
        // Called when cropping is complete
    }
)
```

## Features

- **Core cropping functionality**: Load image from URI and crop it
- **Rectangle and oval crop shapes**: Support for different crop window shapes
- **Aspect ratio constraints**: Optional fixed aspect ratios (1:1, 16:9, etc.)
- **Interactive crop window**: Drag corners/edges to resize, drag center to move
- **Image manipulation**: Pinch to zoom and drag to move the image behind the crop overlay
- **Double-tap to crop**: Convenient gesture to perform the crop operation
- **Composable API**: Fully integrated with Jetpack Compose
- **Coil integration**: Uses Coil for efficient image loading

## API Reference

### ImageCropper Parameters

- `imageUri: Uri` - The URI of the image to be cropped
- `modifier: Modifier` - Modifier to be applied to the cropper
- `cropShape: CropImageView.CropShape` - Shape of the crop window (RECTANGLE, OVAL)
- `aspectRatio: Float?` - Optional fixed aspect ratio (width/height)
- `initialCropRect: Rect?` - Optional initial crop rectangle
- `onImageLoad: ((Bitmap?) -> Unit)?` - Callback when image loading completes
- `onCropComplete: ((CropResult) -> Unit)?` - Callback when cropping completes

### CropResult

Sealed class representing the result of a crop operation:

- `CropResult.Success(bitmap: Bitmap)` - Successful crop with resulting bitmap
- `CropResult.Error(exception: Exception)` - Failed crop with exception details

## Interaction

- **Resize crop window**: Drag any corner or edge of the crop window
- **Move crop window**: Drag the center area of the crop window
- **Scale image**: Pinch gesture on the image to zoom in/out (0.5x to 3.0x)
- **Move image**: Drag the image to reposition it behind the crop overlay
- **Perform crop**: Double-tap anywhere within the crop window
- **Visual feedback**: Grid lines and semi-transparent overlay show crop area

## Integration with Existing Library

The Compose ImageCropper integrates seamlessly with the existing Android Image Cropper library:

- Uses the same `CropImageView.CropShape` enum for consistency
- Reuses `BitmapUtils` for image processing
- Compatible with existing crop options and configurations
- Can be used alongside the traditional View-based cropper

## Requirements

- Android API 21+
- Jetpack Compose BOM 2024.04.01+
- Coil 2.7.0+

## Example Integration

See `SampleComposeActivity` in the sample module for a complete working example.