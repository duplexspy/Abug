# Abug Studio — M1 Build Fix & Chrome Launcher

## What's fixed in this patch

- Corrected `ncnn/net.h` and `ncnn/layer.h` visibility in the macOS CMake build. The compiler now receives the parent include directory of Homebrew's NCNN headers.
- Included `<math.h>` before the bundled `stb_image` and `stb_image_write` implementations to resolve `powf`, `pow`, `ldexp`, and `frexp` build errors.
- Added a Chrome-first browser opener and port reuse/fallback, so running the launcher opens the studio without manually entering a URL.
- Added a one-time `INSTALL_ABUG_COMMAND.command` that installs the `abug` command in `~/bin` and adds it to your zsh PATH without sudo.
- Preserved the existing responsive interface, animations, neural-model dropdowns, local API, input checking, and export/download features.

## One-time setup on Mac

The installation of NCNN and libomp is already complete on the user's Apple Silicon Mac. Open Terminal in the folder extracted from this ZIP and run:

```bash
bash INSTALL_ABUG_COMMAND.command
source ~/.zshrc
abug
```

After this, **just type `abug` in any Terminal**. It builds the native AI engine if needed, starts the local studio, and opens **Google Chrome** automatically. Keep that Terminal running while you edit; press Ctrl+C to stop. If Chrome is not installed, the macOS default browser opens instead.

Alternatively, run `bash RUN_MAC_STUDIO.command` directly or double-click the `.command` file in Finder.

If an older app already occupies port 54322, the launcher searches the next available port and opens the correct one. Running `abug` again while the updated studio is already running opens its existing page instead of duplicating the service.

## Mac verification still required

The source is checked for C++ syntax with the bundled NCNN headers and the HTTP backend is tested in Linux. **The Apple Silicon executable and actual Real-ESRGAN/RIFE model inference cannot be validated without running the native build on a Mac.** If compilation shows any *new* error, please share the compiler lines; we do not claim guaranteed working neural inference until verified on the target Mac.

Full MP4 video processing remains outside this standalone image/frame-pair studio's scope; it does not provide a ready video export pipeline yet.

---

# abug Studio — Enhanced macOS Workspace

This is a source-code patch of the uploaded **Abug-main** project. The project still belongs to its original authors. This version modernizes the *desktop browser interface*, repairs API error reporting, and adds a separate macOS-native NCNN build path. The bundled Android APK sources are preserved.

## Start on an Apple Silicon Mac

You already installed NCNN and libomp with Homebrew. If not, first run:

```bash
brew install ncnn libomp cmake
```

In Terminal, from the updated `Abug-main` folder:

```bash
bash standalone/build_macos.sh
python3 standalone/abug_standalone.py studio --port 54322
```

Open `http://localhost:54322`. The new port avoids a collision with the previously running original server on 54321. Alternatively, double-click `RUN_MAC_STUDIO.command` in Finder (macOS may ask you to allow execution).

The original **`standalone/abug-cli`** is an Android ARM64 ELF file. The Mac build outputs **`standalone/abug-cli-macos`**, preserving the original Android binary. The Python server chooses the correct binary by operating system. If you still see "Engine unavailable", open the **Engine details** button and inspect the error message. The `.command` launcher will stop if native compilation fails rather than pretending rendering works.

If NCNN installation was reported successful but compilation cannot find its CMake package, inspect:

```bash
brew list ncnn | grep -E 'ncnnConfig|ncnn.*dylib|libncnn|ncnn.h'
```

Try a fresh install only if these files are missing. The Homebrew formula uses a shared NCNN library with its CMake package. This project deliberately does **not** link to the bundled Android `libncnn.a`.

## Implemented changes

- Replaced the old embedded HTML with `standalone/studio.html`, retaining Real-ESRGAN upscaling and RIFE frame-pair interpolation.
- Added responsive navigation, fluid hover/focus transitions, reduced-motion support, drag-and-drop uploads, model/tile/timestep selectors, live engine diagnostics, keyboard shortcuts, engine-details modal, visible busy status, PNG download and pointer/touch/keyboard before-after comparison.
- Converted browser-selected PNG/JPG/WebP sources into PNG before invoking the native C++ CLI, avoiding WebP incompatibility in stb_image.
- Added `/api/health` and improved `/api/models`; the interface disables rendering if no compatible native executable exists.
- Added per-request temporary directories so multiple HTTP requests cannot overwrite a shared `/tmp/abug_out.png`.
- Added upload bounds, model validation, timestep/tile validation, malformed JSON handling, missing binary handling, 15-minute timeout, and a single-job execution slot to help avoid simultaneous memory-intensive renders.
- Replaced frontend's ambiguous `Failed to fetch` behavior with informative JSON errors when native launch or inference fails.
- Added a portable CMake setup for macOS using **Homebrew NCNN**; Android's prebuilt ELF binary is not modified.

## Limitations / still to verify on your Mac

- This zip was tested with Python static compilation, HTTP API checks and JavaScript syntax checks in a Linux environment. **I could not compile or run the Apple Silicon binary in this environment.** Run the included macOS build and verify the actual model inference on your M1.
- The existing native C++ Real-ESRGAN/RIFE model loaders may need further changes to work against NCNN 20261020. The new build path is provided, but model compatibility, output correctness and speed are not guaranteed without a test on macOS.
- The standalone app currently processes images (upscaler) or a **pair of image frames** (RIFE), not full video files. End-to-end video-file processing would require a separate FFmpeg-based media pipeline.
- Processing is CPU-first with the existing C++ implementation. Homebrew's NCNN installation including MoltenVK does not automatically enable GPU inference for this source; GPU support would need additional engineering.
- The redesigned interface has not been visually verified in a full local desktop browser session in the authoring environment, though the HTML and JavaScript were syntax-checked.

## Troubleshooting

1. **Port already in use:** Stop your previous server with Ctrl+C, or choose another port using `studio --port 54323`.
2. **No native executable:** Run `bash standalone/build_macos.sh`; check the build output for CMake/compiler details.
3. **ncnn CMake configuration missing:** Run `brew list ncnn` and check `$(brew --prefix ncnn)/lib/cmake/ncnn/ncnnConfig.cmake`.
4. **Model fails after successful native build:** Read the error shown in the studio and stderr in Terminal; a build can succeed while a neural model is incompatible.
5. **Heavy frames:** Choose the 64 px tile size and start with small source images on a MacBook Air with limited RAM.
