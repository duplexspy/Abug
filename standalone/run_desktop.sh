#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Starting abug (🐞) Standalone Desktop Studio..."
python3 "$SCRIPT_DIR/abug_standalone.py"
