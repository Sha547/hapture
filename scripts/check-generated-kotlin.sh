#!/usr/bin/env bash
# Compiles every Kotlin snippet the app generates (all interactions, at min/default/max slider values, plus
# token and haptic exports) against the real project's Compose dependencies. Fails if any doesn't compile.
set -uo pipefail
cd "$(dirname "$0")/.."
DEST=app/src/main/java/zzcheck
trap 'rm -rf "$DEST"' EXIT
./gradlew --offline -q :app:testDebugUnitTest --tests '*GeneratedKotlinDump*' >/dev/null 2>&1 || { echo "dump failed"; exit 2; }
rm -rf "$DEST"; mkdir -p "$DEST"; cp app/build/generated-check/*.kt "$DEST"/
echo "compiling $(ls "$DEST" | wc -l) generated files..."
OUT=$(./gradlew --offline :app:compileDebugKotlin 2>&1); STATUS=$?
echo "$OUT" | grep -E '^e: |error:' | sed 's|file://.*/zzcheck/||;s|/.*/stubs/debug/||' | sort -u
[ $STATUS -eq 0 ] && echo "OK: all generated Kotlin compiles" || echo "FAILED (exit $STATUS)"
exit $STATUS
