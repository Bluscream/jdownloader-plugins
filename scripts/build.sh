#!/usr/bin/env bash
set -euo pipefail

# Find JDownloader directory or use /var/mnt/nas/appdata/jdownloader if available
JD_DIR="${1:-${JD_DIR:-/var/mnt/nas/appdata/jdownloader}}"

if [[ ! -f "$JD_DIR/Core.jar" ]]; then
    echo "Error: Core.jar not found in $JD_DIR"
    echo "Usage: $0 [path/to/jdownloader]"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

echo "==> Building JDownloader plugins against $JD_DIR..."
mkdir -p "$REPO_ROOT/dist"

CP="$JD_DIR/Core.jar:$JD_DIR/JDownloader.jar:$JD_DIR/libs/*"

javac --release 8 \
    -cp "$CP" \
    -d "$REPO_ROOT/dist" \
    $(find "$REPO_ROOT/src" -name "*.java")

echo "==> Build successful! Compiled classes saved to $REPO_ROOT/dist"
