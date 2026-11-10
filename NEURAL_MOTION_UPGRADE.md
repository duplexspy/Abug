# Abug Studio — Neural Motion Edition

## Changes in this update

- New interactive, geometrically projected 3D neural-core stage with drag-to-rotate and reset, rendered locally in canvas without third-party scripts. These animations are **visual UI graphics**, not 3D media editing or AI models.
- Polished lighting, gradients, buttons, tabs, layout transitions, input previews and status feedback.
- Motion On/Off control plus automatic reduced-motion support. Designed for high-DPI Mac and mobile-width browser windows.
- One-click model presets using the **actual bundled** Real-ESRGAN models (AnimeVideo 4x, Photo x4plus, Anime 6B, memory-friendly 2x).
- Auto tile selection, with 64/128/256px values chosen by source dimensions (explicit manual options retained).
- Live display of source dimensions, model-scaled expected output resolution and queued source count.
- Sequential image batch of up to three images, 24 MB total input, with offline ZIP download of processed PNG files.
- Three-frame RIFE output (25/50/75% between two source frames) exported in one ZIP; existing single RIFE timepoint preserved.
- Fullscreen before-and-after comparison. The processing API is unchanged; results still come from native NCNN inference, not from frontend mock filters.
- Existing `abug` shell command opens Google Chrome, and coexists with an older instance on another local port.

## Installation on Apple Silicon macOS

1. Put `Abug-Studio-Neural-Motion.zip` in `~/Downloads`.
2. Stop any old running Abug server in Terminal with Ctrl+C (optional; a new edition will choose another local port automatically).
3. Paste:

```bash
cd ~/Downloads && unzip -o Abug-Studio-Neural-Motion.zip && cd Abug-Studio-M1 && bash INSTALL_ABUG_COMMAND.command && source ~/.zshrc && abug
```

4. Later type `abug` in Terminal. The launcher builds the Mac native engine if needed, starts the Python server and opens Google Chrome. Keep Terminal open.

## Tested / not tested

- Chromium automated tests: wide and narrow viewport, functional controls, model-preset changes, source metadata, simulated image-processing API, batch ZIP validity, three-frame sequence ZIP validity, modal and compare controls.
- Python server syntax and HTTP diagnostics/error responses.
- **Not verified on a real Apple Silicon machine:** native C++ build, Real-ESRGAN quality/performance, RIFE inference quality, and full-size media RAM performance. Existing engine code is preserved; a successful UI simulation is not a native AI benchmark.

## Practical limits

- 3 images maximum in a batch; 24 MB of input combined, 18 MB per image and 110 MB of output PNG data total.
- Both RIFE source frames must have the same dimensions.
- Extremely high output resolutions are demanding on an 8 GB machine. Use memory-saver preset or lower source resolution as needed.
- 3D stage is an interface visualization, not a feature to manipulate 3D models or export 3D animation.

## Original project

Credit to duplexspy for the original Abug Studio source.
