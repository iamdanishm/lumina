package com.agent.lumina.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(PublicPreviewAPI::class)
class GeminiLiveService(context: Context) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var liveSession: LiveSession? = null

    fun initialize() {
        Log.d(TAG, "Initializing GeminiLiveService...")
        scope.launch {
            try {
                val liveGenerationConfig = liveGenerationConfig {
                    responseModality = ResponseModality.AUDIO
                    speechConfig = SpeechConfig(voice = Voice("Erinome"))
                }

                val systemInstruction = Content.Builder()
                    .text("You are Lumina, a real-time visual assistant for blind users. Your goals are SAFETY and AGENCY" +
                          " CRITICAL INSTRUCTION: You must be extremely proactive. " +
                          "1. Always describe the LATEST video frame you receive. " +
                          "2. If the user asks 'what do you see?', re-evaluate the scene from scratch. Do NOT say 'it is the same' if there is any movement. " +
                          "3. Describe scene changes immediately. " +
                          "4. Keep responses very short (1-2 sentences). " +
                          "5. Focus on safety: obstacles on the floor, upcoming doors, or people nearby." +
                            "6. INTERRUPT: If you see immediate physical danger (cars, low branches, holes), shout 'STOP' and the hazard name immediately. Ignore user speech during hazards.\n")

                    .build()

                val liveModel = FirebaseAI.getInstance(Firebase.app)
                    .liveModel(
                        modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
                        generationConfig = liveGenerationConfig,
                        systemInstruction = systemInstruction
                    )

                Log.d(TAG, "Connecting to LiveModel...")
                liveSession = liveModel.connect()
                _connectionState.value = ConnectionState.Ready
                Log.d(TAG, "Service Ready with LiveGenerativeModel. Session established.")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Firebase AI", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Initialization failed")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startAudioConversation() {
        if (_connectionState.value is ConnectionState.Connected) return
        
        val session = liveSession
        if (session == null) {
            Log.e(TAG, "Session is null. Cannot start conversation.")
            return
        }

        Log.d(TAG, "Starting audio conversation...")
        scope.launch {
            try {
                _connectionState.value = ConnectionState.Connecting
                
                // startAudioConversation manages the mic and audio output internally.
                // We pass a simple handler for function calls if needed, 
                // but for now we can pass a dummy or empty handler if required, 
                // or just call it if it supports no-args/default.
                // based on Quickstart: liveSession.startAudioConversation(::handler)
                // We'll define a simple handler.
                
                session.startAudioConversation(::handleFunctionCall)
                _connectionState.value = ConnectionState.Connected
                Log.i(TAG, "Audio conversation started")

                // Trigger initial greeting and first description
                scope.launch {
                    try {
                        // The documentation confirms 'send(String)' exists as a suspend function.
                        // We try to call it directly. If it fails to compile, we'll fall back.
                        (session as? LiveSession)?.send("Hello Lumina. Greet the user and describe the current scene to get started.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send initial prompt", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start audio conversation", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Start conversation failed")
            }
        }
    }

    private fun handleFunctionCall(call: FunctionCallPart): FunctionResponsePart {
        // We are not using function calling yet, so we return an empty response or handle generic logic.
        // Quickstart returns empty JSON object if not handled.
        // Check if we can just return a dummy.
        Log.w(TAG, "Function call received but not handled: ${call.name}")
        // Using reflection or generic empty response if available? 
        // For now, we'll try to constructs a simple response.
        // If FunctionResponsePart requires specific args, we'd need to know them.
        // Assuming we can return a basic error or empty response.
        // Quickstart uses kotlinx.serialization.json.JsonObject(emptyMap())
        // We might need to add serialization dependency or just return a dummy if allowed.
        // For the sake of compilation, we'll assume we don't declare tools, so this shouldn't be called.
        // However, the API requires the handler.
        
        throw NotImplementedError("Function calling not implemented")
    }

    private val sendMutex = kotlinx.coroutines.sync.Mutex()

    fun sendVideoFrame(bitmap: Bitmap) {
        if (_connectionState.value != ConnectionState.Connected) return
        
        // Use tryLock to skip if we are already sending.
        // This is CRITICAL. Parallel sends cause a backlog in the network/server queue,
        // which leads to the model seeing old frames when the user finally asks a question.
        if (!sendMutex.tryLock()) return

        scope.launch {
            try {
                val stream = ByteArrayOutputStream()
                // Use a balanced quality to ensure the model can actually "see" details
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                val jpegBytes = stream.toByteArray()
                
                Log.d(TAG, "Sending fresh frame, size: ${jpegBytes.size} bytes. Latency is controlled.")
                liveSession?.sendVideoRealtime(InlineData(jpegBytes, "image/jpeg"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send video frame", e)
            } finally {
                sendMutex.unlock()
            }
        }
    }

    fun endSession() {
        Log.d(TAG, "Ending session...")
        scope.launch {
            try {
                liveSession?.stopAudioConversation()
                // Do we need to close the session? connect() opened it.
                // Quickstart says stopAudioConversation() in endConversation().
                // It doesn't explicitly 'close' the session object in the viewmodel, 
                // but usually good practice if we are done.
                // For now, we follow quickstart: just stop conversation.
                _connectionState.value = ConnectionState.Ready // Go back to ready
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping conversation", e)
            }
        }
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Ready : ConnectionState() // Session created, ready to start conversation
        object Connecting : ConnectionState()
        object Connected : ConnectionState() // Conversation active
        data class Error(val message: String) : ConnectionState()
    }

    companion object {
        private const val TAG = "GeminiLiveService"
    }
}
