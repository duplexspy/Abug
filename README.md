# abug (🐞)

> **Offline Open-Source AI Video Upscaler & Twixtor Frame Interpolation Studio for Mobile (Android) & Laptops**  
> **Created by:** [duplexspy](https://t.me/effectcraftport)  
> **Official Telegram:** [https://t.me/effectcraftport](https://t.me/effectcraftport)  
> **GitHub Repository:** [https://github.com/duplexspy/Abug](https://github.com/duplexspy/Abug)

---

## 🌟 Overview

**abug** is a high-performance, 100% offline and open-source neural video enhancement studio designed for mobile (Android) and laptop/desktop environments. Powered by **Real-ESRGAN** super-resolution and **RIFE** (Real-Time Intermediate Flow Estimation) optical flow networks, **abug** runs full neural super-resolution and cinematic high-framerate Twixtor interpolation directly on local device hardware without uploading any data to remote servers.

---

## 🚀 Key Features

* **⚡ 100% Offline AI Neural Pipeline**:
  * Bundled Real-ESRGAN (2x, 4x, Anime 6B, AnimeVideo v3) super-resolution.
  * Bundled RIFE v4.6 optical flow neural interpolation (2x, 4x, 8x FPS boost & slow-motion).
  * GFPGAN facial reconstruction & ACES filmic color grading.
  * Zero server calls, zero telemetry, zero internet connection required.

* **🚀 Ultra Multi-Core Parallel Engine**:
  * **Device Hardware Auto-Scan**: Automatically inspects CPU core topology and RAM headroom to select the maximum frames your device can generate simultaneously (up to 8, 12, or 16 parallel frames).
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

## 📖 How to Run (Quick Guide)

> 📘 **Full Step-by-Step Guide**: Read [**HOW_TO_RUN.md**](HOW_TO_RUN.md) for detailed instructions, screenshots, and troubleshooting.

### 📱 1. On Android Phones & Tablets
1. Download the release APK: **[abug-v1.0.0.apk](https://github.com/duplexspy/Abug/releases/download/v1.0.0/abug-v1.0.0.apk)** (74 MB).
2. Tap the downloaded `.apk` and allow installation from unknown sources if prompted.
3. Open **abug** (🐞) and enjoy 100% offline neural video upscaling and Twixtor interpolation!

### 💻 2. On Laptops & Desktops (Windows, macOS, Linux)
#### Standalone Desktop Studio (One-Click)
1. Download **[Abug-Studio.zip](https://github.com/duplexspy/Abug/releases/download/v1.0.0/Abug-Studio.zip)** (~64 MB) from GitHub Releases.
2. Extract the ZIP archive anywhere on your laptop.
3. Launch the studio:
   - **macOS (Apple Silicon M1/M2/M3/M4 & Intel)**: Double-click `RUN_MAC_STUDIO.command` (or run `bash INSTALL_ABUG_COMMAND.command` once, then type `abug` in Terminal). Opens automatically in **Google Chrome**.
   - **Windows**: Double-click `run_desktop.bat` (or run `python abug_standalone.py`).
   - **Linux**: Double-click or run `./run_desktop.sh`.
4. Opens the interactive **abug Desktop Studio** at `http://127.0.0.1:54322` in your browser.

#### Terminal CLI
```bash
# Upscale an image 4x:
python3 standalone/abug_standalone.py upscale -i input.png -o output.png -s 4

# Interpolate frames (Twixtor):
python3 standalone/abug_standalone.py twixtor -f0 frame0.png -f1 frame1.png -o interp.png -t 0.5
```

---

## 💻 Running on Laptops & Desktops

**abug** can be used on laptops and desktop PCs across Windows, Linux, and macOS:

### 1. Windows Laptops (Windows 11 / 10)
* **Windows Subsystem for Android (WSA)**:
  * Enable Windows Subsystem for Android.
  * Install the release APK via `adb install abug-v1.0.0.apk`.
  * Runs with full Intel/AMD CPU multi-threading and GPU pass-through.
* **Android Emulators (BlueStacks, LDPlayer, Nox)**:
  * Open emulator settings and enable ARM64 translation and multi-core allocation.
  * Drag and drop `abug-v1.0.0.apk` to install and run.

### 2. Linux Laptops (Ubuntu, Fedora, Arch, Debian)
* **Waydroid (Native Full-Speed Hardware Pass-through)**:
  * Install Waydroid: `sudo apt install waydroid` (or your distro equivalent).
  * Initialize Waydroid and install the APK: `waydroid app install abug-v1.0.0.apk`.
  * Waydroid runs the Linux kernel container natively with direct Vulkan/OpenGL GPU acceleration at native laptop speeds.

### 3. macOS Laptops (Apple Silicon M1 / M2 / M3 / M4)
* **Android Studio / AVD Emulator**:
  * Download Android Studio for Apple Silicon.
  * Create an ARM64-v8a virtual device (Pixel 8 / Tablet) with Google Play.
  * Start the emulator and run `adb install abug-v1.0.0.apk`.
  * Runs with native ARM hypervisor acceleration at near-native speed.

### 4. Native Desktop C++ Engine Architecture
The core inference pipeline is written in portable, self-contained C++ (`RealEsrganEngine`, `RifeEngine`, `TileEngine`, `ColorConverter`) powered by Tencent NCNN:
* Located in `app/src/main/cpp/`.
* Can be compiled on any laptop OS (Linux, macOS, Windows) by linking against desktop `libncnn` and Vulkan SDK.

---

## 📱 Mobile Hardware Requirements

* **Architecture**: ARM64-v8a
* **Minimum Android Version**: Android 8.0 (API 26)
* **Recommended RAM**: 4 GB+ (8 GB+ recommended for Ultra Mode 8-Frame concurrency)
* **Compute Backend**: Vulkan GPU / ARM64 NEON multi-threaded CPU fallback

---

## 🛠️ Building From Source

```bash
# Clone the repository
git clone https://github.com/duplexspy/Abug.git
cd Abug

# Build Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📢 Community & Updates

Join the official Telegram channel for updates, model packs, presets, and community discussions:
* **Telegram Channel**: [https://t.me/effectcraftport](https://t.me/effectcraftport)
* **Author / Creator**: [duplexspy](https://github.com/duplexspy)

---

## 📄 License

Distributed under the Apache 2.0 License.  
Real-ESRGAN and RIFE neural architectures are properties of their respective authors.
