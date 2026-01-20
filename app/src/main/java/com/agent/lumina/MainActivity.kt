package com.agent.lumina

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.agent.lumina.ui.theme.LuminaTheme
import com.agent.lumina.vision.CameraPreview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.camera.core.ImageProxy
import com.google.firebase.FirebaseApp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.agent.lumina.data.GeminiLiveService
import com.agent.lumina.vision.CameraController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import com.google.accompanist.permissions.*
import android.graphics.Matrix
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val isFirebaseInitialized = try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            false
        }

        setContent {
            LuminaTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                
                // Handle Multiple Permissions
                val permissionState = rememberMultiplePermissionsState(
                    listOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.RECORD_AUDIO
                    )
                )
                
                // Initialize Service and Controller
                val geminiService = remember { 
                    GeminiLiveService(context).apply { initialize() } 
                }
                val cameraController = remember { 
                    CameraController(context, ContextCompat.getMainExecutor(context)) 
                }
                
                val connectionState by geminiService.connectionState.collectAsState()

                LaunchedEffect(Unit) {
                    if (!permissionState.permissions.all { it.status.isGranted }) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        if (permissionState.permissions.all { it.status.isGranted }) {
                            CameraPreview(
                                controller = cameraController,
                                modifier = Modifier.fillMaxSize()
                            ) { imageProxy ->
                                // Real-time frame analysis loop
                                try {
                                    val bitmap = imageProxy.toBitmap()
                                    geminiService.sendVideoFrame(bitmap)
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Frame processing failed", e)
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val textToShow = if (permissionState.shouldShowRationale) {
                                    "Lumina needs Camera and Audio permissions to see and talk with you."
                                } else {
                                    "Camera and Audio permissions are required."
                                }
                                Text(textToShow, modifier = Modifier.padding(16.dp))
                                Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                                    Text("Grant Permissions")
                                }
                            }
                        }

                        // UI Overlay for Controls
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { 
                                    if (connectionState is GeminiLiveService.ConnectionState.Connected) {
                                        geminiService.endSession()
                                    } else if (permissionState.permissions.all { it.status.isGranted }) {
                                        geminiService.startAudioConversation()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                                enabled = permissionState.permissions.all { it.status.isGranted }
                            ) {
                                Text(when(connectionState) {
                                    is GeminiLiveService.ConnectionState.Connected -> "Stop Conversation"
                                    is GeminiLiveService.ConnectionState.Connecting -> "Connecting..."
                                    else -> "Start Lumina"
                                })
                            }
                        }

                        // Status Info
                        Text(
                            text = when {
                                !isFirebaseInitialized -> "Firebase Error"
                                connectionState is GeminiLiveService.ConnectionState.Error -> 
                                    "AI Error: ${(connectionState as GeminiLiveService.ConnectionState.Error).message}"
                                else -> "Lumina Ready"
                            },
                            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Extension to convert CameraX ImageProxy to Bitmap
fun ImageProxy.toBitmap(): android.graphics.Bitmap {
    val planes = this.getPlanes()
    val yBuffer = planes[0].getBuffer() // Y
    val uBuffer = planes[1].getBuffer() // U
    val vBuffer = planes[2].getBuffer() // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.getWidth(), this.getHeight(), null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, this.getWidth(), this.getHeight()), 70, out)
    val imageBytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    
    // Rotate if needed
    val rotation = this.getImageInfo().getRotationDegrees()
    return if (rotation != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}