#!/usr/bin/env bash
# stop.sh <run-name>: stop the app, the keyring, D-Bus and Xvfb started by run-desktop.sh.
NAME="$1"
BASE="${SAWHORSE_TEST_OUT:-$(cd "$(dirname "$0")" && pwd)/desktop-runs}"
OUT="$BASE/$NAME"
for f in app dbus xvfb; do [ -f "$OUT/$f.pid" ] && kill "$(cat "$OUT/$f.pid")" 2>/dev/null; done
pkill -f gnome-keyring-daemon 2>/dev/null; pkill -f 'Xvfb :99' 2>/dev/null
sleep 1; echo "stopped $NAME"
