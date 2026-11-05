#!/usr/bin/env bash
# Launch a Sawhorse desktop binary under Xvfb (display :99) with a D-Bus session and a
# gnome-keyring Secret Service, capturing stderr. Drive it afterwards with xdotool, grab
# screenshots with shot.sh, stop everything with stop.sh.
#   run-desktop.sh <binary> <run-name> [VAR=value ...]
# Output goes to $SAWHORSE_TEST_OUT/<run-name> (default: ./desktop-runs next to this script).
# Needs: xvfb, xdotool, imagemagick, dbus-x11, gnome-keyring. Tested on Ubuntu 24.04.
set -uo pipefail
BIN="$1"; NAME="$2"; shift 2
BASE="${SAWHORSE_TEST_OUT:-$(cd "$(dirname "$0")" && pwd)/desktop-runs}"
OUT="$BASE/$NAME"; mkdir -p "$OUT"
export DISPLAY=:99
Xvfb :99 -screen 0 1440x900x24 -nolisten tcp >"$OUT/xvfb.log" 2>&1 &
echo $! > "$OUT/xvfb.pid"
sleep 1
eval "$(dbus-launch --sh-syntax)"; export DBUS_SESSION_BUS_ADDRESS
echo "$DBUS_SESSION_BUS_PID" > "$OUT/dbus.pid"
echo -n "" | gnome-keyring-daemon --unlock --components=secrets >"$OUT/keyring.log" 2>&1 || true
export HOME="$OUT/home"; mkdir -p "$HOME"
export XDG_CONFIG_HOME="$HOME/.config" XDG_DATA_HOME="$HOME/.local/share" XDG_CACHE_HOME="$HOME/.cache"
for kv in "$@"; do export "$kv"; done
env | grep -E '^(WEBKIT|LIBGL|GDK|GTK|MESA|DISPLAY)' > "$OUT/env.txt"
( "$BIN" >"$OUT/app.stdout" 2>"$OUT/app.stderr" ) &
echo $! > "$OUT/app.pid"
echo "started $BIN as pid $(cat "$OUT/app.pid") on $DISPLAY; output in $OUT"
