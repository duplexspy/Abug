#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "$(uname -s)" == "Darwin" ]] && [[ -f "$SCRIPT_DIR/../RUN_MAC_STUDIO.command" ]]; then
    exec /bin/bash "$SCRIPT_DIR/../RUN_MAC_STUDIO.command" "$@"
fi

echo "Starting abug (🐞) Standalone Desktop Studio..."
if command -v python3 >/dev/null 2>&1; then
    exec python3 "$SCRIPT_DIR/abug_standalone.py" studio "$@"
elif command -v python >/dev/null 2>&1; then
    exec python "$SCRIPT_DIR/abug_standalone.py" studio "$@"
else
    echo "[ERROR] Python 3 was not found. Please install Python 3."
    exit 1
fi
