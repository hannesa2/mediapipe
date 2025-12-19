// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.mediapipe.examples.facemesh

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Size
import androidx.appcompat.widget.AppCompatImageView
import com.google.common.collect.ImmutableSet
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.solutions.facemesh.FaceMesh
import com.google.mediapipe.solutions.facemesh.FaceMeshConnections
import com.google.mediapipe.solutions.facemesh.FaceMeshResult

/** An ImageView implementation for displaying [FaceMeshResult].  */
class FaceMeshResultImageView(context: Context) : AppCompatImageView(context) {
    private var latest: Bitmap? = null

    init {
        scaleType = ScaleType.FIT_CENTER
    }

    /**
     * Sets a [FaceMeshResult] to render.
     *
     * @param result a [FaceMeshResult] object that contains the solution outputs and the input
     * [Bitmap].
     */
    fun setFaceMeshResult(result: FaceMeshResult?) {
        if (result == null) {
            return
        }
        val bmInput = result.inputBitmap()
        val width = bmInput.width
        val height = bmInput.height
        latest = Bitmap.createBitmap(width, height, bmInput.config!!)
        val canvas = Canvas(latest!!)
        val imageSize = Size(width, height)
        canvas.drawBitmap(bmInput, Matrix(), null)
        val numFaces = result.multiFaceLandmarks().size
        for (i in 0..<numFaces) {
            drawLandmarksOnCanvas(
                canvas,
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_TESSELATION,
                imageSize,
                TESSELATION_COLOR,
                TESSELATION_THICKNESS
            )
            drawLandmarksOnCanvas(
                canvas,
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_RIGHT_EYE,
                imageSize,
                RIGHT_EYE_COLOR,
                RIGHT_EYE_THICKNESS
            )
            drawLandmarksOnCanvas(
                canvas,
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_RIGHT_EYEBROW,
                imageSize,
                RIGHT_EYEBROW_COLOR,
                RIGHT_EYEBROW_THICKNESS
            )
            drawLandmarksOnCanvas(
                canvas,
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_LEFT_EYE,
                imageSize,
                LEFT_EYE_COLOR,
                LEFT_EYE_THICKNESS
            )
            drawLandmarksOnCanvas(
                canvas,
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_LEFT_EYEBROW,
                imageSize,
                LEFT_EYEBROW_COLOR,
                LEFT_EYEBROW_THICKNESS
            )
            drawLandmarksOnCanvas(
                canvas,
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_FACE_OVAL,
                imageSize,
                FACE_OVAL_COLOR,
                FACE_OVAL_THICKNESS
            )
            drawLandmarksOnCanvas(
                canvas,
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_LIPS,
                imageSize,
                LIPS_COLOR,
                LIPS_THICKNESS
            )
            if (result.multiFaceLandmarks()[i].landmarkCount
                == FaceMesh.FACEMESH_NUM_LANDMARKS_WITH_IRISES
            ) {
                drawLandmarksOnCanvas(
                    canvas,
                    result.multiFaceLandmarks()[i].landmarkList,
                    FaceMeshConnections.FACEMESH_RIGHT_IRIS,
                    imageSize,
                    RIGHT_EYE_COLOR,
                    RIGHT_EYE_THICKNESS
                )
                drawLandmarksOnCanvas(
                    canvas,
                    result.multiFaceLandmarks()[i].landmarkList,
                    FaceMeshConnections.FACEMESH_LEFT_IRIS,
                    imageSize,
                    LEFT_EYE_COLOR,
                    LEFT_EYE_THICKNESS
                )
            }
        }
    }

    /** Updates the image view with the latest [FaceMeshResult].  */
    fun update() {
        postInvalidate()
        if (latest != null) {
            setImageBitmap(latest)
        }
    }

    private fun drawLandmarksOnCanvas(
        canvas: Canvas,
        faceLandmarkList: MutableList<NormalizedLandmark>,
        connections: ImmutableSet<FaceMeshConnections.Connection>,
        imageSize: Size,
        color: Int,
        thickness: Int
    ) {
        // Draw connections.
        for (c in connections) {
            val connectionPaint = Paint()
            connectionPaint.color = color
            connectionPaint.strokeWidth = thickness.toFloat()
            val start = faceLandmarkList[c.start()]
            val end = faceLandmarkList[c.end()]
            canvas.drawLine(
                start.x * imageSize.width,
                start.y * imageSize.height,
                end.x * imageSize.width,
                end.y * imageSize.height,
                connectionPaint
            )
        }
    }

    companion object {
        private val TESSELATION_COLOR = Color.parseColor("#70C0C0C0")
        private const val TESSELATION_THICKNESS = 3 // Pixels
        private val RIGHT_EYE_COLOR = Color.parseColor("#FF3030")
        private const val RIGHT_EYE_THICKNESS = 5 // Pixels
        private val RIGHT_EYEBROW_COLOR = Color.parseColor("#FF3030")
        private const val RIGHT_EYEBROW_THICKNESS = 5 // Pixels
        private val LEFT_EYE_COLOR = Color.parseColor("#30FF30")
        private const val LEFT_EYE_THICKNESS = 5 // Pixels
        private val LEFT_EYEBROW_COLOR = Color.parseColor("#30FF30")
        private const val LEFT_EYEBROW_THICKNESS = 5 // Pixels
        private val FACE_OVAL_COLOR = Color.parseColor("#E0E0E0")
        private const val FACE_OVAL_THICKNESS = 5 // Pixels
        private val LIPS_COLOR = Color.parseColor("#E0E0E0")
        private const val LIPS_THICKNESS = 5 // Pixels
    }
}
