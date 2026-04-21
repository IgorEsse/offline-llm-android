package com.example.offlinellm.nativebridge

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object LlamaNativeBridge {
    init {
        System.loadLibrary("offline_llm")
    }

    external fun initBackend(): Boolean
    external fun loadModel(path: String, contextSize: Int, threads: Int): Boolean
    external fun unloadModel()
    external fun isModelLoaded(): Boolean
    external fun stopGeneration()
    external fun getModelMetadata(path: String): String

    private external fun nativeGenerate(
        prompt: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        maxTokens: Int,
        callback: TokenCallback
    ): Boolean

    fun generate(
        prompt: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        maxTokens: Int
    ): Flow<String> = callbackFlow {
        val callback = object : TokenCallback {
            override fun onToken(token: String) {
                trySend(token)
            }

            override fun onComplete() {
                close()
            }

            override fun onError(message: String) {
                close(IllegalStateException(message))
            }
        }
        if (!nativeGenerate(prompt, temperature, topK, topP, maxTokens, callback)) {
            close(IllegalStateException("nativeGenerate failed to start"))
        }
        awaitClose { stopGeneration() }
    }
}

interface TokenCallback {
    fun onToken(token: String)
    fun onComplete()
    fun onError(message: String)
}
