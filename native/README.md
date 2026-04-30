Native integration notes live in app/src/main/cpp for Android packaging simplicity in this MVP.

## Setup
1. Ensure llama.cpp sources exist at `third_party/llama.cpp`:
   - either initialize a local submodule, or
   - clone a compatible llama.cpp revision into that directory.
2. Build from Android Studio / Gradle as usual.

If `third_party/llama.cpp` is missing, CMake stops early with an explicit setup hint.
