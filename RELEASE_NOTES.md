# abug v1.0.0 Release Notes

**abug - Offline AI Video Upscaler & Twixtor**  
**Creator**: [duplexspy](https://t.me/effectcraftport)  
**Telegram**: [https://t.me/effectcraftport](https://t.me/effectcraftport)

---

### 🎉 Initial Release Highlights

1. **Rebranding to abug (🐞)**:
   - New stylized acrylic flying ladybug logo and adaptive launcher icon.
   - Acrylic paint visual identity with vibrant crimson and iridescent cyan highlights.
   - Creator tribute and official Telegram channel link: [https://t.me/effectcraftport](https://t.me/effectcraftport).

2. **Ultra Mode Multi-Core Parallel Engine**:
   - Device Capability Auto-Scanner: automatically detects CPU cores and RAM headroom.
   - True simultaneous multi-frame generation (e.g. 8 frames in parallel on 8-core CPUs) without fake sequential delays.
   - Live Multi-Core telemetry grid in Render Queue.

3. **Pink & Green Distortion Fix**:
   - Direct hardware YUV plane stride and format mapping.
   - Native multi-format NV12/NV21/I420 box-filter chroma downsampling.
   - Deterministic `MediaMuxer` index writing.

4. **Interactive Video Split View & Zoom**:
   - Real-time Before vs After video split slider with playback scrubber.
   - 1.0x to 8.0x pinch-to-zoom and pan everywhere in lockstep.
   - Custom Before / After video clip pickers.

---

### 📦 Downloads & Releases

* 📱 **Mobile (Android APK)**: [**abug-v1.0.0.apk**](https://github.com/duplexspy/Abug/releases/download/v1.0.0/abug-v1.0.0.apk) (74 MB)  
  *Target Architecture*: Android ARM64-v8a (Android 8.0+)
* 💻 **Laptop / Desktop (Windows, macOS, Linux ZIP)**: [**Abug-Studio.zip**](https://github.com/duplexspy/Abug/releases/download/v1.0.0/Abug-Studio.zip) (62 MB)  
  *Pre-packaged standalone studio with local Web UI, native C++ CLI, and bundled offline AI models.*

