#!/usr/bin/env bash
# Runs the code the app generates on a connected device/emulator with real touch input
# (scripts/generated-tests/GeneratedComposeBehaviorTest.kt). Needs an emulator/device and ANDROID_HOME.
set -uo pipefail
cd "$(dirname "$0")/.."
DEST=app/src/androidTest/java/zzgen
TEST=app/src/androidTest/java/com/motionlab/app/GeneratedComposeBehaviorTest.kt
# The test depends on the generated files, so it only exists in the source set while this script runs.
trap 'rm -rf "$DEST" "$TEST"' EXIT
./gradlew --offline -q :app:testDebugUnitTest --tests '*GeneratedKotlinDump*' >/dev/null 2>&1 || { echo "dump failed"; exit 2; }
rm -rf "$DEST"; mkdir -p "$DEST"
cp scripts/generated-tests/GeneratedComposeBehaviorTest.kt "$TEST"
cp app/build/generated-check/*_mid.kt "$DEST"/
rm -f app/build/outputs/androidTest-results/connected/debug/*.xml
./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.motionlab.app.GeneratedComposeBehaviorTest 2>&1 | grep -E '^e: |error:|FAILED|BUILD' | sort -u | head -20
python3 - <<'PY'
import glob,re
for f in glob.glob("app/build/outputs/androidTest-results/connected/debug/*.xml"):
    x=open(f).read()
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)',x,re.S):
        fail=re.search(r'<failure[^>]*>(.*?)</failure>',m.group(0),re.S)
        print(("FAIL " if fail else "ok   ")+m.group(1)+((" -> "+fail.group(1).strip().splitlines()[0][:230]) if fail else ""))
PY
