# Abug Studio — Immersive 3D Interface Upgrade

## New user-visible behaviors

- Every configuration dropdown now opens an animated, responsive card-selection popup: neural upscaling model, processing tile size, RIFE model and frame position.
- Popups preserve the existing form values and actually update the native engine request. The original selectors remain in the page for compatibility.
- Popups support Escape, clicking outside, keyboard arrows, Home/End, Tab focus cycling, selection highlighting, focus restoration and accessible modal semantics.
- Every clickable button has hover depth, press animation, and a subtle pointer ripple; selections and preset cards have light/3D motion.
- The existing interactive 3D neural-core canvas remains draggable and motion-enabled/disabled. This is an interface visualization, **not** a rendered 3D video model.
- Tuned dark navy/violet/cyan panels, popup depth, highlight sheens and responsive phone-sized layouts.
- Kept the *actual bundled* Real-ESRGAN 2×/3×/4× and RIFE v4/v4.6 models, image batches, PNG/ZIP exports, status messages and Chrome auto-opening via `abug`.
- Respects `prefers-reduced-motion` and the built-in Motion On/Off toggle. No cloud or WebGL dependency was added.

## One-time Mac upgrade

Download `Abug-Studio-M1-Immersive-3D.zip` into Downloads, then run:

```bash
cd ~/Downloads && unzip -o Abug-Studio-M1-Immersive-3D.zip && cd Abug-Studio-M1 && bash INSTALL_ABUG_COMMAND.command && source ~/.zshrc && abug
```

Later you can type just `abug`; it starts the local server and opens Chrome automatically. If an old server is still open, close its Terminal session or use the newly opened Chrome tab with the different port.

## Verification and limits

Tested with automated Chromium interaction on 1440px desktop and 390px mobile viewports: all four animated selectors, their underlying values, keyboard/escape handling, source upload, batch processing with *simulated native responses*, ZIP downloads, three-frame RIFE option, help modal and animation toggle. Python syntax and HTTP routes were also checked.

**Not verified:** Actual compiled macOS M1 NCNN model inference or a full rendering benchmark, which require running the build and model workloads on your Mac. UI-only 3D animation does not create 3D video editing. Heavy outputs can require significant memory on an 8 GB laptop.

Original Abug project credit: duplexspy.
