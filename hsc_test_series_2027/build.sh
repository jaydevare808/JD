#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

mkdir -p public
SDK="$HOME/android-sdk"
TOOLS="$SDK/cmdline-tools/latest"
GRADLE_HOME="$HOME/gradle-9.6.0"

if ! command -v java >/dev/null 2>&1; then
  mkdir -p "$HOME/jdk"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -o "$HOME/jdk.tar.gz"
  tar -xzf "$HOME/jdk.tar.gz" -C "$HOME/jdk" --strip-components=1
  export JAVA_HOME="$HOME/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ ! -x "$TOOLS/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip" -o "$HOME/cmdline-tools.zip"
  rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/temp"
  mkdir -p "$SDK/cmdline-tools/temp"
  unzip -q "$HOME/cmdline-tools.zip" -d "$SDK/cmdline-tools/temp"
  mv "$SDK/cmdline-tools/temp/cmdline-tools" "$TOOLS"
fi

export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
export PATH="$TOOLS/bin:$SDK/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" >/dev/null

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  curl -fsSL "https://services.gradle.org/distributions/gradle-9.6.0-bin.zip" -o "$HOME/gradle.zip"
  rm -rf "$GRADLE_HOME" "$HOME/gradle-extract"
  mkdir -p "$HOME/gradle-extract"
  unzip -q "$HOME/gradle.zip" -d "$HOME/gradle-extract"
  mv "$HOME/gradle-extract/gradle-9.6.0" "$GRADLE_HOME"
fi

"$GRADLE_HOME/bin/gradle" assembleDebug --no-daemon

cp app/build/outputs/apk/debug/app-debug.apk public/HSC_TEST_SERIES_2027-debug.apk
cat > public/index.html <<'EOF'
<!doctype html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>HSC TEST SERIES 2027</title>
<style>body{font-family:system-ui,sans-serif;max-width:700px;margin:60px auto;padding:24px;background:#f6f7fb;color:#171725}a{display:inline-block;padding:14px 20px;background:#5b3fd4;color:white;border-radius:12px;text-decoration:none;font-weight:700}.card{background:#fff;padding:28px;border:1px solid #e4e4ec;border-radius:20px}</style></head>
<body><div class="card"><h1>HSC TEST SERIES 2027</h1><p>Debug APK for Android device testing.</p><a href="/HSC_TEST_SERIES_2027-debug.apk">Download APK</a></div></body></html>
EOF
