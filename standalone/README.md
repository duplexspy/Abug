# abug (🐞) Standalone Desktop Studio & AI CLI

> **100% Offline Open-Source AI Video Upscaler & Twixtor Frame Interpolation for Laptops & Desktops**  
> **Creator:** [duplexspy](https://t.me/effectcraftport)  
> **Telegram Channel:** [https://t.me/effectcraftport](https://t.me/effectcraftport)

---

## 💻 What is abug Standalone?

**abug Standalone** brings the power of **Real-ESRGAN** neural upscaling and **RIFE** optical flow Twixtor interpolation directly to your laptop or PC (Windows, macOS, Linux). It runs **100% locally on your hardware** with zero telemetry and zero internet connection required.

---

## 🚀 Quick Start (One-Click Launch)

### On Windows Laptops
Double-click `run_desktop.bat` (or open Command Prompt):
```cmd
python standalone\abug_standalone.py
```

### On macOS / Linux Laptops
Run the launcher script:
```bash
./standalone/run_desktop.sh
```
This automatically opens the **abug Desktop Studio** at `http://localhost:54321` in your default browser.

---

## ✨ Features

1. **Local Desktop Studio UI**:
   - Modern acrylic dark UI matching the mobile app with vibrant `#29ABE5` blue styling.
   - Drag-and-drop photo and frame uploads.
   - Interactive Before vs After split comparison slider.
   - Direct download of enhanced results.
2. **Real-ESRGAN Super-Resolution**:
   - 2x, 3x, 4x upscaling.
   - Supports AnimeVideo v3, Real-ESRGAN x4plus (Photorealistic), and Anime 6B models.
3. **RIFE Optical Flow Twixtor**:
   - High-precision FlowNet optical flow frame interpolation.
   - Smooth 2x, 4x, and 8x slow-motion or high-framerate boosts.
4. **Native High-Performance C++ CLI Engine**:
   - Pre-compiled `abug-cli` executable for sub-second inference speed.
   - Direct PNG / JPG / WebP image support without external dependencies.

---

## 🛠️ CLI Usage

You can also use `abug` directly from your terminal or command prompt:

### Upscale an Image (4x)
```bash
python3 standalone/abug_standalone.py upscale -i input.png -o output.png -s 4 --model realesr-anime-x4
```

### Generate In-Between Twixtor Frame
```bash
python3 standalone/abug_standalone.py twixtor -f0 frame0.png -f1 frame1.png -o interp.png -t 0.5 --model rife-v4.6
```

### Direct Native C++ CLI
```bash
./standalone/abug-cli upscale input.jpg output.png 4 app/src/main/assets/models/realesr-animevideov3-x4.bin
./standalone/abug-cli twixtor frame0.png frame1.png interp.png app/src/main/assets/models/rife-v4.6/flownet.bin 0.5
```

---

## 📦 Building Native Engine from Source

If you want to recompile the native C++ binary on your laptop:
```bash
./standalone/build.sh
```

---

## 📢 Community & Support
* **Official Telegram Channel**: [https://t.me/effectcraftport](https://t.me/effectcraftport)
* **Creator / Developer**: [duplexspy](https://github.com/duplexspy)
