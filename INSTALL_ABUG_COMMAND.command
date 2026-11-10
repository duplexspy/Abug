#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "This command installer is intended for macOS." >&2
    exit 1
fi
mkdir -p "$HOME/bin"
# Make a stable launcher with an absolute path to the extracted project.
{
  printf '#!/bin/bash\n'
  printf 'exec /bin/bash %q "$@"\n' "$PROJECT_DIR/RUN_MAC_STUDIO.command"
} > "$HOME/bin/abug"
chmod +x "$HOME/bin/abug"
RC="$HOME/.zshrc"
if ! grep -Fq 'export PATH="$HOME/bin:$PATH"' "$RC" 2>/dev/null; then
    printf '\n# abug Studio launcher\nexport PATH="$HOME/bin:$PATH"\n' >> "$RC"
fi
printf 'Installed the command: abug\nTo activate it in this Terminal: source ~/.zshrc\nThen type: abug\n'
