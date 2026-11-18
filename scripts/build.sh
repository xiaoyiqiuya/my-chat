#!/usr/bin/env bash
# 一键构建 DS Chat APK（本机 toolchain 路径按需修改）
set -euo pipefail

TOOLCHAIN="${TOOLCHAIN:-/d/dsh-android/toolchain}"
export JAVA_HOME="${JAVA_HOME:-$(cygpath -w "$TOOLCHAIN/jdk" 2>/dev/null || echo "$TOOLCHAIN/jdk")}"
export ANDROID_HOME="${ANDROID_HOME:-$(cygpath -w "$TOOLCHAIN/sdk" 2>/dev/null || echo "$TOOLCHAIN/sdk")}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/d/dsh-android/gradle-home}"

cd "$(dirname "$0")/.."
echo "sdk.dir=$(cygpath -w "$ANDROID_HOME" 2>/dev/null || echo "$ANDROID_HOME")" > local.properties

"$TOOLCHAIN/gradle/bin/gradle.bat" assembleDebug assembleRelease --no-daemon --console=plain "$@"

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/my-chat-0.1.3.apk
cp app/build/outputs/apk/debug/app-debug.apk dist/my-chat-0.1.3-debug.apk
ls -la dist
