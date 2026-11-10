#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "This launcher is for macOS. Other platforms can run standalone/abug_standalone.py directly." >&2
    exit 1
fi
echo "abug Studio — Mac M1/M2/M3/M4 launcher"
if [[ -d standalone ]]; then
    DIR="standalone"
else
    DIR="."
fi

if [[ ! -x "$DIR/abug-cli-macos" ]] || [[ "$DIR/abug_cli.cpp" -nt "$DIR/abug-cli-macos" ]] || [[ "$DIR/CMakeLists.txt" -nt "$DIR/abug-cli-macos" ]] ; then
    echo "Building the macOS AI engine (this may be needed only on the first run)..."
    bash "$DIR/build_macos.sh"
fi
# Python will open Google Chrome automatically after the local server binds.
exec python3 "$DIR/abug_standalone.py" studio --port "${1:-54322}"
