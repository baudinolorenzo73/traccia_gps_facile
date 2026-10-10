#!/usr/bin/env bash
# Optional local build; the 2.2 source delivery itself contains no APK.
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_SDK_ROOT:?Imposta il percorso Android SDK}"
: "${SIGNING_KEYSTORE:?Indica la chiave della beta già installata, conservata fuori dal repository}"
: "${SIGNING_STORE_PASSWORD:?Imposta la password del keystore nella sessione locale}"
: "${SIGNING_KEY_PASSWORD:?Imposta la password della chiave nella sessione locale}"
SIGNING_KEY_ALIAS="${SIGNING_KEY_ALIAS:-androiddebugkey}"
build_tools="$ANDROID_SDK_ROOT/build-tools/35.0.0"
platform_jar="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
output_dir="$project_root/build/manual"
apk_name="$(python3 - "$project_root/app/src/main/AndroidManifest.xml" <<'PYVERSION'
import re, sys, xml.etree.ElementTree as ET
v=ET.parse(sys.argv[1]).getroot().attrib['{http://schemas.android.com/apk/res/android}versionName']
if not re.fullmatch(r'[A-Za-z0-9.-]+',v):raise SystemExit('Invalid version name')
print('Traccia-GPS-v'+v+'.apk')
PYVERSION
)"
mkdir -p "$output_dir/classes" "$output_dir/dex" "$project_root/downloads"
"$build_tools/aapt2" compile --dir "$project_root/app/src/main/res" -o "$output_dir/resources.zip"
"$build_tools/aapt2" link -o "$output_dir/base.apk" -I "$platform_jar" --manifest "$project_root/app/src/main/AndroidManifest.xml" --rename-manifest-package it.lorenzo.tracciagps.beta2 --java "$output_dir/generated" --min-sdk-version 26 --target-sdk-version 35 "$output_dir/resources.zip"
find "$project_root/app/src/main/java" "$output_dir/generated" -name '*.java' > "$output_dir/sources.txt"
javac -encoding UTF-8 --release 17 -classpath "$platform_jar" -d "$output_dir/classes" @"$output_dir/sources.txt"
jar cf "$output_dir/classes.jar" -C "$output_dir/classes" .
"$build_tools/d8" --lib "$platform_jar" --min-api 26 --output "$output_dir/dex" "$output_dir/classes.jar"
cp "$output_dir/base.apk" "$output_dir/unsigned.apk"
(cd "$output_dir/dex" && zip -q "$output_dir/unsigned.apk" classes*.dex)
"$build_tools/zipalign" -f -p 4 "$output_dir/unsigned.apk" "$output_dir/aligned.apk"
"$build_tools/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-pass env:SIGNING_STORE_PASSWORD --ks-key-alias "$SIGNING_KEY_ALIAS" --key-pass env:SIGNING_KEY_PASSWORD --out "$project_root/downloads/$apk_name" "$output_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$project_root/downloads/$apk_name"
python3 "$project_root/tools/make-update.py" --apk "$project_root/downloads/$apk_name"
