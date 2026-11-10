#!/bin/bash
# Builds the Munhana APK with raw Android SDK tools (no Gradle needed).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-/mnt/Data/android-sdk}"
BT="$SDK/build-tools/35.0.0"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
JAVAC="${JAVAC:-javac}"

AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"

KEYSTORE="${MUNHANA_KEYSTORE:-$ROOT/app/dev-release.jks}"
KS_PASS="${MUNHANA_KS_PASS:-munhana-dev}"
KS_ALIAS="${MUNHANA_KS_ALIAS:-munhana}"

if [ ! -f "$KEYSTORE" ]; then
  echo "No keystore found; generating a development key at $KEYSTORE"
  keytool -genkeypair -keystore "$KEYSTORE" \
    -storepass "$KS_PASS" -keypass "$KS_PASS" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Munhana Dev, O=Munhana, C=SA" >/dev/null 2>&1
fi

SRC="$ROOT/app/src/main"
BUILD="$ROOT/build"
OUT="$ROOT/dist"
LIBS="$ROOT/libs"

for tool in "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER" "$ANDROID_JAR"; do
  [ -e "$tool" ] || { echo "missing toolchain piece: $tool"; exit 1; }
done

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT" "$BUILD/stage"

echo "[1/8] compiling resources"
"$AAPT2" compile --dir "$SRC/res" -o "$BUILD/res/resources.zip"

echo "[2/8] linking resources + assets"
"$AAPT2" link \
  -o "$BUILD/unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$SRC/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 35 \
  --auto-add-overlay \
  -A "$SRC/assets" \
  "$BUILD/res/resources.zip"

echo "[3/8] compiling java"
find "$SRC/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
set +e
"$JAVAC" -source 17 -target 17 \
  -classpath "$ANDROID_JAR:$LIBS/tess-two-classes.jar" \
  -d "$BUILD/classes" \
  -encoding UTF-8 \
  @"$BUILD/sources.txt" 2>&1 | grep -vE 'location of system modules|--release 17 is recommended|^[0-9]+ warning'
JAVAC_STATUS=${PIPESTATUS[0]}
set -e
if [ "$JAVAC_STATUS" -ne 0 ] || [ ! -f "$BUILD/classes/com/munhana/graph/MainActivity.class" ]; then
  echo "javac failed"; exit 1
fi

echo "[4/8] dexing"
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.txt"
"$D8" --release --min-api 24 --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" \
  $(cat "$BUILD/classes.txt") "$LIBS/tess-two-classes.jar"

echo "[5/8] packaging dex"
cp "$BUILD/unsigned.apk" "$BUILD/step1.apk"
(cd "$BUILD/dex" && zip -q -j "$BUILD/step1.apk" classes*.dex)

echo "[6/8] packaging native libraries"
cp -r "$LIBS/jni" "$BUILD/stage/lib"
(cd "$BUILD/stage" && zip -q -r "$BUILD/step1.apk" lib)

echo "[7/8] aligning"
"$ZIPALIGN" -f -p 4 "$BUILD/step1.apk" "$BUILD/aligned.apk"

echo "[8/8] signing"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$OUT/munhana-1.2.apk" "$BUILD/aligned.apk"

"$APKSIGNER" verify --print-certs "$OUT/munhana-1.2.apk" | head -4
echo
echo "APK: $OUT/munhana-1.2.apk"
ls -lh "$OUT/munhana-1.2.apk"
