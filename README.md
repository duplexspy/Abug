# abug (🐞)

> **Professional Offline AI Video Upscaler & Twixtor Frame Interpolation for Android**  
> **Created by:** [duplexspy](https://t.me/effectcraftport)  
> **Official Channel:** [https://t.me/effectcraftport](https://t.me/effectcraftport)

---

## 🌟 Overview

**abug** is a high-performance, 100% offline neural video enhancement studio designed for Android devices. Powered by Real-ESRGAN and RIFE (Real-Time Intermediate Flow Estimation) optical flow networks, **abug** runs full neural super-resolution and cinematic high-framerate interpolation directly on your smartphone's hardware without sending any data to remote servers.

---

## 🚀 Key Features

* **⚡ 100% Offline AI Neural Pipeline**:
  * Bundled Real-ESRGAN (2x, 4x, Anime 6B, AnimeVideo v3) super-resolution.
  * Bundled RIFE v4.6 optical flow neural interpolation (2x, 4x, 8x FPS boost & slow-motion).
  * GFPGAN facial reconstruction & ACES filmic color grading.
  * Zero server calls, zero telemetry, zero internet connection required.

* **🚀 Ultra Multi-Core Parallel Engine**:
  * **Phone Hardware Auto-Scan**: Automatically inspects CPU core topology and RAM headroom to select the maximum frames your phone can generate simultaneously (up to 8, 12, or 16 parallel frames).
  * **True Simultaneous Frame Generation**: Uses isolated worker memory slots and dedicated multi-core thread pools without fake delays or sequential bottlenecking.
  * **Live Multi-Core Telemetry Grid**: Real-time visual display showing every concurrent worker slot generating frames in parallel.

* **🪟 Interactive Split View & Freeform Zoom**:
  * Real-time before vs. after comparison slider for both photos and videos.
  * Synchronized video scrubber with play/pause.
  * Custom before and after clip selectors.
  * 1.0x to 8.0x pinch-to-zoom and two-finger pan everywhere in exact lockstep.

* **🎨 Pure Color Fidelity**:
  * Hardware `ImageFormat.YUV_420_888` plane decoding with exact row/pixel stride mapping, eliminating pink and green color pack artifacts.
  * Native box-filter chroma downsampling with NV12, NV21, and I420 encoder dispatch.

---

## 📱 Hardware Requirements

* **Architecture**: ARM64-v8a
* **Minimum Android Version**: Android 8.0 (API 26)
* **Recommended RAM**: 4 GB+ (8 GB+ recommended for Ultra Mode 8-Frame concurrency)
* **Compute Backend**: Vulkan GPU / ARM64 NEON multi-threaded CPU fallback

---

## 🛠️ Building From Source

```bash
# Clone the repository
git clone https://github.com/effectcraft/abug.git
cd abug

# Build Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📢 Community & Updates

Join the official Telegram channel for updates, model packs, presets, and community discussions:
* **Telegram Channel**: [https://t.me/effectcraftport](https://t.me/effectcraftport)
* **Author / Creator**: duplexspy

---

## 📄 License

Distributed under the Apache 2.0 License.
Real-ESRGAN and RIFE neural architectures are properties of their respective authors.
