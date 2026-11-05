#!/usr/bin/env bash
# shot.sh <run-name> <label>: screenshot display :99 to <run>/<label>.png and list windows.
set -uo pipefail
NAME="$1"; LABEL="$2"
BASE="${SAWHORSE_TEST_OUT:-$(cd "$(dirname "$0")" && pwd)/desktop-runs}"
OUT="$BASE/$NAME"; export DISPLAY=:99
import -window root "$OUT/$LABEL.png"
xdotool search --name '' 2>/dev/null | while read -r w; do
  echo "$w $(xdotool getwindowname "$w" 2>/dev/null) $(xdotool getwindowgeometry --shell "$w" 2>/dev/null | tr '\n' ' ')"
done > "$OUT/$LABEL.windows.txt"
echo "saved $OUT/$LABEL.png"; cat "$OUT/$LABEL.windows.txt"
