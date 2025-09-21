package com.canhub.cropper

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Handler responsible for updating crop window edges based on user touch interactions.
 * This class manages different types of crop window movements including:
 * - Edge movements (horizontal/vertical)
 * - Corner movements (diagonal resizing)
 * - Center movements (position changes without resizing)
 * 
 * The handler ensures that all movements respect the image boundaries, view boundaries,
 * minimum/maximum size constraints, and aspect ratio requirements when applicable.
 * 
 * @param type The specific type of movement this handler will execute
 * @param cropWindowHandler The main handler that manages crop window state and boundaries
 * @param touchX The initial horizontal touch position used to calculate movement deltas
 * @param touchY The initial vertical touch position used to calculate movement deltas
 */
internal class CropWindowMoveHandler(
  /** The type of move operation this handler is responsible for executing */
  private val type: Type,
  /** Main crop window handler that provides access to crop window state and constraints */
  cropWindowHandler: CropWindowHandler,
  /** The initial x-coordinate of the touch event that started this movement operation */
  touchX: Float,
  /** The initial y-coordinate of the touch event that started this movement operation */
  touchY: Float,
) {

  /**
   * Enumeration of all possible crop window movement types.
   * Each type defines how the crop window should respond to touch movements.
   */
  internal enum class Type {
    /** Movement of the top-left corner handle - affects both top and left edges */
    TOP_LEFT,
    /** Movement of the top-right corner handle - affects both top and right edges */
    TOP_RIGHT,
    /** Movement of the bottom-left corner handle - affects both bottom and left edges */
    BOTTOM_LEFT,
    /** Movement of the bottom-right corner handle - affects both bottom and right edges */
    BOTTOM_RIGHT,
    /** Movement of the left edge handle - affects only the left edge */
    LEFT,
    /** Movement of the top edge handle - affects only the top edge */
    TOP,
    /** Movement of the right edge handle - affects only the right edge */
    RIGHT,
    /** Movement of the bottom edge handle - affects only the bottom edge */
    BOTTOM,
    /** Movement of the center area - translates the entire crop window without resizing */
    CENTER,
  }

  internal companion object {
    /** 
     * Calculates the aspect ratio of a rectangle given its coordinates.
     * The aspect ratio is defined as width divided by height.
     * 
     * @param left The left coordinate of the rectangle
     * @param top The top coordinate of the rectangle  
     * @param right The right coordinate of the rectangle
     * @param bottom The bottom coordinate of the rectangle
     * @return The aspect ratio (width/height) of the rectangle
     */
    internal fun calculateAspectRatio(left: Float, top: Float, right: Float, bottom: Float) =
      (right - left) / (bottom - top)
  }

  /** 
   * The minimum width in pixels that the crop window is allowed to shrink to.
   * This constraint prevents the crop window from becoming too small to be usable.
   */
  private val mMinCropWidth: Float = cropWindowHandler.getMinCropWidth()

  /** 
   * The minimum height in pixels that the crop window is allowed to shrink to.
   * This constraint prevents the crop window from becoming too small to be usable.
   */
  private val mMinCropHeight: Float = cropWindowHandler.getMinCropHeight()

  /** 
   * The maximum width in pixels that the crop window is allowed to expand to.
   * This constraint prevents the crop window from exceeding the available space.
   */
  private val mMaxCropWidth: Float = cropWindowHandler.getMaxCropWidth()

  /** 
   * The maximum height in pixels that the crop window is allowed to expand to.
   * This constraint prevents the crop window from exceeding the available space.
   */
  private val mMaxCropHeight: Float = cropWindowHandler.getMaxCropHeight()

  /**
   * Stores the offset between the exact touch location and the exact handle location.
   * 
   * When a user touches near a handle, there may be a small distance between the touch point
   * and the actual handle center. This offset is calculated and stored to ensure that when
   * the user drags the handle, it doesn't suddenly "jump" to align with the touch point.
   * Instead, the relative position is maintained throughout the drag operation.
   * 
   * The offset is calculated once when the touch begins and is used to adjust all subsequent
   * touch coordinates during the drag operation.
   */
  private val mTouchOffset = PointF(0f, 0f)

  init {
    calculateTouchOffset(cropWindowHandler.getRect(), touchX, touchY)
  }

  /**
   * Primary method that handles all crop window movements and resizing operations.
   * 
   * This method processes touch movements and updates the crop window accordingly based on the
   * movement type (center, edge, or corner). It ensures that all changes respect various constraints:
   * - Image boundaries (crop window cannot extend beyond the image)
   * - View boundaries (crop window cannot extend beyond the view area)  
   * - Size constraints (minimum and maximum width/height limits)
   * - Aspect ratio constraints (when fixed aspect ratio is enabled)
   * 
   * The method works in two phases:
   * 1. Apply the primary movement based on the movement type
   * 2. Adjust secondary edges to maintain constraints and aspect ratios
   * 
   * @param rect The current crop window rectangle that will be modified
   * @param x The new x-coordinate of the touch position
   * @param y The new y-coordinate of the touch position  
   * @param bounds The bounding rectangle of the image being cropped
   * @param viewWidth The width of the view containing the crop overlay
   * @param viewHeight The height of the view containing the crop overlay
   * @param snapMargin The distance in pixels within which edges will snap to boundaries
   * @param fixedAspectRatio Whether the crop window must maintain a fixed aspect ratio
   * @param aspectRatio The target aspect ratio to maintain (width/height) when fixedAspectRatio is true
   */
  fun move(
    rect: RectF,
    x: Float,
    y: Float,
    bounds: RectF,
    viewWidth: Int,
    viewHeight: Int,
    snapMargin: Float,
    fixedAspectRatio: Boolean,
    aspectRatio: Float,
  ) {
    // Adjust the coordinates for the finger position's offset (i.e. the
    // distance from the initial touch to the precise handle location).
    // We want to maintain the initial touch's distance to the pressed
    // handle so that the crop window size does not "jump".
    val adjX = x + mTouchOffset.x
    val adjY = y + mTouchOffset.y
    if (type == Type.CENTER) {
      moveCenter(
        rect = rect,
        x = adjX,
        y = adjY,
        bounds = bounds,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        snapRadius = snapMargin,
      )
    } else {
      if (fixedAspectRatio) {
        moveSizeWithFixedAspectRatio(
          rect = rect,
          x = adjX,
          y = adjY,
          bounds = bounds,
          viewWidth = viewWidth,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
        )
      } else {
        moveSizeWithFreeAspectRatio(
          rect = rect,
          x = adjX,
          y = adjY,
          bounds = bounds,
          viewWidth = viewWidth,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
        )
      }
    }
  }

  /**
   * Calculates the offset of the touch point from the precise location of the specified handle.<br></br>
   * Save these values in a member variable since we want to maintain this offset as we drag the
   * handle.
   */
  private fun calculateTouchOffset(rect: RectF, touchX: Float, touchY: Float) {
    var touchOffsetX = 0f
    var touchOffsetY = 0f
    when (type) {
      Type.TOP_LEFT -> {
        touchOffsetX = rect.left - touchX
        touchOffsetY = rect.top - touchY
      }
      Type.TOP_RIGHT -> {
        touchOffsetX = rect.right - touchX
        touchOffsetY = rect.top - touchY
      }
      Type.BOTTOM_LEFT -> {
        touchOffsetX = rect.left - touchX
        touchOffsetY = rect.bottom - touchY
      }
      Type.BOTTOM_RIGHT -> {
        touchOffsetX = rect.right - touchX
        touchOffsetY = rect.bottom - touchY
      }
      Type.LEFT -> {
        touchOffsetX = rect.left - touchX
        touchOffsetY = 0f
      }
      Type.TOP -> {
        touchOffsetX = 0f
        touchOffsetY = rect.top - touchY
      }
      Type.RIGHT -> {
        touchOffsetX = rect.right - touchX
        touchOffsetY = 0f
      }
      Type.BOTTOM -> {
        touchOffsetX = 0f
        touchOffsetY = rect.bottom - touchY
      }
      Type.CENTER -> {
        touchOffsetX = rect.centerX() - touchX
        touchOffsetY = rect.centerY() - touchY
      }
    }
    mTouchOffset.x = touchOffsetX
    mTouchOffset.y = touchOffsetY
  }

  /** 
   * Center move only changes the position of the crop window without changing the size.
   * This method ensures that the crop window cannot be moved completely outside the overlay bounds.
   * The left edge cannot move more to the right than the overlay's left edge, same for right, top, and bottom.
   */
  private fun moveCenter(
    rect: RectF,
    x: Float,
    y: Float,
    bounds: RectF,
    viewWidth: Int,
    viewHeight: Int,
    snapRadius: Float,
  ) {
    // Calculate the desired movement delta from current center to new position
    var dx = x - rect.centerX()
    var dy = y - rect.centerY()
    
    // Calculate the crop window width and height for boundary calculations
    val cropWidth = rect.width()
    val cropHeight = rect.height()
    
    // Constrain horizontal movement to prevent crop window from moving outside bounds
    // Left edge constraint: rect.left + dx >= bounds.left
    // Right edge constraint: rect.right + dx <= bounds.right
    val minDx = bounds.left - rect.left  // Minimum dx to keep left edge within bounds
    val maxDx = bounds.right - rect.right  // Maximum dx to keep right edge within bounds
    
    // Also constrain to view boundaries (0 to viewWidth)
    val minViewDx = 0f - rect.left  // Minimum dx to keep left edge within view
    val maxViewDx = viewWidth.toFloat() - rect.right  // Maximum dx to keep right edge within view
    
    // Apply the most restrictive constraints
    dx = max(dx, max(minDx, minViewDx))  // Prevent moving too far left
    dx = min(dx, min(maxDx, maxViewDx))  // Prevent moving too far right
    
    // Constrain vertical movement to prevent crop window from moving outside bounds
    // Top edge constraint: rect.top + dy >= bounds.top
    // Bottom edge constraint: rect.bottom + dy <= bounds.bottom
    val minDy = bounds.top - rect.top  // Minimum dy to keep top edge within bounds
    val maxDy = bounds.bottom - rect.bottom  // Maximum dy to keep bottom edge within bounds
    
    // Also constrain to view boundaries (0 to viewHeight)
    val minViewDy = 0f - rect.top  // Minimum dy to keep top edge within view
    val maxViewDy = viewHeight.toFloat() - rect.bottom  // Maximum dy to keep bottom edge within view
    
    // Apply the most restrictive constraints
    dy = max(dy, max(minDy, minViewDy))  // Prevent moving too far up
    dy = min(dy, min(maxDy, maxViewDy))  // Prevent moving too far down
    
    // Apply the constrained movement to the crop window rectangle
    rect.offset(dx, dy)
    
    // Snap edges to bounds if they're close enough (within snapRadius)
    snapEdgesToBounds(edges = rect, bounds = bounds, margin = snapRadius)
  }

  /**
   * Change the size of the crop window on the required edge (or edges for corner size move) without
   * affecting "secondary" edges.<br></br>
   * Only the primary edge(s) are fixed to stay within limits.
   */
  private fun moveSizeWithFreeAspectRatio(
    rect: RectF,
    x: Float,
    y: Float,
    bounds: RectF,
    viewWidth: Int,
    viewHeight: Int,
    snapMargin: Float,
  ) {
    when (type) {
      Type.TOP_LEFT -> {
        adjustTop(
          rect = rect,
          top = y,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          leftMoves = false,
          rightMoves = false,
        )
        adjustLeft(
          rect = rect,
          left = x,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          topMoves = false,
          bottomMoves = false,
        )
      }
      Type.TOP_RIGHT -> {
        adjustTop(
          rect = rect,
          top = y,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          leftMoves = false,
          rightMoves = false,
        )
        adjustRight(
          rect = rect,
          right = x,
          bounds = bounds,
          viewWidth = viewWidth,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          topMoves = false,
          bottomMoves = false,
        )
      }
      Type.BOTTOM_LEFT -> {
        adjustBottom(
          rect = rect,
          bottom = y,
          bounds = bounds,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          leftMoves = false,
          rightMoves = false,
        )
        adjustLeft(
          rect = rect,
          left = x,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          topMoves = false,
          bottomMoves = false,
        )
      }
      Type.BOTTOM_RIGHT -> {
        adjustBottom(
          rect = rect,
          bottom = y,
          bounds = bounds,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          leftMoves = false,
          rightMoves = false,
        )
        adjustRight(
          rect = rect,
          right = x,
          bounds = bounds,
          viewWidth = viewWidth,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          topMoves = false,
          bottomMoves = false,
        )
      }
      Type.LEFT -> adjustLeft(
        rect = rect,
        left = x,
        bounds = bounds,
        snapMargin = snapMargin,
        aspectRatio = 0f,
        topMoves = false,
        bottomMoves = false,
      )
      Type.TOP -> adjustTop(
        rect = rect,
        top = y,
        bounds = bounds,
        snapMargin = snapMargin,
        aspectRatio = 0f,
        leftMoves = false,
        rightMoves = false,
      )
      Type.RIGHT -> adjustRight(
        rect = rect,
        right = x,
        bounds = bounds,
        viewWidth = viewWidth,
        snapMargin = snapMargin,
        aspectRatio = 0f,
        topMoves = false,
        bottomMoves = false,
      )
      Type.BOTTOM -> adjustBottom(
        rect = rect,
        bottom = y,
        bounds = bounds,
        viewHeight = viewHeight,
        snapMargin = snapMargin,
        aspectRatio = 0f,
        leftMoves = false,
        rightMoves = false,
      )
      Type.CENTER -> {
      }
    }
  }

  /**
   * Change the size of the crop window on the required "primary" edge WITH affect to relevant
   * "secondary" edge via aspect ratio.<br></br>
   * Example: change in the left edge (primary) will affect top and bottom edges (secondary) to
   * preserve the given aspect ratio.
   */
  private fun moveSizeWithFixedAspectRatio(
    rect: RectF,
    x: Float,
    y: Float,
    bounds: RectF,
    viewWidth: Int,
    viewHeight: Int,
    snapMargin: Float,
    aspectRatio: Float,
  ) {
    when (type) {
      Type.TOP_LEFT ->
        if (calculateAspectRatio(x, y, rect.right, rect.bottom) < aspectRatio) {
          adjustTop(
            rect = rect,
            top = y,
            bounds = bounds,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            leftMoves = true,
            rightMoves = false,
          )
          adjustLeftByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        } else {
          adjustLeft(
            rect = rect,
            left = x,
            bounds = bounds,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            topMoves = true,
            bottomMoves = false,
          )
          adjustTopByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        }
      Type.TOP_RIGHT ->
        if (calculateAspectRatio(
            left = rect.left,
            top = y,
            right = x,
            bottom = rect.bottom,
          ) < aspectRatio
        ) {
          adjustTop(
            rect = rect,
            top = y,
            bounds = bounds,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            leftMoves = false,
            rightMoves = true,
          )
          adjustRightByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        } else {
          adjustRight(
            rect = rect,
            right = x,
            bounds = bounds,
            viewWidth = viewWidth,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            topMoves = true,
            bottomMoves = false,
          )
          adjustTopByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        }
      Type.BOTTOM_LEFT ->
        if (calculateAspectRatio(
            left = x,
            top = rect.top,
            right = rect.right,
            bottom = y,
          ) < aspectRatio
        ) {
          adjustBottom(
            rect = rect,
            bottom = y,
            bounds = bounds,
            viewHeight = viewHeight,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            leftMoves = true,
            rightMoves = false,
          )
          adjustLeftByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        } else {
          adjustLeft(
            rect = rect,
            left = x,
            bounds = bounds,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            topMoves = false,
            bottomMoves = true,
          )
          adjustBottomByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        }
      Type.BOTTOM_RIGHT ->
        if (calculateAspectRatio(
            left = rect.left,
            top = rect.top,
            right = x,
            bottom = y,
          ) < aspectRatio
        ) {
          adjustBottom(
            rect = rect,
            bottom = y,
            bounds = bounds,
            viewHeight = viewHeight,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            leftMoves = false,
            rightMoves = true,
          )
          adjustRightByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        } else {
          adjustRight(
            rect = rect,
            right = x,
            bounds = bounds,
            viewWidth = viewWidth,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            topMoves = false,
            bottomMoves = true,
          )
          adjustBottomByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        }
      Type.LEFT -> {
        adjustLeft(
          rect = rect,
          left = x,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
          topMoves = true,
          bottomMoves = true,
        )
        adjustTopBottomByAspectRatio(
          rect = rect,
          bounds = bounds,
          aspectRatio = aspectRatio,
        )
      }
      Type.TOP -> {
        adjustTop(
          rect = rect,
          top = y,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
          leftMoves = true,
          rightMoves = true,
        )
        adjustLeftRightByAspectRatio(
          rect = rect,
          bounds = bounds,
          aspectRatio = aspectRatio,
        )
      }
      Type.RIGHT -> {
        adjustRight(
          rect = rect,
          right = x,
          bounds = bounds,
          viewWidth = viewWidth,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
          topMoves = true,
          bottomMoves = true,
        )
        adjustTopBottomByAspectRatio(
          rect = rect,
          bounds = bounds,
          aspectRatio = aspectRatio,
        )
      }
      Type.BOTTOM -> {
        adjustBottom(
          rect = rect,
          bottom = y,
          bounds = bounds,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
          leftMoves = true,
          rightMoves = true,
        )
        adjustLeftRightByAspectRatio(
          rect = rect,
          bounds = bounds,
          aspectRatio = aspectRatio,
        )
      }
      Type.CENTER -> {
      }
    }
  }

  /** Check if edges have gone out of bounds (including snap margin), and fix if needed. */
  private fun snapEdgesToBounds(edges: RectF, bounds: RectF, margin: Float) {
    if (edges.left < bounds.left + margin) {
      edges.offset(bounds.left - edges.left, 0f)
    }

    if (edges.top < bounds.top + margin) {
      edges.offset(0f, bounds.top - edges.top)
    }

    if (edges.right > bounds.right - margin) {
      edges.offset(bounds.right - edges.right, 0f)
    }

    if (edges.bottom > bounds.bottom - margin) {
      edges.offset(0f, bounds.bottom - edges.bottom)
    }
  }

  /**
   * Get the resulting x-position of the left edge of the crop window given the handle's position
   * and the image's bounding box and snap radius.
   *
   * [left] the position that the left edge is dragged to
   * [bounds] the bounding box of the image that is being cropped
   * [snapMargin] the snap distance to the image edge (in pixels)
   */
  private fun adjustLeft(
    rect: RectF,
    left: Float,
    bounds: RectF,
    snapMargin: Float,
    aspectRatio: Float,
    topMoves: Boolean,
    bottomMoves: Boolean,
  ) {
    var newLeft = left
    if (newLeft < 0) {
      newLeft /= 1.05f
      mTouchOffset.x -= newLeft / 1.1f
    }

    if (newLeft < bounds.left) mTouchOffset.x -= (newLeft - bounds.left) / 2f

    if (newLeft - bounds.left < snapMargin) newLeft = bounds.left
    // Checks if the window is too small horizontally
    if (rect.right - newLeft < mMinCropWidth) newLeft = rect.right - mMinCropWidth
    // Checks if the window is too large horizontally
    if (rect.right - newLeft > mMaxCropWidth) newLeft = rect.right - mMaxCropWidth

    if (newLeft - bounds.left < snapMargin) newLeft = bounds.left
    // check vertical bounds if aspect ratio is in play
    if (aspectRatio > 0) {
      var newHeight = (rect.right - newLeft) / aspectRatio
      // Checks if the window is too small vertically
      if (newHeight < mMinCropHeight) {
        newLeft = max(bounds.left, rect.right - mMinCropHeight * aspectRatio)
        newHeight = (rect.right - newLeft) / aspectRatio
      }
      // Checks if the window is too large vertically
      if (newHeight > mMaxCropHeight) {
        newLeft = max(bounds.left, rect.right - mMaxCropHeight * aspectRatio)
        newHeight = (rect.right - newLeft) / aspectRatio
      }
      // if top AND bottom edge moves by aspect ratio check that it is within full height bounds
      if (topMoves && bottomMoves) {
        newLeft = max(
          newLeft,
          max(bounds.left, rect.right - bounds.height() * aspectRatio),
        )
      } else {
        // if top edge moves by aspect ratio check that it is within bounds
        if (topMoves && rect.bottom - newHeight < bounds.top) {
          newLeft =
            max(bounds.left, rect.right - (rect.bottom - bounds.top) * aspectRatio)
          newHeight = (rect.right - newLeft) / aspectRatio
        }
        // if bottom edge moves by aspect ratio check that it is within bounds
        if (bottomMoves && rect.top + newHeight > bounds.bottom) {
          newLeft = max(
            newLeft,
            max(bounds.left, rect.right - (bounds.bottom - rect.top) * aspectRatio),
          )
        }
      }
    }
    rect.left = newLeft
  }

  /**
   * Get the resulting x-position of the right edge of the crop window given the handle's position
   * and the image's bounding box and snap radius.
   *
   * [right] the position that the right edge is dragged to
   * [bounds] the bounding box of the image that is being cropped
   * [viewWidth]
   * [snapMargin] the snap distance to the image edge (in pixels)
   */
  private fun adjustRight(
    rect: RectF,
    right: Float,
    bounds: RectF,
    viewWidth: Int,
    snapMargin: Float,
    aspectRatio: Float,
    topMoves: Boolean,
    bottomMoves: Boolean,
  ) {
    var newRight = right
    if (newRight > viewWidth) {
      newRight = viewWidth + (newRight - viewWidth) / 1.05f
      mTouchOffset.x -= (newRight - viewWidth) / 1.1f
    }

    if (newRight > bounds.right) mTouchOffset.x -= (newRight - bounds.right) / 2f
    // If close to the edge
    if (bounds.right - newRight < snapMargin) newRight = bounds.right
    // Checks if the window is too small horizontally
    if (newRight - rect.left < mMinCropWidth) newRight = rect.left + mMinCropWidth
    // Checks if the window is too large horizontally
    if (newRight - rect.left > mMaxCropWidth) newRight = rect.left + mMaxCropWidth
    // If close to the edge
    if (bounds.right - newRight < snapMargin) newRight = bounds.right
    // check vertical bounds if aspect ratio is in play
    if (aspectRatio > 0) {
      var newHeight = (newRight - rect.left) / aspectRatio
      // Checks if the window is too small vertically
      if (newHeight < mMinCropHeight) {
        newRight = min(bounds.right, rect.left + mMinCropHeight * aspectRatio)
        newHeight = (newRight - rect.left) / aspectRatio
      }
      // Checks if the window is too large vertically
      if (newHeight > mMaxCropHeight) {
        newRight = min(bounds.right, rect.left + mMaxCropHeight * aspectRatio)
        newHeight = (newRight - rect.left) / aspectRatio
      }
      // if top AND bottom edge moves by aspect ratio check that it is within full height bounds
      if (topMoves && bottomMoves) {
        newRight =
          min(newRight, min(bounds.right, rect.left + bounds.height() * aspectRatio))
      } else {
        // if top edge moves by aspect ratio check that it is within bounds
        if (topMoves && rect.bottom - newHeight < bounds.top) {
          newRight =
            min(bounds.right, rect.left + (rect.bottom - bounds.top) * aspectRatio)
          newHeight = (newRight - rect.left) / aspectRatio
        }
        // if bottom edge moves by aspect ratio check that it is within bounds
        if (bottomMoves && rect.top + newHeight > bounds.bottom) {
          newRight = min(
            newRight,
            min(bounds.right, rect.left + (bounds.bottom - rect.top) * aspectRatio),
          )
        }
      }
    }
    rect.right = newRight
  }

  /**
   * Get the resulting y-position of the top edge of the crop window given the handle's position and
   * the image's bounding box and snap radius.
   *
   * [top] the x-position that the top edge is dragged to
   * [bounds] the bounding box of the image that is being cropped
   * [snapMargin] the snap distance to the image edge (in pixels)
   */
  private fun adjustTop(
    rect: RectF,
    top: Float,
    bounds: RectF,
    snapMargin: Float,
    aspectRatio: Float,
    leftMoves: Boolean,
    rightMoves: Boolean,
  ) {
    var newTop = top
    if (newTop < 0) {
      newTop /= 1.05f
      mTouchOffset.y -= newTop / 1.1f
    }

    if (newTop < bounds.top) mTouchOffset.y -= (newTop - bounds.top) / 2f

    if (newTop - bounds.top < snapMargin) newTop = bounds.top
    // Checks if the window is too small vertically
    if (rect.bottom - newTop < mMinCropHeight) newTop = rect.bottom - mMinCropHeight
    // Checks if the window is too large vertically
    if (rect.bottom - newTop > mMaxCropHeight) newTop = rect.bottom - mMaxCropHeight

    if (newTop - bounds.top < snapMargin) newTop = bounds.top
    // check horizontal bounds if aspect ratio is in play
    if (aspectRatio > 0) {
      var newWidth = (rect.bottom - newTop) * aspectRatio
      // Checks if the crop window is too small horizontally due to aspect ratio adjustment
      if (newWidth < mMinCropWidth) {
        newTop = max(bounds.top, rect.bottom - mMinCropWidth / aspectRatio)
        newWidth = (rect.bottom - newTop) * aspectRatio
      }
      // Checks if the crop window is too large horizontally due to aspect ratio adjustment
      if (newWidth > mMaxCropWidth) {
        newTop = max(bounds.top, rect.bottom - mMaxCropWidth / aspectRatio)
        newWidth = (rect.bottom - newTop) * aspectRatio
      }
      // if left AND right edge moves by aspect ratio check that it is within full width bounds
      if (leftMoves && rightMoves) {
        newTop = max(newTop, max(bounds.top, rect.bottom - bounds.width() / aspectRatio))
      } else {
        // if left edge moves by aspect ratio check that it is within bounds
        if (leftMoves && rect.right - newWidth < bounds.left) {
          newTop = max(bounds.top, rect.bottom - (rect.right - bounds.left) / aspectRatio)
          newWidth = (rect.bottom - newTop) * aspectRatio
        }
        // if right edge moves by aspect ratio check that it is within bounds
        if (rightMoves && rect.left + newWidth > bounds.right) {
          newTop = max(
            newTop,
            max(bounds.top, rect.bottom - (bounds.right - rect.left) / aspectRatio),
          )
        }
      }
    }
    rect.top = newTop
  }

  /**
   * Get the resulting y-position of the bottom edge of the crop window given the handle's position
   * and the image's bounding box and snap radius.
   *
   * [bottom] the position that the bottom edge is dragged to
   * [bounds] the bounding box of the image that is being cropped
   * [viewHeight]
   * [snapMargin] the snap distance to the image edge (in pixels)
   */
  private fun adjustBottom(
    rect: RectF,
    bottom: Float,
    bounds: RectF,
    viewHeight: Int,
    snapMargin: Float,
    aspectRatio: Float,
    leftMoves: Boolean,
    rightMoves: Boolean,
  ) {
    var newBottom = bottom
    if (newBottom > viewHeight) {
      newBottom = viewHeight + (newBottom - viewHeight) / 1.05f
      mTouchOffset.y -= (newBottom - viewHeight) / 1.1f
    }

    if (newBottom > bounds.bottom) mTouchOffset.y -= (newBottom - bounds.bottom) / 2f

    if (bounds.bottom - newBottom < snapMargin) newBottom = bounds.bottom
    // Checks if the window is too small vertically
    if (newBottom - rect.top < mMinCropHeight) newBottom = rect.top + mMinCropHeight
    // Checks if the window is too small vertically
    if (newBottom - rect.top > mMaxCropHeight) newBottom = rect.top + mMaxCropHeight
    if (bounds.bottom - newBottom < snapMargin) newBottom = bounds.bottom
    // check horizontal bounds if aspect ratio is in play
    if (aspectRatio > 0) {
      var newWidth = (newBottom - rect.top) * aspectRatio
      // Checks if the window is too small horizontally
      if (newWidth < mMinCropWidth) {
        newBottom = min(bounds.bottom, rect.top + mMinCropWidth / aspectRatio)
        newWidth = (newBottom - rect.top) * aspectRatio
      }
      // Checks if the window is too large horizontally
      if (newWidth > mMaxCropWidth) {
        newBottom = min(bounds.bottom, rect.top + mMaxCropWidth / aspectRatio)
        newWidth = (newBottom - rect.top) * aspectRatio
      }
      // if left AND right edge moves by aspect ratio check that it is within full width bounds
      if (leftMoves && rightMoves) {
        newBottom =
          min(newBottom, min(bounds.bottom, rect.top + bounds.width() / aspectRatio))
      } else {
        // if left edge moves by aspect ratio check that it is within bounds
        if (leftMoves && rect.right - newWidth < bounds.left) {
          newBottom =
            min(bounds.bottom, rect.top + (rect.right - bounds.left) / aspectRatio)
          newWidth = (newBottom - rect.top) * aspectRatio
        }
        // if right edge moves by aspect ratio check that it is within bounds
        if (rightMoves && rect.left + newWidth > bounds.right) {
          newBottom = min(
            newBottom,
            min(bounds.bottom, rect.top + (bounds.right - rect.left) / aspectRatio),
          )
        }
      }
    }
    rect.bottom = newBottom
  }

  /**
   * Adjust left edge by current crop window height and the given aspect ratio, the right edge
   * remains in position while the left adjusts to keep aspect ratio to the height.
   */
  private fun adjustLeftByAspectRatio(rect: RectF, aspectRatio: Float) {
    rect.left = rect.right - rect.height() * aspectRatio
  }

  /**
   * Adjust top edge by current crop window width and the given aspect ratio, the bottom edge
   * remains in position while the top adjusts to keep aspect ratio to the width.
   */
  private fun adjustTopByAspectRatio(rect: RectF, aspectRatio: Float) {
    rect.top = rect.bottom - rect.width() / aspectRatio
  }

  /**
   * Adjust right edge by current crop window height and the given aspect ratio, the left edge
   * remains in position while the left adjusts to keep aspect ratio to the height.
   */
  private fun adjustRightByAspectRatio(rect: RectF, aspectRatio: Float) {
    rect.right = rect.left + rect.height() * aspectRatio
  }

  /**
   * Adjust bottom edge by current crop window width and the given aspect ratio, the top edge
   * remains in position while the top adjusts to keep aspect ratio to the width.
   */
  private fun adjustBottomByAspectRatio(rect: RectF, aspectRatio: Float) {
    rect.bottom = rect.top + rect.width() / aspectRatio
  }

  /**
   * Adjust left and right edges by current crop window height and the given aspect ratio, both
   * right and left edges adjusts equally relative to center to keep aspect ratio to the height.
   */
  private fun adjustLeftRightByAspectRatio(rect: RectF, bounds: RectF, aspectRatio: Float) {
    rect.inset((rect.width() - rect.height() * aspectRatio) / 2, 0f)
    if (rect.left < bounds.left) {
      rect.offset(bounds.left - rect.left, 0f)
    }

    if (rect.right > bounds.right) {
      rect.offset(bounds.right - rect.right, 0f)
    }
  }

  /**
   * Adjust top and bottom edges by current crop window width and the given aspect ratio, both top
   * and bottom edges adjusts equally relative to center to keep aspect ratio to the width.
   */
  private fun adjustTopBottomByAspectRatio(rect: RectF, bounds: RectF, aspectRatio: Float) {
    rect.inset(0f, (rect.height() - rect.width() / aspectRatio) / 2)
    if (rect.top < bounds.top) {
      rect.offset(0f, bounds.top - rect.top)
    }

    if (rect.bottom > bounds.bottom) {
      rect.offset(0f, bounds.bottom - rect.bottom)
    }
  }
}
