# Menene adult recovery and 50/500 fixture handoff — 2026-07-31

## Outcome

Recovery is fixed and verified on the final same-key release. The release is not declared GO: the Manager still owns the independent final playback, G8 interpretation, and soak gates.

## Exact tree and scope

- Worktree: `/tmp/menene-luna-rebuild-fix`
- Branch: `luna/menene-rebuild-fix`
- Base before this lane: `8d43f5e86d6999d039a388c936fd8cf8eb2c2abc`
- Source changes: `MainActivity.kt`, `AdminTapDetector.kt`, `AdminTapDetectorTest.kt`
- Evidence: this report and `2026-07-31-menene-recovery-admin.png`
- No changes to manifest, applicationId, database schema, signing material, qualified library, player, branding, or kiosk policy.

## Recovery change

The old click-only listener did not reliably receive the SM-T585/API27 low-level injected taps. The new small `AdminTapDetector` counts raw `ACTION_DOWN` events, preserves five taps within 3000 ms, resets after expiry or success, and calls `performClick()` on `ACTION_UP` for accessibility. No visible button, exported component, deep link, shell bypass, or debuggable release path was added.

Unit coverage: 4 detector tests (fifth tap, expiry reset, exact boundary, success reset), all passing in both debug and release unit suites. Each suite has 14 tests, 0 failures, 0 errors.

## Build and signing evidence

- `env -u PYTHONHOME -u PYTHONPATH /usr/bin/python3 tools/validate_repo.py`: exit 0; 47 XML files, manifest/policy/builder/version checks pass.
- Exact-tree clean build with dependency verification: `./gradlew --no-daemon clean test lintDebug lintRelease assembleDebug assembleRelease bundleRelease`: exit 0, BUILD SUCCESSFUL.
- Connected retry after dismissing the transient Play Protect prompt: `./gradlew --no-daemon connectedDebugAndroidTest`: exit 0, 17/17 on SM-T585/API27. The earlier attempt started 0 tests and failed only in UTP device setup with `Connection refused`; it is retained as a transient harness failure, not counted as PASS.
- Final signed `assembleRelease bundleRelease`: exit 0.
- APK SHA-256: `5db881fe2747a83bfc0c04aab1d220c07108faed5402a9b1157399139622e471`
- AAB SHA-256: `b875f96826576d28ad95d9c5766075d09a9f062d547ae2298b9c1dd83bf8d3fd`
- `apksigner verify --verbose --print-certs`: v1/v2 PASS, one signer, certificate SHA-256 `1e70501cdd9e58d7dcfb7f39d91bde3be504029511a967fdafa3685240351387`.
- AAB `jarsigner -verify`: exit 0.
- `git diff --check`: exit 0.

## Tablet recovery and security

- Device: SM-T585, Android 8.1/API27, serial `52003c4f4b57456b`.
- Final APK installed with `adb install -r`: `Success`.
- Package `ro.mehene.app`, versionCode 5, versionName 2.1.0, UID 10157; firstInstallTime remained `2026-07-31 07:42:29`.
- Five raw `input tap 76 65` events completed in 1069 ms and opened `AdminActivity`.
- Admin UI title `Administrare Menene`; final landscape capture SHA-256 `644e81f9b44b828b07cc13fbf47c60b3eee80df349ba631efacc5d5dda9ccf00`.
- Direct shell launch remains rejected: exit 255 with `SecurityException ... AdminActivity ... not exported`.
- Close control returned to `MainActivity`.

## QA fixture

Generated test-only media was kept separate from the qualified library and pushed to the SD card:

- Active fixture: `/storage/486F-ACE7/Menene-Audit-50-500`
- Local generated size: 11 MB; device allocated size: 156 MB
- 1093 files: 500 MP4, 525 WebP, 66 SRT/VTT, plus catalog/metadata
- 50 series, 100 seasons, 500 episodes; mixed series/episode artwork and mixed subtitle formats
- Catalog SHA-256: `414c788f78124afde560c43d8a68bb410619ff708ab52c855accb2a85e7edb6e`
- Admin scan result: `50 seriale • 500 episoade`, 0 ignored videos, 25 series without cover, 0 episodes without thumbnail, scan duration `368503 ms`.
- A second folder-scan-only sibling was also copied to `/storage/486F-ACE7/Menene-Audit-50-500-FolderScan`; it has the same media counts and catalog retained as `fixture-catalog.json`, but was not selected for a separate timing result.
- Qualified `/storage/486F-ACE7/Menene-Qualified-20260731` was not modified.

## Not covered / residual risk

Manager must independently run the final 3/3 full playback, G8 render and navigation interpretation, 20 auto-next, 20 background/foreground, 10 force-stop/restart, and 2-hour soak with logcat zero-counts. The measured 368.5-second 50/500 scan is a significant performance risk and should not be treated as a release-quality PASS without review.
