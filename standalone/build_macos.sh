#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This build script runs on macOS only." >&2
  exit 1
fi
if ! command -v brew >/dev/null 2>&1; then
  echo "Homebrew is required. Install Homebrew and run: brew install ncnn libomp" >&2
  exit 1
fi
if ! brew --prefix ncnn >/dev/null 2>&1; then
  echo "NCNN is not installed. Run: brew install ncnn libomp" >&2
  exit 1
fi
NCNN_PREFIX="$(brew --prefix ncnn)"
if [[ ! -f "$NCNN_PREFIX/lib/cmake/ncnn/ncnnConfig.cmake" && ! -f "$NCNN_PREFIX/share/ncnn/ncnnConfig.cmake" ]]; then
  echo "NCNN CMake config not found in $NCNN_PREFIX" >&2
  echo "Run 'brew list ncnn' to see whether the library development files are installed." >&2
  exit 2
fi
BREW_PREFIX="$(brew --prefix)"
BUILD_DIR="$SCRIPT_DIR/build-macos"
echo "Building macOS native AI engine against $NCNN_PREFIX"
cmake -S "$SCRIPT_DIR" -B "$BUILD_DIR" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$NCNN_PREFIX;$BREW_PREFIX/opt/libomp;$BREW_PREFIX" \
  -DCMAKE_OSX_ARCHITECTURES="$(uname -m)"
cmake --build "$BUILD_DIR" --config Release --parallel 4
if [[ ! -x "$SCRIPT_DIR/abug-cli-macos" ]]; then
  echo "Build did not produce abug-cli-macos" >&2
  exit 3
fi
echo "Built $(file "$SCRIPT_DIR/abug-cli-macos")"
echo "Restart the Python server: python3 standalone/abug_standalone.py"
