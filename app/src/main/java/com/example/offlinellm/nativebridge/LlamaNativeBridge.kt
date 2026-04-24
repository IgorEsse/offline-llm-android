package com.example.offlinellm.nativebridge

import android.os.SystemClock
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
    external fun countTokens(text: String): Int

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
        val chunkBuffer = StringBuilder()
        var lastEmitAt = SystemClock.elapsedRealtime()

        fun emitBuffered(force: Boolean = false) {
            if (chunkBuffer.isEmpty()) return
            val now = SystemClock.elapsedRealtime()
            val shouldEmit = force ||
                chunkBuffer.length >= 24 ||
                chunkBuffer.last().isWhitespace() ||
                chunkBuffer.last() in setOf('.', ',', '!', '?', '\n') ||
                (now - lastEmitAt) >= 80L
            if (!shouldEmit) return
            trySend(chunkBuffer.toString())
            chunkBuffer.setLength(0)
            lastEmitAt = now
        }

        val callback = object : TokenCallback {
            override fun onToken(token: String) {
                chunkBuffer.append(token)
                emitBuffered()
            }

            override fun onComplete() {
                emitBuffered(force = true)
                close()
            }

            override fun onError(message: String) {
                emitBuffered(force = true)
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
