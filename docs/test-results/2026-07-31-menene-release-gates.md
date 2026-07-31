# Menene release-gate evidence — 2026-07-31

## Candidate

- Worktree: `/tmp/mehene-luna-legal-release`, branch `luna/mehene-legal-release`.
- Base: `1bb20f689ed5947123779b1ff4cfe259087648b3`.
- Package and namespace remain `ro.mehene.app`; DB/storage identifiers and signing alias remain unchanged.
- Final candidate is versionName `2.1.0`, versionCode `5` (monotonic update from the installed versionCode 4).
- Kiosk and Device Owner remain disabled.

## Release artifacts

- APK: `app/build/outputs/apk/release/app-release.apk`, SHA-256 `3ed1ab4e705d4614cef1089cc4de4543964a0ee1afaf307cfdf22dd9bf6b056d`.
- AAB: `app/build/outputs/bundle/release/app-release.aab`, SHA-256 `f1597fca905f416642e3cb9ca050fc1b0ff5d74181926633453f9437be19fd5a`.
- `apksigner verify --verbose --print-certs`: v1 PASS, v2 PASS, one signer; certificate SHA-256 `1e70501cdd9e58d7dcfb7f39d91bde3be504029511a967fdafa3685240351387`.
- `aapt2 dump badging`: package `ro.mehene.app`, versionCode 5, versionName 2.1.0, label `Menene`.

## Build and device evidence

- `validate_repo.py`: PASS.
- Debug `clean test lintDebug assembleDebug`: PASS.
- Release `lintRelease assembleRelease bundleRelease` with owner-approved keyring lookup: PASS.
- `connectedDebugAndroidTest` on the final source tree, SM-T585/API27: 17/17 PASS.
- Final release installed on SM-T585 serial `52003c4f4b57456b`; package UID remained `10157`, versionCode 5.
- SAF regrant selected `content://com.android.externalstorage.documents/tree/486F-ACE7%3AMenene-Qualified-20260731`; Admin scan reported 3 seriale, 3 episoade, zero ignored files, zero missing covers and zero missing thumbnails.
- Final landscape smoke showed `MENENE`, `MENENE TV`, and cards `Calut`, `Capybara`, `Masinuta`; one offline episode reached the Media3 player with no matching FATAL/ANR/PlaybackException log entries.
- External shell launch of `AdminActivity` was rejected with `SecurityException ... not exported`, confirming the temporary probe flag was removed.

## Update and rollback safety

- Same-key update from installed versionCode 4 to candidate versionCode 5: install PASS; UID stayed `10157`; certificate fingerprint stayed unchanged; app data and SAF grant were retained.
- Lower versionCode 4 install probe: safely rejected as `INSTALL_FAILED_VERSION_DOWNGRADE`; no downgrade or data deletion was used.
- Safe rollback therefore requires a forward-fix build signed with the same key; destructive downgrade was not used.

## Content and key backup

- Qualified library: 3/3 legal episodes, catalog SHA-256 `838aa6486f5830f84e716f3bd36672edc074c2ad6c624171445e92f98d3a3263`; final media hashes are in [CONTENT_MANIFEST_2026-07-31-QUALIFIED.json](../CONTENT_MANIFEST_2026-07-31-QUALIFIED.json).
- Device folder: `/storage/486F-ACE7/Menene-Qualified-20260731/`; tablet hashes matched staging hashes.
- Primary keystore path is owner-controlled under `/home/andrei/.mehene/release-2026/`; local encrypted backup is under `/opt/Mobiup/ops/backups/mehene-release-2026/`; encrypted off-host backup is under `server:/storage/backups/mehene-release-2026/`.
- Off-host restore-test: PASS; restored keystore SHA-256 matched the primary. No secrets are recorded here.

## Open gates and residual risk

- Not run in this lane: connected final release instrumentation, a true 50/500 on-device fixture, 2-hour/20-auto-next soak, 20 background/foreground cycles, and 10 force-stop/restart cycles. These remain required before a release GO.
- Visual review is implementation smoke only; independent Minimax review on SM-T585 is still required for contrast, clipping, adaptive/round icon and adult-recovery readability.
- The nominal SSH identity path was absent on Dell during backup transfer; the existing SSH path completed the transfer, but the identity-path discrepancy must be repaired before future DR operations.
