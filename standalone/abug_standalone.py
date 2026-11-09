#!/usr/bin/env python3
"""
abug (🐞) Standalone Desktop Studio & AI CLI
Offline Open-Source AI Video Upscaler & Twixtor Frame Interpolation
Created by duplexspy (https://t.me/effectcraftport)
"""

import sys
import os
import io
import json
import base64
import argparse
import subprocess
import webbrowser
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
CLI_BIN = os.path.join(SCRIPT_DIR, "abug-cli")
MODELS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "models")

MODELS = {
    "realesr-anime-x4": {
        "name": "Real-ESRGAN AnimeVideo v3 (4x)",
        "type": "upscale",
        "scale": 4,
        "path": os.path.join(MODELS_DIR, "realesr-animevideov3-x4.bin")
    },
    "realesr-anime-x3": {
        "name": "Real-ESRGAN AnimeVideo v3 (3x)",
        "type": "upscale",
        "scale": 3,
        "path": os.path.join(MODELS_DIR, "realesr-animevideov3-x3.bin")
    },
    "realesr-anime-x2": {
        "name": "Real-ESRGAN AnimeVideo v3 (2x)",
        "type": "upscale",
        "scale": 2,
        "path": os.path.join(MODELS_DIR, "realesr-animevideov3-x2.bin")
    },
    "realesrgan-x4plus": {
        "name": "Real-ESRGAN x4plus (Photo)",
        "type": "upscale",
        "scale": 4,
        "path": os.path.join(MODELS_DIR, "realesrgan-x4plus.bin")
    },
    "realesrgan-x4plus-anime": {
        "name": "Real-ESRGAN x4plus Anime (6B)",
        "type": "upscale",
        "scale": 4,
        "path": os.path.join(MODELS_DIR, "realesrgan-x4plus-anime.bin")
    },
    "rife-v4.6": {
        "name": "RIFE v4.6 Optical FlowNet (Twixtor)",
        "type": "twixtor",
        "path": os.path.join(MODELS_DIR, "rife-v4.6", "flownet.bin")
    },
    "rife-v4": {
        "name": "RIFE v4 FlowNet (Twixtor)",
        "type": "twixtor",
        "path": os.path.join(MODELS_DIR, "rife-v4", "flownet.bin")
    }
}

HTML_PAGE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>abug 🐞 | Standalone Desktop Studio</title>
<style>
:root {
  --bg-primary: #0F141C;
  --bg-surface: #151C28;
  --bg-card: #1D2636;
  --accent-blue: #29ABE5;
  --accent-cyan: #00D2C4;
  --text-primary: #F0F4F8;
  --text-muted: #8E9EB0;
  --border: rgba(255, 255, 255, 0.08);
}
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  background: var(--bg-primary);
  color: var(--text-primary);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
header {
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border);
  padding: 16px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}
.brand-icon {
  width: 44px;
  height: 44px;
  background: var(--accent-blue);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: 0 4px 16px rgba(41, 171, 229, 0.35);
}
.brand h1 { font-size: 20px; font-weight: 700; letter-spacing: -0.5px; }
.brand h1 span { color: var(--accent-blue); }
.brand p { font-size: 12px; color: var(--text-muted); }
.badge {
  background: rgba(41, 171, 229, 0.15);
  color: var(--accent-blue);
  border: 1px solid rgba(41, 171, 229, 0.3);
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}
.telegram-btn {
  background: #229ED9;
  color: white;
  text-decoration: none;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  transition: opacity 0.2s;
}
.telegram-btn:hover { opacity: 0.9; }

main {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.tabs {
  display: flex;
  gap: 12px;
  background: var(--bg-surface);
  padding: 6px;
  border-radius: 12px;
  width: fit-content;
  border: 1px solid var(--border);
}
.tab {
  padding: 10px 22px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-muted);
  transition: all 0.2s;
}
.tab.active {
  background: var(--accent-blue);
  color: #fff;
  box-shadow: 0 2px 8px rgba(41, 171, 229, 0.3);
}
.card {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.dropzone {
  border: 2px dashed rgba(41, 171, 229, 0.4);
  background: rgba(29, 38, 54, 0.5);
  border-radius: 12px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}
.dropzone:hover {
  border-color: var(--accent-blue);
  background: rgba(41, 171, 229, 0.05);
}
.dropzone p { font-size: 14px; color: var(--text-muted); margin-top: 8px; }
.controls {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.control-group label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}
select, input[type="number"] {
  width: 100%;
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text-primary);
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
}
select:focus, input:focus { border-color: var(--accent-blue); }

.action-btn {
  background: linear-gradient(135deg, #29ABE5, #0088FF);
  color: white;
  border: none;
  padding: 14px 28px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(41, 171, 229, 0.4);
  transition: all 0.2s;
}
.action-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(41, 171, 229, 0.5); }
.action-btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }

.split-container {
  position: relative;
  width: 100%;
  height: 480px;
  background: #0B0E14;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
  display: none;
}
.split-img {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  object-fit: contain;
}
.split-after {
  clip-path: polygon(50% 0, 100% 0, 100% 100%, 50% 100%);
}
.split-slider {
  position: absolute;
  top: 0; bottom: 0;
  width: 4px;
  background: var(--accent-blue);
  left: 50%;
  cursor: ew-resize;
  box-shadow: 0 0 10px rgba(41, 171, 229, 0.8);
}
.split-slider::after {
  content: '◂ ▸';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: var(--accent-blue);
  color: white;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.5);
}
.labels {
  position: absolute;
  bottom: 16px;
  width: 100%;
  display: flex;
  justify-content: space-between;
  padding: 0 20px;
  pointer-events: none;
}
.label-tag {
  background: rgba(0, 0, 0, 0.7);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}
.status-bar {
  display: none;
  padding: 12px 16px;
  border-radius: 8px;
  background: rgba(41, 171, 229, 0.1);
  border: 1px solid rgba(41, 171, 229, 0.2);
  color: var(--accent-blue);
  font-size: 13px;
  font-weight: 600;
}
footer {
  text-align: center;
  padding: 20px;
  color: var(--text-muted);
  font-size: 12px;
  border-top: 1px solid var(--border);
}
</style>
</head>
<body>
<header>
  <div class="brand">
    <div class="brand-icon">🐞</div>
    <div>
      <h1>abug <span>Desktop Studio</span></h1>
      <p>Offline AI Video Upscaler & Twixtor by duplexspy</p>
    </div>
  </div>
  <div style="display: flex; align-items: center; gap: 12px;">
    <span class="badge">100% Offline Engine</span>
    <a href="https://t.me/effectcraftport" target="_blank" class="telegram-btn">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.75-.55 2.92-1.27 4.86-2.11 5.83-2.52 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .38z"/></svg>
      Telegram Channel
    </a>
  </div>
</header>

<main>
  <div class="tabs">
    <div class="tab active" onclick="switchTab('upscale')">⚡ Super-Resolution (Real-ESRGAN)</div>
    <div class="tab" onclick="switchTab('twixtor')">🎬 Twixtor Frame Interpolation (RIFE)</div>
  </div>

  <div class="card" id="upscale-panel">
    <div class="dropzone" id="drop-upscale" onclick="document.getElementById('file-upscale').click()">
      <div style="font-size: 36px; margin-bottom: 8px;">🖼️</div>
      <strong>Click or Drag Image Here to Upscale</strong>
      <p>Supports PNG, JPG, WebP. Processed locally on your laptop CPU/GPU.</p>
      <input type="file" id="file-upscale" accept="image/*" style="display:none" onchange="handleFileSelect(event, 'upscale')">
    </div>

    <div class="controls">
      <div class="control-group">
        <label>Neural Model</label>
        <select id="model-upscale">
          <option value="realesr-anime-x4">Real-ESRGAN AnimeVideo v3 (4x)</option>
          <option value="realesr-anime-x3">Real-ESRGAN AnimeVideo v3 (3x)</option>
          <option value="realesr-anime-x2">Real-ESRGAN AnimeVideo v3 (2x)</option>
          <option value="realesrgan-x4plus">Real-ESRGAN x4plus (Photorealistic 4x)</option>
          <option value="realesrgan-x4plus-anime">Real-ESRGAN Anime 6B (4x)</option>
        </select>
      </div>

      <div class="control-group">
        <label>Tile Size</label>
        <select id="tile-size">
          <option value="128" selected>128 px (Balanced RAM)</option>
          <option value="64">64 px (Low RAM / Fast)</option>
          <option value="256">256 px (High-End Laptop)</option>
        </select>
      </div>
    </div>

    <button class="action-btn" id="btn-upscale" onclick="processUpscale()" disabled>Start Neural Upscaling</button>
  </div>

  <div class="card" id="twixtor-panel" style="display:none">
    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
      <div class="dropzone" onclick="document.getElementById('file-f0').click()">
        <div style="font-size: 28px; margin-bottom: 4px;">1️⃣</div>
        <strong>Start Frame (Frame A)</strong>
        <p id="f0-name">Click to select</p>
        <input type="file" id="file-f0" accept="image/*" style="display:none" onchange="handleTwixtorFile(event, 0)">
      </div>
      <div class="dropzone" onclick="document.getElementById('file-f1').click()">
        <div style="font-size: 28px; margin-bottom: 4px;">2️⃣</div>
        <strong>End Frame (Frame B)</strong>
        <p id="f1-name">Click to select</p>
        <input type="file" id="file-f1" accept="image/*" style="display:none" onchange="handleTwixtorFile(event, 1)">
      </div>
    </div>

    <div class="controls">
      <div class="control-group">
        <label>RIFE Optical Flow Model</label>
        <select id="model-twixtor">
          <option value="rife-v4.6">RIFE v4.6 FlowNet (Latest High-Precision)</option>
          <option value="rife-v4">RIFE v4 FlowNet (Standard)</option>
        </select>
      </div>

      <div class="control-group">
        <label>Timestep (Position)</label>
        <select id="timestep">
          <option value="0.5" selected>0.50 (Exact Midpoint - 2x FPS)</option>
          <option value="0.25">0.25 (Quarter 1 - 4x Slow-Mo)</option>
          <option value="0.75">0.75 (Quarter 3 - 4x Slow-Mo)</option>
        </select>
      </div>
    </div>

    <button class="action-btn" id="btn-twixtor" onclick="processTwixtor()" disabled>Generate In-Between Frame (RIFE)</button>
  </div>

  <div class="status-bar" id="status-bar"></div>

  <div class="split-container" id="split-view">
    <img id="img-before" class="split-img" alt="Before">
    <img id="img-after" class="split-img split-after" alt="After">
    <div class="split-slider" id="slider"></div>
    <div class="labels">
      <span class="label-tag">Original</span>
      <span class="label-tag">Enhanced (abug AI)</span>
    </div>
  </div>

  <div id="download-area" style="display:none; text-align: right;">
    <a id="download-link" download="abug_enhanced.png" class="action-btn" style="text-decoration: none; display: inline-block;">
      ⬇️ Download Enhanced Image
    </a>
  </div>
</main>

<footer>
  abug v1.0.0 Standalone Studio • Created by <a href="https://t.me/effectcraftport" style="color:var(--accent-blue); text-decoration:none;">duplexspy</a> • 100% Offline AI Video Upscaler & Twixtor
</footer>

<script>
let currentMode = 'upscale';
let currentFile = null;
let file0 = null, file1 = null;

function switchTab(mode) {
  currentMode = mode;
  document.querySelectorAll('.tab').forEach((t, i) => {
    t.classList.toggle('active', (i === 0 && mode === 'upscale') || (i === 1 && mode === 'twixtor'));
  });
  document.getElementById('upscale-panel').style.display = mode === 'upscale' ? 'flex' : 'none';
  document.getElementById('twixtor-panel').style.display = mode === 'twixtor' ? 'flex' : 'none';
  document.getElementById('split-view').style.display = 'none';
  document.getElementById('download-area').style.display = 'none';
  document.getElementById('status-bar').style.display = 'none';
}

function handleFileSelect(event, type) {
  const file = event.target.files[0];
  if (!file) return;
  currentFile = file;
  document.getElementById('drop-upscale').querySelector('strong').innerText = 'Selected: ' + file.name;
  document.getElementById('btn-upscale').disabled = false;
  
  const reader = new FileReader();
  reader.onload = (e) => {
    document.getElementById('img-before').src = e.target.result;
  };
  reader.readAsDataURL(file);
}

function handleTwixtorFile(event, index) {
  const file = event.target.files[0];
  if (!file) return;
  if (index === 0) {
    file0 = file;
    document.getElementById('f0-name').innerText = file.name;
  } else {
    file1 = file;
    document.getElementById('f1-name').innerText = file.name;
  }
  document.getElementById('btn-twixtor').disabled = !(file0 && file1);
}

function setStatus(text, loading = false) {
  const bar = document.getElementById('status-bar');
  bar.style.display = 'block';
  bar.innerText = (loading ? '⏳ ' : '✅ ') + text;
}

async function processUpscale() {
  if (!currentFile) return;
  const btn = document.getElementById('btn-upscale');
  btn.disabled = true;
  setStatus('Running Real-ESRGAN neural upscaler on laptop hardware...', true);

  const modelKey = document.getElementById('model-upscale').value;
  const tileSize = document.getElementById('tile-size').value;

  const reader = new FileReader();
  reader.onload = async (e) => {
    const b64 = e.target.result.split(',')[1];
    try {
      const resp = await fetch('/api/upscale', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          image_b64: b64,
          model: modelKey,
          tile_size: parseInt(tileSize)
        })
      });
      const data = await resp.json();
      if (data.status === 'success') {
        const afterSrc = 'data:image/png;base64,' + data.output_b64;
        document.getElementById('img-after').src = afterSrc;
        document.getElementById('split-view').style.display = 'block';
        document.getElementById('download-area').style.display = 'block';
        document.getElementById('download-link').href = afterSrc;
        setStatus('Upscale completed in ' + (data.time_ms / 1000).toFixed(2) + 's (' + data.width + 'x' + data.height + ')');
      } else {
        setStatus('Error: ' + data.error);
      }
    } catch (err) {
      setStatus('Network/Server error: ' + err.message);
    } finally {
      btn.disabled = false;
    }
  };
  reader.readAsDataURL(currentFile);
}

async function processTwixtor() {
  if (!file0 || !file1) return;
  const btn = document.getElementById('btn-twixtor');
  btn.disabled = true;
  setStatus('Running RIFE optical flow frame interpolation...', true);

  const modelKey = document.getElementById('model-twixtor').value;
  const timestep = document.getElementById('timestep').value;

  const r0 = new Promise(res => { const r = new FileReader(); r.onload = e => res(e.target.result.split(',')[1]); r.readAsDataURL(file0); });
  const r1 = new Promise(res => { const r = new FileReader(); r.onload = e => res(e.target.result.split(',')[1]); r.readAsDataURL(file1); });

  const [b64_0, b64_1] = await Promise.all([r0, r1]);

  try {
    const resp = await fetch('/api/twixtor', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        frame0_b64: b64_0,
        frame1_b64: b64_1,
        model: modelKey,
        timestep: parseFloat(timestep)
      })
    });
    const data = await resp.json();
    if (data.status === 'success') {
      const interpSrc = 'data:image/png;base64,' + data.output_b64;
      document.getElementById('img-before').src = 'data:image/png;base64,' + b64_0;
      document.getElementById('img-after').src = interpSrc;
      document.getElementById('split-view').style.display = 'block';
      document.getElementById('download-area').style.display = 'block';
      document.getElementById('download-link').href = interpSrc;
      setStatus('RIFE frame interpolation completed in ' + (data.time_ms / 1000).toFixed(2) + 's');
    } else {
      setStatus('Error: ' + data.error);
    }
  } catch (err) {
    setStatus('Network/Server error: ' + err.message);
  } finally {
    btn.disabled = false;
  }
}

// Split slider drag handling
const splitView = document.getElementById('split-view');
const slider = document.getElementById('slider');
const imgAfter = document.getElementById('img-after');
let isDragging = false;

slider.addEventListener('mousedown', () => isDragging = true);
window.addEventListener('mouseup', () => isDragging = false);
window.addEventListener('mousemove', (e) => {
  if (!isDragging) return;
  const rect = splitView.getBoundingClientRect();
  let x = e.clientX - rect.left;
  if (x < 0) x = 0;
  if (x > rect.width) x = rect.width;
  const pct = (x / rect.width) * 100;
  slider.style.left = pct + '%';
  imgAfter.style.clipPath = `polygon(${pct}% 0, 100% 0, 100% 100%, ${pct}% 100%)`;
});
</script>
</body>
</html>
"""

class DesktopStudioHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass # Quiet console logs

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path in ["/", "/index.html"]:
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(HTML_PAGE.encode("utf-8"))
        elif parsed.path == "/api/models":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(MODELS).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        parsed = urlparse(self.path)
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        req = json.loads(body.decode("utf-8"))

        if parsed.path == "/api/upscale":
            self.handle_upscale(req)
        elif parsed.path == "/api/twixtor":
            self.handle_twixtor(req)
        else:
            self.send_response(404)
            self.end_headers()

    def handle_upscale(self, req):
        image_b64 = req.get("image_b64")
        model_key = req.get("model", "realesr-anime-x4")
        tile_size = req.get("tile_size", 128)

        if not image_b64 or model_key not in MODELS:
            self.respond_json({"status": "error", "error": "Invalid request or unknown model"}, 400)
            return

        model_info = MODELS[model_key]
        in_tmp = "/tmp/abug_in.png"
        out_tmp = "/tmp/abug_out.png"

        with open(in_tmp, "wb") as f:
            f.write(base64.b64decode(image_b64))

        cmd = [
            CLI_BIN,
            "upscale",
            in_tmp,
            out_tmp,
            str(model_info["scale"]),
            model_info["path"],
            str(tile_size)
        ]

        import time
        t0 = time.time()
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        t1 = time.time()

        if res.returncode != 0 or not os.path.exists(out_tmp):
            err = res.stderr.decode("utf-8", errors="ignore")
            self.respond_json({"status": "error", "error": f"Inference failed: {err}"}, 500)
            return

        with open(out_tmp, "rb") as f:
            out_bytes = f.read()

        from PIL import Image
        im = Image.open(out_tmp)
        w, h = im.size

        self.respond_json({
            "status": "success",
            "output_b64": base64.b64encode(out_bytes).decode("utf-8"),
            "time_ms": int((t1 - t0) * 1000),
            "width": w,
            "height": h
        })

    def handle_twixtor(self, req):
        f0_b64 = req.get("frame0_b64")
        f1_b64 = req.get("frame1_b64")
        model_key = req.get("model", "rife-v4.6")
        timestep = req.get("timestep", 0.5)

        if not f0_b64 or not f1_b64 or model_key not in MODELS:
            self.respond_json({"status": "error", "error": "Invalid request or unknown model"}, 400)
            return

        model_info = MODELS[model_key]
        f0_tmp = "/tmp/abug_f0.png"
        f1_tmp = "/tmp/abug_f1.png"
        out_tmp = "/tmp/abug_interp.png"

        with open(f0_tmp, "wb") as f:
            f.write(base64.b64decode(f0_b64))
        with open(f1_tmp, "wb") as f:
            f.write(base64.b64decode(f1_b64))

        cmd = [
            CLI_BIN,
            "twixtor",
            f0_tmp,
            f1_tmp,
            out_tmp,
            model_info["path"],
            str(timestep)
        ]

        import time
        t0 = time.time()
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        t1 = time.time()

        if res.returncode != 0 or not os.path.exists(out_tmp):
            err = res.stderr.decode("utf-8", errors="ignore")
            self.respond_json({"status": "error", "error": f"Twixtor failed: {err}"}, 500)
            return

        with open(out_tmp, "rb") as f:
            out_bytes = f.read()

        self.respond_json({
            "status": "success",
            "output_b64": base64.b64encode(out_bytes).decode("utf-8"),
            "time_ms": int((t1 - t0) * 1000)
        })

    def respond_json(self, data, code=200):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))

def start_server(port=54321):
    server = HTTPServer(("127.0.0.1", port), DesktopStudioHandler)
    url = f"http://localhost:{port}"
    print("==========================================================")
    print("  abug (🐞) Standalone Desktop Studio")
    print(f"  Local Web UI Studio: {url}")
    print("  Press Ctrl+C to stop.")
    print("==========================================================")
    try:
        webbrowser.open(url)
    except Exception:
        pass
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping abug Desktop Studio...")
        server.server_close()

def main():
    parser = argparse.ArgumentParser(description="abug 🐞 Standalone AI Video Upscaler & Twixtor CLI")
    subparsers = parser.add_subparsers(dest="command", help="Commands")

    # Studio command (default)
    studio_parser = subparsers.add_parser("studio", help="Launch interactive Desktop Studio UI in browser")
    studio_parser.add_argument("--port", type=int, default=54321, help="Port to serve studio UI")

    # Upscale command
    up_parser = subparsers.add_parser("upscale", help="Upscale an image or frame")
    up_parser.add_argument("-i", "--input", required=True, help="Input image path")
    up_parser.add_argument("-o", "--output", required=True, help="Output image path")
    up_parser.add_argument("-s", "--scale", type=int, default=4, choices=[2, 3, 4], help="Upscaling scale factor")
    up_parser.add_argument("-m", "--model", default="realesr-anime-x4", choices=list(MODELS.keys()), help="Model preset")
    up_parser.add_argument("--tile-size", type=int, default=128, help="Tile size for memory management")

    # Twixtor command
    tw_parser = subparsers.add_parser("twixtor", help="Interpolate in-between frame between two frames")
    tw_parser.add_argument("-f0", "--frame0", required=True, help="First frame path")
    tw_parser.add_argument("-f1", "--frame1", required=True, help="Second frame path")
    tw_parser.add_argument("-o", "--output", required=True, help="Output interpolated frame path")
    tw_parser.add_argument("-t", "--timestep", type=float, default=0.5, help="Interpolation position (0.0 to 1.0)")
    tw_parser.add_argument("-m", "--model", default="rife-v4.6", choices=list(MODELS.keys()), help="RIFE model preset")

    args = parser.parse_args()

    if args.command == "upscale":
        model_info = MODELS[args.model]
        cmd = [
            CLI_BIN,
            "upscale",
            args.input,
            args.output,
            str(args.scale),
            model_info["path"],
            str(args.tile_size)
        ]
        ret = subprocess.run(cmd)
        sys.exit(ret.returncode)
    elif args.command == "twixtor":
        model_info = MODELS[args.model]
        cmd = [
            CLI_BIN,
            "twixtor",
            args.frame0,
            args.frame1,
            args.output,
            model_info["path"],
            str(args.timestep)
        ]
        ret = subprocess.run(cmd)
        sys.exit(ret.returncode)
    else:
        port = getattr(args, "port", 54321)
        start_server(port)

if __name__ == "__main__":
    main()
