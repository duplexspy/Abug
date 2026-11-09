# 📖 How to Run abug (🐞)

**Offline Open-Source AI Video Upscaler & Twixtor Studio**  
*Creator: [duplexspy](https://github.com/duplexspy) • Telegram: [https://t.me/effectcraftport](https://t.me/effectcraftport)*

---

## 📑 Table of Contents
1. [📱 Running on Android (Mobile & Tablets)](#-1-running-on-android-mobile--tablets)
2. [💻 Running on Laptops & Desktops](#-2-running-on-laptops--desktops-windows-macos-linux)
   - [Method A: Standalone Desktop Studio (One-Click)](#method-a-standalone-desktop-studio-recommended)
   - [Method B: Command-Line CLI](#method-b-command-line-cli)
   - [Method C: Android on Laptop (WSA / Waydroid / Emulators)](#method-c-android-on-laptop)
3. [❓ Troubleshooting & Tips](#-troubleshooting--tips)

---

## 📱 1. Running on Android (Mobile & Tablets)

### Step 1: Download the APK
Download the latest pre-compiled APK from the official release page:
* 📥 **[Download abug-v1.0.0.apk](https://github.com/duplexspy/Abug/releases/download/v1.0.0/abug-v1.0.0.apk)** (~74 MB)

### Step 2: Install on Your Phone
1. Tap the downloaded `.apk` file on your device.
2. If prompted, tap **Settings** and enable **"Allow from this source"** (standard security check for sideloaded apps).
3. Tap **Install**.

> [!TIP]
> **Launcher Icon Cache Tip**: If you had a previous version installed, uninstall it first before installing the new build so your phone's home screen launcher refreshes the app icon to the ladybug on the blue background.

### Step 3: Grant Permissions
When opening **abug** for the first time:
* Grant **Photos and Videos** (or Storage) permission so the studio can import your clips and save enhanced exports to your gallery.

### Step 4: Using the App
1. **Import**: Tap **Import Video** or **Import Photo** to pick your media.
2. **Choose Mode**:
   - **Super-Resolution**: Choose 2x, 3x, or 4x with Real-ESRGAN (AnimeVideo v3 for anime/cartoons, x4plus for real-life footage).
   - **Twixtor (Frame Interpolation)**: Choose 2x, 4x, or 8x FPS boost or slow-motion powered by RIFE v4.6 FlowNet.
3. **Multi-Core Ultra Mode**:
   - The app automatically detects your phone's CPU core count and memory headroom to allocate concurrent frame workers (up to 8 parallel frames).
4. **Compare**: Drag the interactive Before/After split slider and use pinch-to-zoom (up to 8x) to inspect details in real time.
5. **Export**: Tap **Export** to render and save directly to your phone's Movies/DCIM gallery.

---

## 💻 2. Running on Laptops & Desktops (Windows, macOS, Linux)

### Method A: Standalone Desktop Studio (Recommended)

The standalone studio provides a local web-based graphical interface that runs on **Windows, macOS, and Linux laptops** with zero external dependencies.

#### Option 1: From Release Package (Easiest)
1. Download **[abug-standalone-v1.0.0.tar.gz](https://github.com/duplexspy/Abug/releases/download/v1.0.0/abug-standalone-v1.0.0.tar.gz)** from GitHub Releases.
2. Extract the archive:
   ```bash
   tar -xzvf abug-standalone-v1.0.0.tar.gz
   cd standalone
   ```
3. Run the studio:
   - **On Windows**: Double-click `run_desktop.bat` (or run `python abug_standalone.py` in Command Prompt).
   - **On macOS / Linux**: Double-click or run `./run_desktop.sh`.
4. Your default web browser will automatically open to `http://localhost:54321`.

#### Option 2: From Cloned Source
```bash
git clone https://github.com/duplexspy/Abug.git
cd Abug

# Launch Desktop Studio
./standalone/run_desktop.sh      # On macOS/Linux
# OR
standalone\run_desktop.bat      # On Windows
```

---

### Method B: Command-Line CLI

You can run neural upscaling and Twixtor interpolation directly from your terminal or command prompt:

#### 1. Python CLI Interface
```bash
# Super-Resolution Upscaling (4x):
python3 standalone/abug_standalone.py upscale -i input.png -o output.png -s 4 --model realesr-anime-x4

# Twixtor Optical Flow Interpolation:
python3 standalone/abug_standalone.py twixtor -f0 frame0.png -f1 frame1.png -o interp.png -t 0.5 --model rife-v4.6
```

#### 2. Native C++ Executable (`abug-cli`)
```bash
# Direct sub-second native C++ inference:
./standalone/abug-cli upscale input.jpg output.png 4 app/src/main/assets/models/realesr-animevideov3-x4.bin 128
./standalone/abug-cli twixtor frame0.png frame1.png interp.png app/src/main/assets/models/rife-v4.6/flownet.bin 0.5
```

---

### Method C: Android on Laptop

If you want the exact Android mobile app interface running on your laptop:

#### 1. Windows 11 / 10
* **Windows Subsystem for Android (WSA)**:
  1. Open WSA settings and enable Developer Mode.
  2. Run `adb connect 127.0.0.1:58526`.
  3. Run `adb install abug-v1.0.0.apk`.
  4. Launch **abug** directly from your Windows Start Menu!
* **Android Emulators (BlueStacks / LDPlayer / Nox)**:
  1. Open BlueStacks or LDPlayer.
  2. Drag and drop `abug-v1.0.0.apk` onto the emulator window to install.

#### 2. Linux Laptops (Ubuntu / Fedora / Arch / Debian)
* **Waydroid (Full Native Hardware Performance)**:
  1. Install Waydroid (`sudo apt install waydroid` or distro equivalent).
  2. Start the container: `waydroid session start`.
  3. Install: `waydroid app install abug-v1.0.0.apk`.
  4. Waydroid runs with direct Vulkan/OpenGL GPU hardware pass-through at native speed.

#### 3. macOS Laptops (Apple Silicon M1 / M2 / M3 / M4)
* **Android Studio AVD Emulator**:
  1. Open Android Studio → Device Manager.
  2. Create a Virtual Device (Pixel 8) with ARM64-v8a system image.
  3. Start the emulator and run `adb install abug-v1.0.0.apk`.
  4. Runs via macOS ARM hypervisor with near-zero overhead.

---

## ❓ Troubleshooting & Tips

* **Out of Memory on Large Images/Videos**:
  - In the settings, reduce **Tile Size** from `256` to `128` or `64`. Tiling splits frames into smaller blocks so high-resolution media processes without exceeding RAM.
* **Which Model to Choose?**:
  - **Real-ESRGAN AnimeVideo v3**: Best for animation, anime, 2D art, and cartoon clips. Very sharp line preservation.
  - **Real-ESRGAN x4plus**: Best for real-life camera videos, nature, portraits, and photography.
  - **RIFE v4.6**: The highest quality optical flow model for smooth motion interpolation and slow-mo.
* **Support & Updates**:
  - Official Telegram Community: [https://t.me/effectcraftport](https://t.me/effectcraftport)
