# Architectural decisions

1. **Thin JNI boundary** keeps llama.cpp volatility out of Kotlin.
2. **Room + DataStore** balances structured history with lightweight settings.
3. **MVVM + Flow** provides responsive, cancelable streaming UI.
4. **arm64-v8a-only** keeps NDK complexity low in MVP.

## Assumptions
- User imports valid GGUF files.
- Device has enough RAM for selected model/context.
- llama.cpp source is present under `third_party/llama.cpp`.

## MVP limitations
- No RAG/embeddings/multimodal.
- No internet fallback/inference.
- One active model at a time.
