package com.example.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: List<List<NormalizedLandmark>> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var imageRotationDegrees: Int = 0

    private val landmarkPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 10f
    }

    private val connectionPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    fun setResults(
        handLandmarks: List<List<NormalizedLandmark>>,
        inputImageWidth: Int,
        inputImageHeight: Int,
        rotationDegrees: Int
    ) {
        this.results = handLandmarks
        this.imageWidth = inputImageWidth
        this.imageHeight = inputImageHeight
        this.imageRotationDegrees = rotationDegrees
        postInvalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (results.isEmpty()) return

        val scaleX = width.toFloat() / imageWidth.toFloat()
        val scaleY = height.toFloat() / imageHeight.toFloat()

        for (hand in results) {
            for (landmark in hand) {
                val x = landmark.x() * imageWidth * scaleX
                val y = landmark.y() * imageHeight * scaleY
                canvas.drawCircle(x, y, landmarkPaint.strokeWidth / 2, landmarkPaint)
            }

            drawConnections(canvas, hand)
        }
    }

    private fun drawConnections(canvas: Canvas, hand: List<NormalizedLandmark>) {
        val connections = listOf(
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
            Pair(5, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
            Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
            Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
            Pair(5, 9), Pair(9, 13), Pair(13, 17)
        )

        val scaleX = width.toFloat() / imageWidth.toFloat()
        val scaleY = height.toFloat() / imageHeight.toFloat()

        for ((startIdx, endIdx) in connections) {
            val start = hand[startIdx]
            val end = hand[endIdx]

            val startX = start.x() * imageWidth * scaleX
            val startY = start.y() * imageHeight * scaleY
            val endX = end.x() * imageWidth * scaleX
            val endY = end.y() * imageHeight * scaleY

            canvas.drawLine(startX, startY, endX, endY, connectionPaint)
        }
    }
}
