#!/usr/bin/env bash

GRADLE_VERSION="9.6.1"

run_gradle() {
	if [[ -x "$ROOT_DIR/gradlew" ]]; then
		"$ROOT_DIR/gradlew" "$@"
		return
	fi

	local cache_root="${XDG_CACHE_HOME:-$HOME/.cache}/pilltap"
	local gradle_home="$cache_root/gradle-$GRADLE_VERSION"
	local gradle_bin="$gradle_home/bin/gradle"

	if [[ ! -x "$gradle_bin" ]]; then
		mkdir -p "$cache_root"
		local zip="$cache_root/gradle-$GRADLE_VERSION-bin.zip"
		local url="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

		echo "Gradle not found in the repo. Downloading Gradle $GRADLE_VERSION..."

		if command -v curl >/dev/null 2>&1; then
			curl -fL --retry 3 -o "$zip" "$url"
		elif command -v wget >/dev/null 2>&1; then
			wget -O "$zip" "$url"
		else
			echo "Need curl or wget to download Gradle." >&2
			exit 1
		fi

		rm -rf "$gradle_home"
		if command -v unzip >/dev/null 2>&1; then
			unzip -q "$zip" -d "$cache_root"
		elif command -v bsdtar >/dev/null 2>&1; then
			bsdtar -xf "$zip" -C "$cache_root"
		else
			echo "Need unzip or bsdtar to unpack Gradle." >&2
			exit 1
		fi
		rm -f "$zip"
	fi

	"$gradle_bin" -p "$ROOT_DIR" "$@"
}
