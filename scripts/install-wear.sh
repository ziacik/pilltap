#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/gradle.sh"

SERIAL="${1:-${ANDROID_SERIAL:-}}"

pick_device() {
	local devices
	mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')

	if [[ -n "$SERIAL" ]]; then
		return
	fi

	if (( ${#devices[@]} == 0 )); then
		echo "No ADB device connected." >&2
		exit 1
	fi

	if (( ${#devices[@]} > 1 )); then
		echo "More than one ADB device is connected:" >&2
		printf '  %s\n' "${devices[@]}" >&2
		echo "Usage: $0 <adb-serial>" >&2
		echo "or set ANDROID_SERIAL." >&2
		exit 1
	fi

	SERIAL="${devices[0]}"
}

pick_device
ADB=(adb -s "$SERIAL")

echo "Building Wear OS APK..."
run_gradle :wear:assembleDebug

APK="$ROOT_DIR/wear/build/outputs/apk/debug/wear-debug.apk"
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }

echo "Installing Wear OS APK on $SERIAL..."
"${ADB[@]}" install -r "$APK"

echo "Done."
