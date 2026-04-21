# offline-llm-android (MVP)

Production-minded Android MVP for **fully offline** chat with local GGUF models using **llama.cpp + JNI/NDK**.

## Architecture overview
- `ui/`: Jetpack Compose screens (Models, Chat, Settings, Diagnostics)
- `viewmodel/`: MVVM state + actions
- `domain/`: app contracts + core models
- `data/`: Room entities/DAO + repositories + DataStore settings
- `nativebridge/`: thin Kotlin wrapper around stable JNI API
- `app/src/main/cpp/`: JNI C++ implementation
- `third_party/llama.cpp/`: llama.cpp source (recommended git submodule)
- `docs/`: architecture and extension notes

## llama.cpp integration
JNI API exposed to Kotlin:
- `initBackend()`
- `loadModel(path, contextSize, threads)`
- `unloadModel()`
- `isModelLoaded()`
- `generate(prompt, params, callback)` (streaming)
- `stopGeneration()`
- `getModelMetadata(path)`

Native code is isolated in `llama_jni.cpp`; UI never touches llama.cpp types directly.


## Repository note (PR tooling)
In this environment, PR tooling does not accept binary files.
Therefore `gradle/wrapper/gradle-wrapper.jar` is intentionally not committed.
After cloning, run `gradle wrapper --gradle-version 8.7` once to regenerate it locally.

## Build instructions (Android Studio)
1. Install Android Studio (Hedgehog+), Android SDK 34, NDK 26+, CMake 3.22.1.
2. Add llama.cpp into `third_party/llama.cpp`.
   - Suggested: git submodule
   - `git submodule add https://github.com/ggerganov/llama.cpp third_party/llama.cpp`
3. Open project in Android Studio.
4. Sync Gradle.
5. Build/run on arm64-v8a device.

## NDK/CMake notes
- ABI: **arm64-v8a only** for MVP.
- CMake links app shared library against llama.cpp target.
- Native generation runs on background thread and streams tokens via callback.

## Model import flow
1. User taps **Import GGUF model**.
2. SAF picker returns `content://` URI.
3. App validates `.gguf`, reads metadata, copies file into `files/models/`.
4. App calculates SHA-256 checksum, saves metadata to Room.
5. User sets active model.

## Privacy
- No analytics.
- No remote inference.
- No hidden network calls for generation.
- Chats and models stay local on device.

## Known limitations (MVP)
- Single-chat timeline (no multi-session UI yet)
- Metadata extraction is intentionally minimal
- Token/s metric placeholder in diagnostics
- `top_k/top_p/temp` sampler wiring may need tuning per llama.cpp revision

## Future extensions
- RAG over local files
- Voice input/output
- Multiple chat sessions with titles
- Benchmark screen (latency, token/s, memory)
