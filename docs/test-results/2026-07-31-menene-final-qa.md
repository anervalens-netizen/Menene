# Menene final QA — 2026-07-31

## Verdict

**FAIL / NO-GO.** The exact-tree rebuild, signing, connected tests, downgrade refusal, and 3/3 offline playback passed. Release cannot be accepted because adult recovery was not reachable through the documented five-tap path, so the separate 50/500 fixture could not be selected, and the required 2-hour / 20-auto-next soak could not be established. No product fix was made in this QA lane.

## Identity and scope

- QA base: commit `690db6d7f8d208e4147506a9000decc294d2b97e`, tree `a7b83f4ecb8efb904c98f2ac1ac3a5a44ee5eb44`, parent `f4e82789f7aa5184029d5c495dae4497860f1dd0`.
- Worktree: `/tmp/menene-luna-rebuild-fix`, branch `luna/menene-rebuild-fix`.
- Only this evidence report and required device captures were added; app/source, qualified library, database, SAF grants, and signing material were not modified.
- Device: SM-T585, serial `52003c4f4b57456b`, Android API 27, landscape 1920x1200.

## Fresh rebuild and signing

All commands below ran on the exact QA base and returned exit 0 unless stated otherwise:

```text
env -u PYTHONHOME -u PYTHONPATH /usr/bin/python3 tools/validate_repo.py
./gradlew --no-daemon clean test lintDebug lintRelease assembleDebug assembleRelease bundleRelease
ANDROID_SERIAL=52003c4f4b57456b ./gradlew --no-daemon connectedDebugAndroidTest
git diff --check
```

Results: validator PASS (47 XML files, manifest/policy/builder/version); Gradle PASS, 131 actionable tasks; connected PASS, 17/17 on SM-T585/API27; diff check PASS.

Fresh artifacts:

- APK: `app/build/outputs/apk/release/app-release.apk`, SHA-256 `a2166295702d8331b86684f6ad16c9bd69f906c3baa4f1834b2647a44f780946`.
- AAB: `app/build/outputs/bundle/release/app-release.aab`, SHA-256 `f1597fca905f416642e3cb9ca050fc1b0ff5d74181926633453f9437be19fd5a`.
- APK `apksigner verify --verbose --print-certs`: v1 PASS, v2 PASS, one signer; certificate SHA-256 `1e70501cdd9e58d7dcfb7f39d91bde3be504029511a967fdafa3685240351387`; DN `CN=Mehene Release 2026, OU=Android, O=Mehene, L=Bucharest, ST=Romania, C=RO`; RSA 4096.
- AAB `jarsigner -verify`: exit 0.
- `aapt2 dump badging`: package `ro.mehene.app`, versionCode 5, versionName 2.1.0, label `Menene`.

The fresh APK SHA differs from the earlier candidate because the payload/ZIP central directory are byte-identical and the first byte difference is in the APK signing block. The AAB is byte-identical; both APKs verify with the same certificate and v1/v2 schemes. This is signing-block nondeterminism, not an application payload change.

## Device, update, and rollback evidence

- Fresh same-key `adb install -r app/build/outputs/apk/release/app-release.apk`: exit 0.
- After install: package `ro.mehene.app`, versionCode 5, UID 10157, firstInstallTime `2026-07-31 07:42:29`, lastUpdateTime `2026-07-31 10:56:25`; MainActivity displayed the three qualified cards.
- v4 artifact: `/tmp/menene-v4-release-20260731.apk`, SHA-256 `355a69f4bc6c82e8aff996efb46806246ad5b04c904ee4c62f16d980796ad40c`, same package/certificate, versionCode 4.
- `adb install -r /tmp/menene-v4-release-20260731.apk` without `-d`: exit 1, expected `INSTALL_FAILED_VERSION_DOWNGRADE`.
- Post-refusal: versionCode 5, UID 10157, firstInstallTime, and visible library remained unchanged. No uninstall, clear-data, or forced downgrade was used.
- Direct shell access to the external-storage provider was denied as expected without an app SAF grant; the qualified library remained visible in the app, but the persisted SAF URI could not be independently printed from shell.

## Offline playback and visual evidence

The qualified manifest specifies three 30-second MP4/H.264/AAC episodes. Each was opened from the visible library and allowed to run beyond its declared duration:

- Calut: 35 seconds; returned to SeriesActivity with the completed badge; no FATAL/ANR/PlaybackException.
- Capybara: 45 seconds; returned to SeriesActivity with the completed badge; no FATAL/ANR/PlaybackException.
- Masinuta: 35 seconds; returned to SeriesActivity; no FATAL/ANR/PlaybackException.

Main, series, and player landscape captures are committed under `docs/test-results/2026-07-31-menene-final-qa/`. Capture SHA-256 values:

```text
main-landscape.png       633ec08afbdd6588bcbecdc05f18c3d3995f8e8269d73862c520501a4cb22a17
calut-series.png          577225f9a7fec1446307ce3c08a81d9d089b8f4270896870321c30026544275c
calut-player-start.png    d022087346e74740b2e9b1f8fc105d31a8b151a55d72ea38dc582e1723787caf
calut-player-end.png      577225f9a7fec1446307ce3c08a81d9d089b8f4270896870321c30026544275c
capybara-series.png       b013a6086ea80bb2ce72896e4f47a4231d52438e46a1887522682df0faf3bdd3
capybara-player-start.png 550005850b907b786a0ce0c07e2eb1f341b3b45c3c25accb43bd38ecc48065ef
capybara-player-end.png   2c7bb9000216eca3df4f84f86c878f5b2b4b398f7eb7f08c2e86155fc7fea30e
masinuta-series.png       f7966b0c607313cf31bd060a21f2586a9cbc4d4d2357c2a4acb9cc58a4c9f556
masinuta-player-start.png 7329f00f360c57ee264f35a7f304ec49faf5199482731361a03b9b5eb77f8bc2
masinuta-player-end.png   cfda21003c0a2908f12ccd5641663acb7b858c11e46d30513eca15750e26b594
```

The current qualified catalog has one episode per series. MENENE TV therefore returned to MainActivity after the single episode rather than producing a next-episode transition; measured auto-next count is 0. This prevents claiming the 20-auto-next gate.

## Admin recovery blocker and unexecuted scale gate

Source behavior is unchanged: `MainActivity.kt:62` wires the logo, and `:190-205` requires five taps within 3000 ms. UIAutomator reported the clickable logo bounds `[33,22][120,109]`. Repeated controlled probes using both `input tap` and `input touchscreen tap`, five taps within the 3-second window, left the app in MainActivity. No AdminActivity screenshot or folder picker was reachable.

The direct shell check correctly returned exit 255 with `SecurityException ... AdminActivity ... not exported`, so exported protection was not bypassed. `run-as` was not usable because the release package is not debuggable. No app code or manifest change was made.

Because the folder picker is only reachable from AdminActivity, G8 could not be executed without violating scope. No `Menene-Audit-50` fixture was created or mixed with `Menene-Qualified-20260731`; 50/500 scan/render/navigation, memory, and error metrics are **UNEXECUTED**.

## Soak result

- Started `2026-07-31T11:08:15+03:00`; stopped `2026-07-31T11:12:45+03:00` after the confirmed Admin/auto-next blockers.
- Duration: approximately 4m30s; background/foreground cycles: 1; force-stop/restart cycles: 0; auto-next: 0.
- Logcat after the partial run: 0 matches for `FATAL EXCEPTION|ANR in|PlaybackException`.
- Required 2-hour duration, 20 auto-next, 20 background/foreground, and 10 force-stop/restart gates: **UNEXECUTED / FAIL**. Adult recovery preservation is also unverified because AdminActivity was unreachable.

## Residual risks and next action

Release remains NO-GO. A separate authorized lane must first make the documented adult recovery path reachable on the release build without weakening `exported=false`; then select an isolated 50/500 fixture, run the full soak with a real multi-episode queue, and repeat visual/Admin/SAF verification. This QA lane intentionally made no remediation.
