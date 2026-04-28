This project expects llama.cpp source at this path for native build-time use.

Setup options:
1) Preferred (reproducible): add llama.cpp as a git submodule locally:
   git submodule add https://github.com/ggerganov/llama.cpp third_party/llama.cpp
   git submodule update --init --recursive

2) Alternative: clone a compatible llama.cpp revision into this directory.

Notes:
- llama.cpp is used only during native compilation (CMake/NDK).
- llama.cpp source files are NOT packaged into APK assets.
- GGUF models are imported at runtime and are NOT bundled into APK.
