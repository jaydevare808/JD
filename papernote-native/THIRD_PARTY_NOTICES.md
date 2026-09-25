# PaperNote 3.0.0 Third-Party Notices

PaperNote includes the following third-party components in the Android build.

## SmolLM2-360M-Instruct

PaperNote bundles the quantized file:

- Model: SmolLM2-360M-Instruct-Q4_K_M.gguf
- Source: bartowski/SmolLM2-360M-Instruct-GGUF
- License: Apache License 2.0
- SHA-256: 2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2

The model is used only for local study-assistant inference. PaperNote does not send prompts or notebook data to a model server.

## llama-android / llama.cpp

- Dependency: dev.ffmpegkit-maintained:llama-android:0.1.1
- License: MIT
- Upstream includes llama.cpp and its third-party notices.
- Runtime architecture in this PaperNote build: arm64-v8a.

See the upstream project for its complete third-party notice set.

## Important

PaperNote is not affiliated with or endorsed by the SmolLM, Hugging Face, llama.cpp, or llama-android projects.

AI output is generated locally and may be incorrect. Students should verify important academic answers against their textbook, teacher, official material, or answer key.
