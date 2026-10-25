# Contributing

Repository access is currently private. Consumers can use public Maven Central artifacts after release, but private source browsing and GitHub issues require granted access.

Use JDK 17, the committed wrapper, and Android SDK 36. Run `bash scripts/verify.sh`; run native coverage on explicitly selected API 23 and supported API 36 emulators. Keep the five-method facade compatible and both artifacts versioned together. Changes to runtime behavior require corresponding tests and architecture notes. Never add production URL telemetry or per-WebView-screen hooks.

Open changes against main. CI validates without signing or Central credentials. Maintainers prepare a changelog, run signed dry-run and native gates, then publish the private GitHub Release to trigger public Central publication. Follow docs/RELEASING.md; ordinary merges never publish.
