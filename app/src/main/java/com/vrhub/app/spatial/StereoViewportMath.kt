package com.vrhub.app.spatial

import kotlin.math.max

/**
 * Aspect-preserving texture crop for one Cardboard eye viewport.
 *
 * Use the returned UV rectangle in the camera/background shader. It fills the eye viewport
 * without stretching. When source and viewport aspect ratios differ, some source edges are
 * intentionally cropped; the source texture itself is never downscaled by this calculation.
 */
object StereoViewportMath {
    data class UvRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    data class EyeViewport(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    fun eyeViewports(surfaceWidth: Int, surfaceHeight: Int): Pair<EyeViewport, EyeViewport> {
        require(surfaceWidth > 1 && surfaceHeight > 0)
        val leftWidth = surfaceWidth / 2
        val rightWidth = surfaceWidth - leftWidth
        return EyeViewport(0, 0, leftWidth, surfaceHeight) to
            EyeViewport(leftWidth, 0, rightWidth, surfaceHeight)
    }

    /**
     * Returns normalized UV bounds for a center-crop ("cover") fit.
     * Coordinates use top-left origin, matching Android camera image coordinates.
     */
    fun centerCropUv(
        sourceWidth: Int,
        sourceHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): UvRect {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(viewportWidth > 0 && viewportHeight > 0)

        val sourceAspect = sourceWidth.toFloat() / sourceHeight
        val viewportAspect = viewportWidth.toFloat() / viewportHeight

        return if (sourceAspect > viewportAspect) {
            val visibleWidth = viewportAspect / sourceAspect
            val inset = (1f - visibleWidth) / 2f
            UvRect(inset, 0f, 1f - inset, 1f)
        } else {
            val visibleHeight = sourceAspect / viewportAspect
            val inset = (1f - visibleHeight) / 2f
            UvRect(0f, inset, 1f, 1f - inset)
        }
    }

    /** Maps normalized screen UV into the center-cropped source UV rectangle. */
    fun mapUv(u: Float, v: Float, crop: UvRect): Pair<Float, Float> =
        (crop.left + u * (crop.right - crop.left)) to
            (crop.top + v * (crop.bottom - crop.top))
}
