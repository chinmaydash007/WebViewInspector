# Contributing

Repository access is currently private. Consumers can use public Maven Central artifacts after release, but private source browsing and GitHub issues require granted access.

Use JDK 17, the committed wrapper, and Android SDK 36. Run `bash scripts/verify.sh`; run native coverage on explicitly selected API 24 and supported API 36 emulators. Preserve the five original methods and two load helpers with full/no-op parity. Keep both artifacts versioned together. Runtime changes need tests and architecture notes. Advanced capture is opt-in via the helper; never add production telemetry or implicit client interception.

Open changes against main. CI validates without signing or Central credentials. Maintainers prepare a changelog, run signed dry-run and native gates, then publish the private GitHub Release to trigger public Central publication. Follow docs/RELEASING.md; ordinary merges never publish.
