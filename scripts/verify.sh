#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon spotlessCheck
./gradlew --no-daemon :webview-inspector:testDebugUnitTest :webview-inspector:lintDebug \
  :webview-inspector-no-op:lintDebug :sample:lintDebug \
  :webview-inspector:assembleDebugAndroidTest :sample:assembleDebug :sample:assembleDiagnostic :sample:assembleRelease stageRelease
python3 scripts/check_api.py
python3 scripts/bundle.py
python3 -m unittest discover -s scripts/tests -v
version=$(sed -n 's/^VERSION_NAME=//p' gradle.properties)
./gradlew --no-daemon --refresh-dependencies -p consumer-fixture -PinspectorVersion="$version" \
  -PvalidationRepository="$PWD/build/validation-repository" :app:lintDebug :app:assembleDebug :app:assembleDiagnostic :app:assembleRelease
