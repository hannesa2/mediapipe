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

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import com.google.mediapipe.framework.TextureFrame
import com.google.mediapipe.solutioncore.CameraInput
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView
import com.google.mediapipe.solutioncore.VideoInput
import com.google.mediapipe.solutions.facemesh.FaceMesh
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions
import com.google.mediapipe.solutions.facemesh.FaceMeshResult
import java.io.IOException
import java.io.InputStream

// ContentResolver dependency
class FaceMeshActivity : ComponentActivity() {
    private var facemesh: FaceMesh? = null

    private enum class InputSource {
        UNKNOWN,
        IMAGE,
        VIDEO,
        CAMERA,
    }

    private var inputSource: InputSource = InputSource.UNKNOWN

    // Image demo UI and image loader components.
    private var imageGetter: ActivityResultLauncher<Intent?>? = null
    private var imageView: FaceMeshResultImageView? = null

    // Video demo UI and video loader components.
    private var videoInput: VideoInput? = null
    private var videoGetter: ActivityResultLauncher<Intent?>? = null

    // Live camera demo UI and camera components.
    private var cameraInput: CameraInput? = null

    private var glSurfaceView: SolutionGlSurfaceView<FaceMeshResult?>? = null

    // Container for preview display
    private var previewContainer: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Add a toggle to switch between the original face mesh and attention mesh.
        setupStaticImageDemoUiComponents()
        setupVideoDemoUiComponents()
        setupLiveDemoUiComponents()

        setContent {
            MaterialTheme {
                FaceMeshScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FaceMeshScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MediaPipe Face Mesh") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Button Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { onLoadPictureClick() }) {
                        Text("Load Picture")
                    }
                    Button(onClick = { onLoadVideoClick() }) {
                        Text("Load Video")
                    }
                    Button(onClick = { onStartCameraClick() }) {
                        Text("Start Camera")
                    }
                }

                // Preview Display Layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                id = View.generateViewId()
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                previewContainer = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (inputSource == InputSource.UNKNOWN) {
                        Text(
                            text = "Load an image, video, or use camera to see results.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    private fun onLoadPictureClick() {
        if (inputSource != InputSource.IMAGE) {
            stopCurrentPipeline()
            setupStaticImageModePipeline()
        }
        // Reads images from gallery.
        val pickImageIntent = Intent(Intent.ACTION_PICK)
        pickImageIntent.setDataAndType(MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*")
        imageGetter?.launch(pickImageIntent)
    }

    private fun onLoadVideoClick() {
        stopCurrentPipeline()
        setupStreamingModePipeline(InputSource.VIDEO)
        // Reads video from gallery.
        val pickVideoIntent = Intent(Intent.ACTION_PICK)
        pickVideoIntent.setDataAndType(MediaStore.Video.Media.INTERNAL_CONTENT_URI, "video/*")
        videoGetter?.launch(pickVideoIntent)
    }

    private fun onStartCameraClick() {
        if (inputSource == InputSource.CAMERA) {
            return
        }
        stopCurrentPipeline()
        setupStreamingModePipeline(InputSource.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        if (inputSource == InputSource.CAMERA) {
            // Restarts the camera and the opengl surface rendering.
            cameraInput = CameraInput(this)
            cameraInput!!.setNewFrameListener { textureFrame: TextureFrame? -> facemesh!!.send(textureFrame) }
            glSurfaceView!!.post { this.startCamera() }
            glSurfaceView!!.visibility = View.VISIBLE
        } else if (inputSource == InputSource.VIDEO) {
            videoInput!!.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView!!.visibility = View.GONE
            cameraInput!!.close()
        } else if (inputSource == InputSource.VIDEO) {
            videoInput!!.pause()
        }
    }

    private fun downscaleBitmap(originalBitmap: Bitmap): Bitmap {
        val aspectRatio = originalBitmap.width.toDouble() / originalBitmap.height
        var width = imageView!!.width
        var height = imageView!!.height
        if ((imageView!!.width.toDouble() / imageView!!.height) > aspectRatio) {
            width = (height * aspectRatio).toInt()
        } else {
            height = (width / aspectRatio).toInt()
        }
        return Bitmap.createScaledBitmap(originalBitmap, width, height, false)
    }

    @Throws(IOException::class)
    private fun rotateBitmap(inputBitmap: Bitmap, imageData: InputStream): Bitmap {
        val orientation =
            ExifInterface(imageData)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (orientation == ExifInterface.ORIENTATION_NORMAL) {
            return inputBitmap
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> matrix.postRotate(0f)
        }
        return Bitmap.createBitmap(
            inputBitmap, 0, 0, inputBitmap.width, inputBitmap.height, matrix, true
        )
    }

    /** Sets up the UI components for the static image demo.  */
    private fun setupStaticImageDemoUiComponents() {
        // The Intent to access gallery and read images as bitmap.
        imageGetter = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult? ->
            val resultIntent = result!!.data
            if (resultIntent != null) {
                if (result.resultCode == RESULT_OK) {
                    var bitmap: Bitmap? = null
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(this.contentResolver, resultIntent.data!!)
                            bitmap = downscaleBitmap(ImageDecoder.decodeBitmap(source))
                        } else {
                            @Suppress("DEPRECATION")
                            bitmap = downscaleBitmap(
                                MediaStore.Images.Media.getBitmap(this.contentResolver, resultIntent.data)
                            )
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Bitmap reading error:$e")
                    }
                    try {
                        val imageData = this.contentResolver.openInputStream(resultIntent.data!!)
                        bitmap = rotateBitmap(bitmap!!, imageData!!)
                    } catch (e: IOException) {
                        Log.e(TAG, "Bitmap rotation error:$e")
                    }
                    if (bitmap != null) {
                        facemesh!!.send(bitmap)
                    }
                }
            }
        }
        imageView = FaceMeshResultImageView(this)
    }

    /** Sets up core workflow for static image mode.  */
    private fun setupStaticImageModePipeline() {
        this.inputSource = InputSource.IMAGE
        // Initializes a new MediaPipe Face Mesh solution instance in the static image mode.
        facemesh =
            FaceMesh(
                this,
                FaceMeshOptions.builder()
                    .setStaticImageMode(true)
                    .setRefineLandmarks(true)
                    .setRunOnGpu(RUN_ON_GPU)
                    .build()
            )

        // Connects MediaPipe Face Mesh solution to the user-defined FaceMeshResultImageView.
        facemesh!!.setResultListener { faceMeshResult: FaceMeshResult? ->
            logNoseLandmark(faceMeshResult,  /*showPixelValues=*/true)
            imageView!!.setFaceMeshResult(faceMeshResult)
            runOnUiThread { imageView!!.update() }
        }
        facemesh!!.setErrorListener { message: String?, _: RuntimeException? -> Log.e(TAG, "MediaPipe Face Mesh error:$message") }

        // Updates the preview layout.
        runOnUiThread {
            previewContainer?.removeAllViewsInLayout()
            imageView!!.setImageDrawable(null)
            previewContainer?.addView(imageView)
            imageView!!.visibility = View.VISIBLE
        }
    }

    /** Sets up the UI components for the video demo.  */
    private fun setupVideoDemoUiComponents() {
        // The Intent to access gallery and read a video file.
        videoGetter =
            registerForActivityResult(
                StartActivityForResult()
            ) { result: ActivityResult? ->
                val resultIntent = result!!.data
                if (resultIntent != null) {
                    if (result.resultCode == RESULT_OK) {
                        glSurfaceView!!.post {
                            videoInput!!.start(
                                this,
                                resultIntent.data,
                                facemesh!!.glContext,
                                glSurfaceView!!.width,
                                glSurfaceView!!.height
                            )
                        }
                    }
                }
            }
    }

    /** Sets up the UI components for the live demo with camera input.  */
    private fun setupLiveDemoUiComponents() {
        // Button click is now handled in Compose UI
    }

    /** Sets up core workflow for streaming mode.  */
    private fun setupStreamingModePipeline(inputSource: InputSource) {
        this.inputSource = inputSource
        // Initializes a new MediaPipe Face Mesh solution instance in the streaming mode.
        facemesh = FaceMesh(
            this,
            FaceMeshOptions.builder()
                .setStaticImageMode(false)
                .setRefineLandmarks(true)
                .setRunOnGpu(RUN_ON_GPU)
                .build()
        )
        facemesh?.setErrorListener { message: String?, _: RuntimeException? -> Log.e(TAG, "MediaPipe Face Mesh error:$message") }

        if (inputSource == InputSource.CAMERA) {
            cameraInput = CameraInput(this).apply {
                setNewFrameListener { textureFrame: TextureFrame? ->
                    facemesh!!.send(textureFrame)
                }
            }
        } else if (inputSource == InputSource.VIDEO) {
            videoInput = VideoInput(this).apply {
                setNewFrameListener { textureFrame: TextureFrame? -> facemesh!!.send(textureFrame) }
            }
        }

        // Initializes a new Gl surface view with a user-defined FaceMeshResultGlRenderer.
        glSurfaceView = SolutionGlSurfaceView<FaceMeshResult?>(this, facemesh!!.glContext, facemesh!!.glMajorVersion)
        glSurfaceView?.setSolutionResultRenderer(FaceMeshResultGlRenderer())
        glSurfaceView?.setRenderInputImage(true)
        facemesh?.setResultListener { faceMeshResult: FaceMeshResult? ->
            logNoseLandmark(faceMeshResult,  /*showPixelValues=*/false)
            glSurfaceView?.setRenderData(faceMeshResult)
            glSurfaceView?.requestRender()
        }

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView?.post { this.startCamera() }
        }

        // Updates the preview layout.
        runOnUiThread {
            imageView?.visibility = View.GONE
            previewContainer?.removeAllViewsInLayout()
            previewContainer?.addView(glSurfaceView)
            glSurfaceView?.visibility = View.VISIBLE
            previewContainer?.requestLayout()
        }
    }

    private fun startCamera() {
        cameraInput?.start(
            this,
            facemesh?.glContext,
            CameraInput.CameraFacing.FRONT,
            glSurfaceView!!.width,
            glSurfaceView!!.height
        )
    }

    private fun stopCurrentPipeline() {
        cameraInput?.setNewFrameListener(null)
        cameraInput?.close()
        videoInput?.setNewFrameListener(null)
        videoInput?.close()
        if (glSurfaceView != null) {
            glSurfaceView!!.visibility = View.GONE
        }
        facemesh?.close()
    }

    private fun logNoseLandmark(result: FaceMeshResult?, showPixelValues: Boolean) {
        if (result == null || result.multiFaceLandmarks().isEmpty()) {
            return
        }
        val noseLandmark = result.multiFaceLandmarks()[0].landmarkList[1]
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            val width = result.inputBitmap().width
            val height = result.inputBitmap().height
            Log.i(TAG, "MediaPipe Face Mesh nose coordinates (pixel values): x=${noseLandmark.x * width}, y=${noseLandmark.y * height}")
        } else {
            Log.i(TAG, "MediaPipe Face Mesh nose normalized coordinates (value range: [0, 1]): x=${noseLandmark.x}, y=${noseLandmark.y}")
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        // Run the pipeline and the model inference on GPU or CPU.
        private const val RUN_ON_GPU = true
    }
}
