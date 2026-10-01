package com.richa.assistant

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

data class GazePoint(val x: Float, val y: Float)

class FaceGazeService {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
    )

    fun detect(bitmap: Bitmap, onResult: (GazePoint?) -> Unit) {
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face == null) onResult(null)
                else {
                    val x = (face.boundingBox.centerX() / bitmap.width.toFloat()).coerceIn(0f, 1f)
                    val y = (face.boundingBox.centerY() / bitmap.height.toFloat()).coerceIn(0f, 1f)
                    onResult(GazePoint(x, y))
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun close() = detector.close()
}
