#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/gradle.sh"

SERIAL="${1:-${ANDROID_SERIAL:-}}"

pick_device() {
	if [[ -n "$SERIAL" ]]; then
		return
	fi

	local device_serials=()
	local device_labels=()

	while IFS= read -r line; do
		[[ -z "$line" || "$line" == "List of devices attached" ]] && continue

		if [[ "$line" =~ ^(.*[^[:space:]])[[:space:]]+device([[:space:]].*)?$ ]]; then
			local serial="${BASH_REMATCH[1]}"
			local details="${BASH_REMATCH[2]:-}"
			local model=""
			local product=""

			if [[ "$details" =~ model:([^[:space:]]+) ]]; then
				model="${BASH_REMATCH[1]}"
			fi
			if [[ "$details" =~ product:([^[:space:]]+) ]]; then
				product="${BASH_REMATCH[1]}"
			fi

			device_serials+=("$serial")

			local label=""
			[[ -n "$model" ]] && label+="model:$model"
			if [[ -n "$product" ]]; then
				[[ -n "$label" ]] && label+="  "
				label+="product:$product"
			fi
			[[ -n "$label" ]] && label+="  "
			label+="$serial"
			device_labels+=("$label")
		fi
	done < <(adb devices -l)

	case "${#device_serials[@]}" in
		0)
			echo "No ADB device connected." >&2
			exit 1
			;;
		1)
			SERIAL="${device_serials[0]}"
			;;
		*)
			echo "Multiple ADB targets found:"
			PS3="Select target: "
			select label in "${device_labels[@]}"; do
				if [[ -n "$label" ]]; then
					SERIAL="${device_serials[REPLY - 1]}"
					break
				fi
				echo "Invalid selection." >&2
			done
			;;
	esac
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
