package com.agent.lumina.vision

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Stable
class CameraController(
    private val context: Context,
    private val executor: Executor
) {
    var imageCapture: ImageCapture? = null
    var isFrozen by mutableStateOf(false)
        private set

    fun freeze() {
        isFrozen = true
    }

    fun resume() {
        isFrozen = false
    }

    fun captureHighRes(
        onImageCaptured: (ImageProxy) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError(IllegalStateException("Camera not initialized"))
            return
        }

        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    onImageCaptured(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }
}

@Composable
fun CameraPreview(
    controller: CameraController,
    modifier: Modifier = Modifier,
    onFrameCaptured: (ImageProxy) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val preview = remember { Preview.Builder().build() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(480, 360))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    val imageCapture = remember { 
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build() 
    }

    // Sync controller with use cases
    LaunchedEffect(imageCapture) {
        controller.imageCapture = imageCapture
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(controller.isFrozen) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        if (controller.isFrozen) {
            // Unbind analysis to "stop watching" while keeping the last frame if possible
            // or just stop the analyzer. 
            // Truly freezing the preview usually involves capturing a bitmap and showing it.
            // For now, we unbind analysis to stop the "Live" feed to Gemini.
            cameraProvider.unbind(imageAnalysis)
        } else {
             try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                    imageCapture
                )
                preview.setSurfaceProvider(previewView.surfaceProvider)
                imageAnalysis.setAnalyzer(cameraExecutor, ThrottledAnalyzer(onFrameCaptured))
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Use case binding failed", exc)
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

private class ThrottledAnalyzer(
    private val onFrameCaptured: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastAnalyzedTimestamp = 0L
    private val frameIntervalMs = 1500L // Throttling to 1.5s ensures sequential processing without backlog

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp >= frameIntervalMs) {
            onFrameCaptured(image)
            lastAnalyzedTimestamp = currentTimestamp
        } else {
            image.close()
        }
    }
}


