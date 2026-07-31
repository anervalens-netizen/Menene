# Menene reproducible rebuild fix — 2026-07-31

## Scope and source

- Worktree: `/tmp/menene-luna-rebuild-fix`, branch `luna/menene-rebuild-fix`.
- Source: Minimax QA commit `f4e82789f7aa5184029d5c495dae4497860f1dd0`, parent Menene candidate `57ad2aa737def5da3b658bfdaff581c2c2707813`.
- Product code, branding, assets, manifest, version and device data were not modified in this remediation. The final remediation commit is the commit containing this report and is reported in the Buzz handoff.

## Fix

Only these files changed:

- `gradle/verification-metadata.xml`: added the exact SHA-256 entries for `com.google.guava:guava-parent:33.3.1-jre` POM (`55441db27e8869dfefe053059bdf478bdc7e95585642bf391f0023345fd56287`) and `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0` POM (`1239e9dbe1397cd5971342956b2511bc3ace7b641842e4372a088dcfa8b9ad55`).
- `tools/validate_repo.py`: updated the narrow version assertion from `versionCode = 4` / `2.1.0 / 4` to `versionCode = 5` / `2.1.0 / 5`, matching the already-approved Menene candidate.

Both POM bytes were fetched over HTTPS from Maven Central on 2026-07-31 and hashed locally; no accept-all or verification disablement was used.

## Verification

- `env -u PYTHONHOME -u PYTHONPATH /usr/bin/python3 tools/validate_repo.py`: PASS, 47 XML files, offline manifest, policy, builder and version 2.1.0 checks.
- `./gradlew --no-daemon clean test lintDebug lintRelease assembleDebug assembleRelease bundleRelease`: PASS, BUILD SUCCESSFUL, 131 actionable tasks.
- `ANDROID_SERIAL=52003c4f4b57456b ANDROID_HOME=/home/andrei/android-sdk ANDROID_SDK_ROOT=/home/andrei/android-sdk ./gradlew --no-daemon connectedDebugAndroidTest`: PASS, 17/17 on SM-T585/API27.
- `git diff --check`: PASS.

## Rebuilt candidate artifacts

- APK SHA-256: `6ada70ad10b78a601da286ce29ba3b05b7ea06d681ceea57d3faf9019c09b210`.
- AAB SHA-256: `f1597fca905f416642e3cb9ca050fc1b0ff5d74181926633453f9437be19fd5a`.
- `apksigner verify --verbose --print-certs`: v1 PASS, v2 PASS, one signer; certificate SHA-256 `1e70501cdd9e58d7dcfb7f39d91bde3be504029511a967fdafa3685240351387`.
- `aapt2 dump badging`: `ro.mehene.app`, versionCode 5, versionName 2.1.0, label Menene.
- `jarsigner -verify -verbose -certs` on AAB: exit 0; only normal bundle-entry warnings.

## v4 downgrade QA artifact

- Built separately from base `1bb20f689ed5947123779b1ff4cfe259087648b3`, with only the same two temporary metadata entries needed for dependency verification.
- Explicit path: `/tmp/menene-v4-release-20260731.apk`.
- SHA-256: `355a69f4bc6c82e8aff996efb46806246ad5b04c904ee4c62f16d980796ad40c`.
- `aapt2 dump badging`: `ro.mehene.app`, versionCode 4, versionName 2.1.0.
- `apksigner verify`: v1 PASS, v2 PASS, same certificate fingerprint as v5.
- Not installed and not committed; Minimax owns the non-destructive downgrade-refusal probe.

## Remaining scope

This lane did not run the independent QA matrix, G8 50/500, full soak, visual review or install the v4 artifact. Minimax must rerun the complete QA against the remediation commit and use the explicit v4 artifact for downgrade refusal. Release remains NO-GO until those gates pass.
