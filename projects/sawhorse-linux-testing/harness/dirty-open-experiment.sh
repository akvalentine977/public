#!/usr/bin/env bash
# Does File > Open work on Linux when the document has unsaved changes?
# Usage: dirty-open-experiment.sh <binary> <run-name>
set -uo pipefail
H="$(cd "$(dirname "$0")" && pwd)"; BIN="$1"; NAME="$2"
OUTBASE="${SAWHORSE_TEST_OUT:-$H/desktop-runs}"
O="$OUTBASE/$NAME"; D="$O/home"
w(){ timeout "$1" bash -c 'while :; do :; done' 2>/dev/null; }
chip(){ convert "$O/$1.png" -crop 1000x26+60+78 +repage "$O/$1-chip.png"; convert "$O/$1.png" -crop 1080x40+0+858 +repage "$O/$1-status.png"; }
"$H/run-desktop.sh" "$BIN" "$NAME" >/dev/null
export DISPLAY=:99
timeout 90 xdotool search --sync --name '^Sawhorse$' >/dev/null 2>&1; w 6
xdotool mousemove 700 450
draw(){ xdotool key --delay 150 Escape; xdotool mousemove "$3" "$4"; xdotool key --delay 150 r; xdotool type --delay 60 "$1"; xdotool key --delay 200 Return; xdotool key --delay 300 p; xdotool type --delay 60 "$2"; xdotool key --delay 200 Return; w 2; }
saveas(){ xdotool mousemove 700 450; xdotool key --delay 150 ctrl+shift+s; w 3; xdotool key ctrl+a; paste "$1"; xdotool key Return; w 4; }
paste(){ printf '%s' "$1" | xclip -selection clipboard -in -loops 1 >/dev/null 2>&1 & w 1; xdotool key ctrl+v; w 1; }
openf(){ xdotool mousemove 700 450; xdotool key --delay 150 ctrl+o; w 3; xdotool key ctrl+l; w 1; paste "$1"; "$H/shot.sh" "$NAME" "$2-typed" >/dev/null; xdotool key Return; w 1; "$H/shot.sh" "$NAME" "$2-now" >/dev/null; w 4; "$H/shot.sh" "$NAME" "$2" >/dev/null; chip "$2-now"; chip "$2"; }
echo "1. board A"; draw '36, 12' '3/4' 700 450; "$H/shot.sh" "$NAME" s1 >/dev/null; chip s1
echo "2. save as one.shz"; saveas "$D/one.shz"
echo "3. board B"; draw '10, 10' '1' 900 300; "$H/shot.sh" "$NAME" s3 >/dev/null; chip s3
echo "4. save as two.shz"; saveas "$D/two.shz"; ls -la "$D" | grep shz
echo "5. open one.shz while CLEAN (expect 1 solid)"; openf "$D/one.shz" open-clean
echo "6. board C (dirty again)"; draw '5, 5' '1' 950 250; "$H/shot.sh" "$NAME" s6 >/dev/null; chip s6
echo "7. open two.shz while DIRTY (expect prompt or 2 solids 424 in3)"; openf "$D/two.shz" open-dirty
echo "--- stderr ---"; tail -4 "$O/app.stderr"
