#!/usr/bin/env python3
"""
abug (🐞) Standalone Desktop Studio & AI CLI
Offline Open-Source AI Video Upscaler & Twixtor Frame Interpolation
Created by duplexspy (https://t.me/effectcraftport)
"""

import sys
import os
import json
import base64
import argparse
import subprocess
import webbrowser
from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse
from urllib.error import URLError
from urllib.request import urlopen

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))

# Prefer local models folder if distributed as standalone zip, else fallback to repo assets
LOCAL_MODELS_DIR = os.path.join(SCRIPT_DIR, "models")
REPO_MODELS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "models")
MODELS_DIR = LOCAL_MODELS_DIR if os.path.isdir(LOCAL_MODELS_DIR) else REPO_MODELS_DIR

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

STUDIO_HTML_PATH = os.path.join(SCRIPT_DIR, "studio.html")

# The native desktop executable is intentionally separate from the Android ELF binary.
# Users can also override this via ABUG_CLI_PATH for testing and custom builds.
def native_binary_path():
    override = os.environ.get("ABUG_CLI_PATH")
    if override:
        return os.path.abspath(override)
    if sys.platform == "darwin":
        return os.path.join(SCRIPT_DIR, "abug-cli-macos")
    if sys.platform == "win32":
        return os.path.join(SCRIPT_DIR, "abug-cli.exe")
    return os.path.join(SCRIPT_DIR, "abug-cli")

CLI_BIN = native_binary_path()

MAX_REQUEST_BYTES = 50 * 1024 * 1024
MAX_IMAGE_BYTES = 18 * 1024 * 1024
PROCESS_TIMEOUT_SECONDS = 900

# Only one large NCNN job at a time on memory-constrained Apple Silicon laptops.
import threading
_ENGINE_SLOT = threading.BoundedSemaphore(1)


def engine_health():
    import platform
    machine = platform.machine()
    message = "Native executable detected. Model inference has not been validated yet."
    ready = True
    if not os.path.isfile(CLI_BIN):
        ready = False
        message = "Missing native executable. On macOS run: bash standalone/build_macos.sh, then restart the server."
    elif not os.access(CLI_BIN, os.X_OK):
        ready = False
        message = "The native engine is not executable. Rebuild it for this system."
    else:
        with open(CLI_BIN, 'rb') as f:
            magic = f.read(4)
        is_elf = magic == bytes([0x7f, 69, 76, 70])
        if is_elf:
            # The archive's Android ELF interpreter cannot run on desktop Linux.
            with open(CLI_BIN, 'rb') as elf_file:
                android_linker = b'/system/bin/linker64' in elf_file.read(2 * 1024 * 1024)
        else:
            android_linker = False
        mach_o = magic in (bytes.fromhex('cffaedfe'), bytes.fromhex('feedfacf'),
                           bytes.fromhex('cafebabe'), bytes.fromhex('bebafeca'))
        if sys.platform == "darwin" and not mach_o:
            ready = False
            message = "Android/Linux ELF executable detected. Build the macOS version with bash standalone/build_macos.sh."
        if sys.platform.startswith("linux") and (not is_elf or android_linker):
            ready = False
            message = ("This is an Android ELF executable and cannot run on Linux."
                       if android_linker else "The native executable does not match Linux. Rebuild for this computer.")
    if ready and not all(os.path.isfile(model["path"]) for model in MODELS.values()):
        ready = False
        message = "One or more bundled neural model files are missing. Restore the complete assets/models folder."
    return {
        "app": "abug-studio",
        "edition": "neural-motion",
        "ready": ready,
        "message": message,
        "platform": platform.system(),
        "architecture": machine,
        "binary": os.path.basename(CLI_BIN),
    }


class ProcessingError(Exception):
    def __init__(self, text, http_code=400):
        super().__init__(text)
        self.http_code = http_code


def image_bytes(value):
    if not isinstance(value, str):
        raise ProcessingError("No image was provided.")
    try:
        raw = base64.b64decode(value, validate=True)
    except Exception as exc:
        # Incorrect/partial base64 must not terminate the HTTP request.
        raise ProcessingError("Invalid image encoding.") from exc
    if not raw or len(raw) > MAX_IMAGE_BYTES:
        raise ProcessingError("Image is empty or exceeds the 18 MB limit.")
    # The browser uploads PNG bytes after canvas conversion.
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ProcessingError("Expected a PNG image. Reopen the image in the updated studio interface.")
    if len(raw) < 24:
        raise ProcessingError("Image header is incomplete.")
    return raw


def png_dimensions(data):
    import struct
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ProcessingError("Native engine did not produce a valid PNG file.", 500)
    width, height = struct.unpack(">II", data[16:24])
    if not (0 < width <= 32768 and 0 < height <= 32768):
        raise ProcessingError("Invalid output image dimensions.", 500)
    return width, height


def execute_native(cmd, output_path):
    health = engine_health()
    if not health["ready"]:
        raise ProcessingError(health["message"], 503)
    import time
    if not _ENGINE_SLOT.acquire(blocking=False):
        raise ProcessingError("Another render is already running. Wait for it to finish.", 429)
    start = time.monotonic()
    try:
        try:
            cwd_path = PROJECT_ROOT if os.path.isdir(os.path.join(PROJECT_ROOT, "app")) else SCRIPT_DIR
            result = subprocess.run(cmd, cwd=cwd_path, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE, timeout=PROCESS_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            raise ProcessingError("AI render timed out after 15 minutes. Try a smaller image or tile size.", 504)
        except OSError as exc:
            raise ProcessingError("Could not start native AI engine: " + str(exc), 503)
    finally:
        _ENGINE_SLOT.release()
    if result.returncode != 0:
        err = result.stderr.decode('utf-8', errors='replace').strip()
        if not err:
            err = result.stdout.decode('utf-8', errors='replace').strip()
        raise ProcessingError("Inference failed (exit %s): %s" % (result.returncode, err[-1200:] or "no error output"), 500)
    if not os.path.isfile(output_path) or os.path.getsize(output_path) == 0:
        raise ProcessingError("The engine finished without producing an output image.", 500)
    return int((time.monotonic() - start) * 1000)

class DesktopStudioHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[abug HTTP] " + (fmt % args), file=sys.stderr)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path in ("/", "/index.html"):
            try:
                with open(STUDIO_HTML_PATH, 'rb') as f:
                    html = f.read()
            except OSError as exc:
                self.respond_json({"status": "error", "error": str(exc)}, 500)
                return
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(html)
        elif parsed.path == "/api/health":
            self.respond_json(engine_health())
        elif parsed.path == "/api/models":
            self.respond_json({"models": {key: {"name": value["name"],
                            "type": value["type"], "available": os.path.isfile(value["path"])}
                             for key, value in MODELS.items()}})
        else:
            self.respond_json({"status": "error", "error": "Endpoint not found"}, 404)

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path not in ("/api/upscale", "/api/twixtor"):
            self.respond_json({"status": "error", "error": "Endpoint not found"}, 404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_REQUEST_BYTES:
                raise ProcessingError("Request exceeds upload limit or is empty.", 413)
            if "application/json" not in self.headers.get("Content-Type", ""):
                raise ProcessingError("Expected JSON data.", 415)
            try:
                req = json.loads(self.rfile.read(length).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                raise ProcessingError("Request is not valid JSON.")
            if not isinstance(req, dict):
                raise ProcessingError("Expected a JSON object.")
            if parsed.path == "/api/upscale":
                result = self.handle_upscale(req)
            else:
                result = self.handle_twixtor(req)
            self.respond_json(result)
        except ProcessingError as exc:
            self.respond_json({"status": "error", "error": str(exc)}, exc.http_code)
        except Exception as exc:
            import traceback
            traceback.print_exc()
            self.respond_json({"status": "error", "error": "Unexpected server error: " + str(exc)}, 500)

    def handle_upscale(self, req):
        import tempfile
        model_key = req.get("model", "realesr-anime-x4")
        if not isinstance(model_key, str) or model_key not in MODELS or MODELS[model_key]["type"] != "upscale":
            raise ProcessingError("Unknown or invalid upscaling model.")
        model = MODELS[model_key]
        if not os.path.isfile(model["path"]):
            raise ProcessingError("Selected neural model file is missing.", 500)
        try:
            tile = int(req.get("tile_size", 128))
        except (ValueError, TypeError):
            raise ProcessingError("Invalid tile size.")
        if tile not in (64, 128, 256):
            raise ProcessingError("Tile size must be 64, 128 or 256.")
        src = image_bytes(req.get("image_b64"))
        with tempfile.TemporaryDirectory(prefix="abug_") as tmp:
            input_path = os.path.join(tmp, "input.png")
            output_path = os.path.join(tmp, "result.png")
            with open(input_path, "wb") as f:
                f.write(src)
            ms = execute_native([CLI_BIN, "upscale", input_path, output_path,
                                 str(model["scale"]), model["path"], str(tile)], output_path)
            with open(output_path, 'rb') as f:
                output = f.read()
            width, height = png_dimensions(output)
            return {"status": "success", "output_b64": base64.b64encode(output).decode('ascii'),
                    "time_ms": ms, "width": width, "height": height}

    def handle_twixtor(self, req):
        import tempfile
        model_key = req.get("model", "rife-v4.6")
        if not isinstance(model_key, str) or model_key not in MODELS or MODELS[model_key]["type"] != "twixtor":
            raise ProcessingError("Unknown or invalid interpolation model.")
        model = MODELS[model_key]
        if not os.path.isfile(model["path"]):
            raise ProcessingError("Selected neural model file is missing.", 500)
        try:
            timestep = float(req.get("timestep", 0.5))
        except (ValueError, TypeError):
            raise ProcessingError("Invalid timestep.")
        if timestep not in (0.25, 0.5, 0.75):
            raise ProcessingError("Timestep must be 0.25, 0.5 or 0.75.")
        source0 = image_bytes(req.get("frame0_b64"))
        source1 = image_bytes(req.get("frame1_b64"))
        if png_dimensions(source0) != png_dimensions(source1):
            raise ProcessingError("Start and end frames must have the same width and height.")
        with tempfile.TemporaryDirectory(prefix="abug_") as tmp:
            a = os.path.join(tmp, "a.png")
            b = os.path.join(tmp, "b.png")
            output_path = os.path.join(tmp, "result.png")
            with open(a, 'wb') as f:
                f.write(source0)
            with open(b, 'wb') as f:
                f.write(source1)
            ms = execute_native([CLI_BIN, "twixtor", a, b, output_path,
                                 model["path"], str(timestep)], output_path)
            with open(output_path, 'rb') as f:
                output = f.read()
            width, height = png_dimensions(output)
            return {"status": "success", "output_b64": base64.b64encode(output).decode('ascii'),
                    "time_ms": ms, "width": width, "height": height}

    def respond_json(self, data, code=200):
        payload = json.dumps(data).encode("utf-8")
        try:
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError):
            pass


def open_studio_browser(url):
    """Prefer Chrome on macOS; use the system default only when unavailable."""
    if os.environ.get("ABUG_NO_BROWSER") == "1":
        return
    if sys.platform == "darwin":
        try:
            subprocess.run(["open", "-a", "Google Chrome", url],
                           check=True, timeout=10,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print("Opened in Google Chrome:", url, flush=True)
            return
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            print("Chrome not found; opening the system default browser.", flush=True)
    try:
        webbrowser.open(url)
    except Exception as exc:
        print("Open this address manually:", url, "(browser error:", exc, ")", flush=True)


def is_abug_server(port):
    try:
        with urlopen(f"http://127.0.0.1:{port}/api/health", timeout=2) as response:
            data = json.load(response)
        return (isinstance(data, dict) and data.get("app") == "abug-studio"
                and data.get("edition") == "neural-motion")
    except (OSError, URLError, ValueError, json.JSONDecodeError):
        return False


def start_server(port=54322):
    from http.server import ThreadingHTTPServer
    class StudioServer(ThreadingHTTPServer):
        daemon_threads = True

    # An existing studio should be reopened, not started a second time.
    # If another app owns the chosen port, use the next available port.
    server = None
    for candidate in range(port, min(port + 15, 65536)):
        if is_abug_server(candidate):
            url = f"http://127.0.0.1:{candidate}"
            print("Existing abug Studio detected at", url, flush=True)
            open_studio_browser(url)
            return
        try:
            server = StudioServer(("127.0.0.1", candidate), DesktopStudioHandler)
            port = candidate
            break
        except OSError as exc:
            if getattr(exc, "errno", None) not in (48, 98, 10048):
                raise
    if server is None:
        raise SystemExit("Could not start the studio: ports %s through %s are occupied." % (port, min(port + 14, 65535)))
    url = f"http://127.0.0.1:{port}"
    print("==========================================================")
    print(" abug Desktop Studio — Neural Motion Edition")
    print(" URL:", url)
    print(" Native engine:", engine_health()["message"])
    print(" Press Ctrl+C to stop.")
    print("==========================================================", flush=True)
    open_studio_browser(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping abug Studio...")
    finally:
        server.server_close()

def main():
    parser = argparse.ArgumentParser(description="abug 🐞 Standalone AI Video Upscaler & Twixtor CLI")
    subparsers = parser.add_subparsers(dest="command", help="Commands")

    # Studio command (default)
    studio_parser = subparsers.add_parser("studio", help="Launch interactive Desktop Studio UI in browser")
    studio_parser.add_argument("--port", type=int, default=54322, help="Port to serve studio UI")

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

    cwd_path = PROJECT_ROOT if os.path.isdir(os.path.join(PROJECT_ROOT, "app")) else SCRIPT_DIR
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
        try:
            ret = subprocess.run(cmd, cwd=cwd_path)
        except OSError as exc:
            sys.exit("Cannot start native AI engine: " + str(exc))
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
        try:
            ret = subprocess.run(cmd, cwd=cwd_path)
        except OSError as exc:
            sys.exit("Cannot start native AI engine: " + str(exc))
        sys.exit(ret.returncode)
    else:
        port = getattr(args, "port", 54322)
        start_server(port)

if __name__ == "__main__":
    main()
