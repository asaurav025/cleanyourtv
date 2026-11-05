#!/usr/bin/env bash
# Build the My Home companion, install it on the TV and push its myhome.json (your lights and market symbols).
#   TV_HOST=<tv ip> addons/my-home/install.sh [myhome.json]
# Then push a theme with "paired_app": "dev.asaurav.myhome" (tools/theme.sh) so Home starts talking to it.
set -euo pipefail
CONFIG="${1:-}"
if [ -n "$CONFIG" ]; then
  [ -f "$CONFIG" ] || { echo "no such file: $CONFIG" >&2; exit 1; }
  python3 -c 'import json,sys; json.load(open(sys.argv[1]))' "$CONFIG" || { echo "not valid JSON: $CONFIG" >&2; exit 1; }
  CONFIG="$(cd "$(dirname "$CONFIG")" && pwd)/$(basename "$CONFIG")"
fi
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
PY=../../tools/venv/bin/python
[ -x "$PY" ] || PY=../../tools/venv/Scripts/python.exe
PKG=dev.asaurav.myhome
DIR=/sdcard/Android/data/$PKG/files
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=$ANDROID_HOME" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/my-home.apk
OUT=$(tv 'pm install -r /data/local/tmp/my-home.apk; rm -f /data/local/tmp/my-home.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# The note page has to answer from the background. Some firmware (TCL's TclAppBoot) refuses to start an app without
# AUTO_START, and an app has to be exempt from battery optimisation to start its foreground service from the background.
tv "cmd appops set $PKG AUTO_START allow 2>/dev/null; cmd deviceidle whitelist +$PKG >/dev/null 2>&1; true" >/dev/null

if [ -n "$CONFIG" ]; then
  # Start the app once so Android creates its files directory with the right owner, then copy the file in.
  tv "am start -n $PKG/.LightsActivity >/dev/null; sleep 2; input keyevent BACK" >/dev/null
  tv --push "$CONFIG" /data/local/tmp/myhome.json >/dev/null
  OUT=$(tv "mkdir -p $DIR 2>/dev/null; cp /data/local/tmp/myhome.json $DIR/myhome.json && echo copied; rm -f /data/local/tmp/myhome.json")
  case "$OUT" in *copied*) echo "myhome.json pushed" ;; *) echo "ERROR: could not write $DIR/myhome.json: $OUT" >&2; exit 1 ;; esac
fi
echo "Done. Home picks it up on its next return to the front."
