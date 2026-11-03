#!/usr/bin/env bash
# Build a self-contained Tulita desktop app image for the CURRENT operating system.
# The resulting folder runs without Maven, without a JDK and without this source tree.
set -euo pipefail
cd "$(dirname "$0")"

APP_NAME="Tulita"
DEST="dist"
INPUT="target/app-input"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage was not found. Install a full JDK 17 or 21 (not a JRE) and run this script again."
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven was not found on PATH."
  exit 1
fi

echo "1/4  Building the application jar (tests skipped)..."
mvn -q -DskipTests package

echo "2/4  Collecting runtime libraries..."
rm -rf "$INPUT"
mkdir -p "$INPUT"
mvn -q dependency:copy-dependencies -DoutputDirectory="$INPUT" -DincludeScope=runtime
cp target/tulita-*.jar "$INPUT"/ 2>/dev/null || cp target/*.jar "$INPUT"/

echo "3/4  Packaging a self-contained app image..."
rm -rf "$DEST/$APP_NAME"
jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version 3.0.0 \
  --input "$INPUT" \
  --main-jar "$(cd "$INPUT" && ls tulita*.jar | head -1)" \
  --main-class tulita.Launcher \
  --dest "$DEST" \
  --java-options "-Xmx1024m" \
  --java-options "-Dfile.encoding=UTF-8" \

echo "4/4  Copying the curated catalog next to the launcher..."
mkdir -p "$DEST/$APP_NAME/datasets"
cp -R datasets/promap "$DEST/$APP_NAME/datasets/promap"

# Ship photos only. The 89 MB model is NO LONGER copied automatically, because some
# git hosts and file-sharing tools reject files over 50 MB. Include it explicitly:
#   TULITA_INCLUDE_MODEL=1 ./package-app.sh
MODEL_SRC="${TULITA_MODEL_SOURCE:-$HOME/.tulita/clip-vision-int8.onnx}"
if [ "${TULITA_INCLUDE_MODEL:-0}" = "1" ] && [ -f "$MODEL_SRC" ]; then
  mkdir -p "$DEST/$APP_NAME/model"
  cp "$MODEL_SRC" "$DEST/$APP_NAME/model/clip-vision-int8.onnx"
  echo "Model copied from $MODEL_SRC (fully offline photo search)."
else
  echo "Model NOT copied (keeps the build under 300 MB)."
  echo "First launch offers the verified 89 MB download, or re-run with TULITA_INCLUDE_MODEL=1."
fi

echo
echo "Optional zip for sharing: (cd $DEST && zip -qr Tulita-$(uname -s)-$(uname -m).zip $APP_NAME)"

echo
echo "Done: $DEST/$APP_NAME"
echo "Run it with: $DEST/$APP_NAME/bin/$APP_NAME"
echo "Copy the whole '$APP_NAME' folder to another computer with the same OS to run it there."
