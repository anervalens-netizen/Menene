# Mehene Android pre-release gates — 2026-07-31

## Identitate și scope

- Host: `dell-standby`.
- Worktree de integrare: `/tmp/mehene-manager-integration`, branch `manager/mehene-release-gates`, bază `7d86ee1e67171382445e80da66d7fc65fe968abf`.
- Device: Samsung SM-T585, Android 8.1/API 27.
- Fără cheie release, Device Owner sau kiosk; bibliotecile Pilot și Validation au fost folosite numai pentru calificare.

## Implementare integrată

- MEH-103: progres namespace-uit prin cheia `(libraryId, episodeId)`; catalogul gol nu șterge alte namespace-uri; Room v1→v2 păstrează `legacy` și îl copiază vizibil în prima bibliotecă activă.
- Markerul primei biblioteci legacy este sticky, iar copierea Room se reia idempotent la restart; testul de process-death verifică atât recuperarea, cât și protejarea progresului țintă mai nou.
- MEH-104: health probe la startup și `ResilientPlaybackProgressDao` cu fallback la construcție, query/read, write și clear; backupul atomic rămâne sursa durabilă după restart.
- `ProgressRepository.loadStateUnlocked` restaurează backupul și când Room este gol; checkpointul critic scrie backupul sincron înaintea mutexului DAO.
- `AdminActivity.restoreAfterSettings` este salvat/restaurat prin `onSaveInstanceState`.
- Schema Room v1 este împachetată în assets-ul androidTest; `MigrationTestHelper` creează v1 și validează migrarea la v2.
- Runtime-ul `kotlinx-serialization` este aliniat explicit la BOM 1.8.1, cerut de Room 2.8.4; elimină incompatibilitatea ABI cu DataStore 1.7.3 observată în prima rulare MigrationTestHelper.

## Verificări automate finale

Pe sursa finală înainte de commit:

- `ANDROID_HOME=/home/andrei/android-sdk ANDROID_SDK_ROOT=/home/andrei/android-sdk ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — PASS; `BUILD SUCCESSFUL`, 59 actionable tasks, lint fără erori.
- `ANDROID_SERIAL=192.168.0.32:5555 ANDROID_HOME=/home/andrei/android-sdk ANDROID_SDK_ROOT=/home/andrei/android-sdk ./gradlew --no-daemon :app:connectedDebugAndroidTest` — PASS; 17/17 pe SM-T585, `BUILD SUCCESSFUL`, 77 actionable tasks.
- XML: `app/build/outputs/androidTest-results/connected/debug/TEST-SM-T585 - 8.1.0-_app-.xml` — `tests=17`, `failures=0`, `errors=0`, `skipped=0`.
- Transportul USB a pierdut serverul ADB înainte de instalare în două încercări (`0 tests`); gate-ul final a rulat pe aceeași tabletă prin ADB TCP temporar, cu server non-daemon menținut în sesiunea Gradle.
- `git diff --check` și `git diff --cached --check` — PASS înainte de actualizarea acestei dovezi.

Artefacte:

- `app/build/outputs/apk/debug/app-debug.apk` — 11.950.639 bytes, SHA-256 `214abc2760af63d1cde1e6ea07e853628d686b74a53606e28704701d9da8ec3d`.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — 842.600 bytes, SHA-256 `cdfbca5161598453fedc7c735777826e72b7a7083e83f852cc0cebf999bb11a5`.
- JUnit BOM metadata: `https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.9.2/junit-bom-5.9.2.module`, SHA-256 `ab137ba5a8e32c9b066bf9126a1c76dd5614b724ba5c0b02549772b5e9f4cf1f`.

## Probe funcționale pe device

- Cold start și biblioteca SAF Pilot: PASS; 3 seriale/3 episoade.
- Pilot→Validation→Pilot: PASS; checkpointul Pilot de 14.395 ms a rămas separat și cardul `Continuă` a reapărut la revenire.
- Checkpoint + force-stop: PASS; X a scris sincron 20.335 ms, urmat imediat de `am force-stop`; restartul a afișat `Continuă`.
- Room gol: PASS; backupul a restaurat în DB nouă rândul namespaced la 20.335 ms.
- Room corupt controlat: PASS; aplicația a rămas utilizabilă prin fallback și backup; fixture-ul a fost eliminat, DB a fost recreată, `PRAGMA integrity_check=ok`, iar rândul restaurat este prezent.
- Admin recreate: PASS; Settings→kill real→Back a schimbat PID `16526→16830` și a restaurat AdminActivity cu biblioteca Pilot.
- Logcat final: zero `FATAL EXCEPTION` și zero ANR; erorile SQLite au existat numai în proba de corupere controlată.

## Riscuri și verdict

- Self-review-ul final a găsit și a remediat fereastra process-death dintre markerul legacy și copierea Room; replay-ul este acum idempotent și acoperit determinist. Nu au rămas defecte noi în probele finale.
- Release signing, upgrade/rollback, biblioteca 50/500, soak G6 și kiosk G7 rămân porți separate; Andy și Rabbit rămân `test-only`.
- Verdict pentru patchul Android MEH-103/MEH-104/P3: **GO pentru integrare Git**.
- Verdict pentru release semnat/instalare definitivă: **NO-GO** până la porțile separate de release, update/rollback, performanță, soak și kiosk.
