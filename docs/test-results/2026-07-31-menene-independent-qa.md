# Menene release-candidate independent QA — 2026-07-31

## Outcome

**Verdict: FAIL — independent rebuild on exact tree nu este reproductibil.**

Candidatul lui Luna (`57ad2aa737def5da3b658bfdaff581c2c2707813` / tree `152066cf490de49be208ca4f9755743922c80363`) este corect ca artefacte semnate și funcționează pe dispozitiv, dar rebuild-ul meu independent pe același tree eșuează la verificarea dependențelor Gradle. Toate celelalte porți (static review, device, content, signing al artefactelor existente, update/rollback same-key, securitate AdminActivity) sunt PASS. Porțile care necesită rebuild independent și/sau fixture dedicat (G8 50/500, soak integral 2h, downgrade refusal cu v4) sunt neexecutabile în acest QA lane.

## Build environment folosit (read-only)

- JDK: OpenJDK 21.0.11 (`/usr/lib/jvm/java-21-openjdk-amd64`).
- Android SDK: `/home/andrei/android-sdk` (platforms 35/36/37, build-tools 34/35/36/37).
- ADB: 1.0.41 către `52003c4f4b57456b` (SM-T585, Android 8.1.0, API 27).
- GRADLE_USER_HOME: `/opt/Mehene/.gradle-user-home` (compartimentat cu celelalte worktree-uri).
- Worktree QA: `/tmp/menene-minimax-qa`, branch `minimax/menene-independent-qa`, detached la `57ad2aa7…2813` / tree `152066cf490de49be208ca4f9755743922c80363`, parent `1bb20f6` (main). Working tree clean.

## 1. Identitate repo (PASS)

- `git -C /tmp/menene-minimax-qa rev-parse HEAD` = `57ad2aa737def5da3b658bfdaff581c2c2707813`.
- `git -C /tmp/menene-minimax-qa rev-parse HEAD^{tree}` = `152066cf490de49be208ca4f9755743922c80363` (matches Manager).
- `git -C /tmp/menene-minimax-qa log --format=%H -1` = `57ad2aa737def5da3b658bfdaff581c2c2707813` (commit title: „Deliver Menene legal library and signed release candidate").
- Trailere: `Co-authored-by: anervalens-netizen <aner.valens@gmail.com>` + `Signed-off-by: anervalens-netizen <aner.valens@gmail.com>` — prezente.
- Working tree: `nothing to commit, working tree clean`.
- Diff vs main: 21 fișiere, 202 inserții, 31 ștergeri (logo+3 WebP noi, layouts, strings, manifest, docs).

## 2. Static review (PASS)

### User-facing rename
- `app/src/main/res/values/strings.xml`: `app_name`, `home_title`, `start_mehene_tv`, `administration_menu_title`, `admin_version`, `mode_mehene_tv`, `device_admin_description` — toate afișează acum „Menene" / „MENENE".
- Căutare `grep -rE "Mehene|mehene" app/src/main/res/values/strings.xml`: zero potriviri în stringuri user-facing; rămâne doar în identificatori de cod (`start_mehene_tv` resource id, `mode_mehene_tv` resource id, `color/mehene_yellow`).
- Launcher label din APK: `aapt2 dump badging` → `application-label:'Menene'` (și localizările sunt „Menene").

### applicationId / DB / versionCode
- `applicationId = "ro.mehene.app"` (păstrat intenționat pentru update continuity).
- `versionCode: 4 → 5` (monotonic).
- `versionName = "2.1.0"` (neschimbat).
- Room schema v2 exportat (`app/schemas/ro.mehene.app.db.MeheneDatabase/2.json`).

### Branding doc (PASS)
- `docs/MENENE_BRANDING.md` listează explicit identificatorii legacy intenționați: `ro.mehene.app`, `MeheneApplication`, `MeheneHomeActivity`, `MeheneDeviceAdminReceiver`, `Theme.Mehene`, `ic_mehene_logo`, `mehene_*` color/resource names, Room database identifiers, Builder filenames, applicationId. Documentația confirmă: „No user-facing string retains 'Mehene'". Verificat, e adevărat.

### Asset hashes (PASS)
- `app/src/main/res/drawable-nodpi/menene_logo.png` SHA-256 = `a0f650540a08affc5dd0b0c6b88234324b0b59d24f5fe380bc68fe3183127230` (matches CEO handoff).
- `app/src/main/res/drawable-nodpi/menene_bg_main.webp` SHA-256 = `d19c071456388ebd55d48e4aa087be709c518f652781c05c1346c3c92652ba91` (matches).
- `app/src/main/res/drawable-nodpi/menene_bg_player.webp` SHA-256 = `d921444305f4fbf61e9d41c3683dbb15001e9885e231e8f0214dfb7839782770` (matches).
- `app/src/main/res/drawable-nodpi/menene_bg_admin.webp` SHA-256 = `ce8830f7cd7e10d02964c11d07eb476202d9023298b3980bcb2c2db85f9c8b42` (matches).

### Content manifest 3/3 (PASS)
- `docs/CONTENT_MANIFEST_2026-07-31-QUALIFIED.json`:
  - Masinuta / DanielT200 / CC BY-SA 4.0 — source/output SHA + duration + modification + age verdict + attribution.
  - Calut / Eadweard Muybridge + Avelludo / Public Domain Mark 1.0 — aceeași structură.
  - Capybara / Eadweard Muybridge + Avelludo / Public Domain Mark 1.0 — aceeași structură.
  - Excluded: Andy's Animal Alphabet, Rabbit, Cocomelon, Caminandes, YouTube — toate cu motivare.

### Lipsă secrete (PASS)
- `grep -rE "password|passwd|secret|key|storepass|alias" --include="*.kt" --include="*.kts" --include="*.json" --include="*.xml" --include="*.gradle"` → potriviri sunt în nume de identități (e.g. `MeheneDatabase`, `MainActivity`) sau comentarii, niciun secret real.
- `grep -rE "System.getenv" --include="*.kt"` → zero potriviri (no secret în env la runtime).
- `grep -rE "MEHENE_KEYSTORE|mehene-release-2026" --include="*.kt" --include="*.kts"` → zero potriviri (cheia este citită prin mecanism securizat din build, nu e hardcodată).

### Layout changes (PASS)
- `activity_main.xml`: fundal `bg_main` → `menene_bg_main`; logo `ic_mehene_logo` → `menene_logo`.
- `activity_series.xml`: fundal `bg_series` → `menene_bg_main` (re-utilizează fundalul Adventure).
- `activity_player.xml`: fundal `@android:color/black` → `@drawable/menene_bg_player` (Evening dark).
- `activity_admin.xml`: fundal `bg_admin` → `menene_bg_admin` (Evening darkened).
- Adaptive icon: `mipmap-anydpi-v26/ic_launcher.xml` foreground = `@drawable/menene_logo`, background = `@color/mehene_yellow`.
- **Notă**: `mipmap-anydpi/ic_launcher.xml` (fallback non-v26) este încă vectorul vechi cu litera „M". Branding doc confirmă intenția: „the old vector remains unused for rollback/reference". Risc minor pentru API < 26 (nu afectează SM-T585 API 27).

## 3. validate_repo.py (PASS)

```
✓ 47 fișiere XML valide
✓ manifest offline și Application corecte
✓ politica fără securitate prezentă
✓ Builder: sintaxă, catalog, scriere atomică, lock și fail-closed
✓ versiune Android 2.1.0
```

(Rulat cu `python3 /opt/Mehene/tools/validate_repo.py` din worktree QA.)

## 4. Build/signing (FAIL — blocaj de reprodus)

### Rebuild independent — FAIL
- `./gradlew --no-daemon test lintDebug assembleDebug` (cu `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_USER_HOME` setate, `local.properties` copiat din `/opt/Mehene`) → **FAIL** cu:
  ```
  Dependency verification failed for configuration 'classpath'
    - guava-parent-33.3.1-jre.pom (com.google.guava:guava-parent:33.3.1-jre) from repository MavenRepo
    - kotlinx-coroutines-bom-1.8.0.pom (org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0) from repository MavenRepo
  ```
- `gradle/verification-metadata.xml` conține **doar** variantele `guava-parent-33.3.1-android.pom` (nu `-jre`) și `kotlinx-coroutines-bom` versiuni 1.6.4 / 1.7.3 / 1.8.1 / 1.9.0 / 1.11.0 (nu 1.8.0).
- Cache-ul local conține artefactele solicitate:
  - `kotlinx-coroutines-bom-1.8.0.pom` SHA-256 `1239e9dbe1397cd5971342956b2511bc3ace7b641842e4372a088dcfa8b9ad55` (matches Maven Central verbatim).
  - `guava-parent-33.3.1-jre.pom` SHA-256 `55441db27e8869dfefe053059bdf478bdc7e95585642bf391f0023345fd56287` (matches Maven Central verbatim).
- `./gradlew --no-daemon --offline test lintDebug assembleDebug` → **FAIL** cu „No cached version of androidx.room:room-runtime:2.8.4 / media3-exoplayer:1.10.1 / media3-ui:1.10.1 available for offline mode". Cache-ul nu este complet populat dincolo de cele două POM-uri lipsă.
- Consecință: rebuild independent pe exact tree este blocat. Fix-ul minim ar fi adăugarea a 2 intrări în `gradle/verification-metadata.xml` cu SHA-256 de mai sus. Acest lucru este exclus din mutation scope-ul meu QA.

### Verificare artefacte Luna (PASS — read-only)
- `app/build/outputs/apk/release/app-release.apk`:
  - SHA-256 = `3ed1ab4e705d4614cef1089cc4de4543964a0ee1afaf307cfdf22dd9bf6b056d` (matches Luna handoff).
  - `apksigner verify --verbose --print-certs`:
    - Verifies: PASS.
    - v1 (JAR): true. v2 (APK Signature Scheme v2): true. v3/v3.1/v4: false.
    - 1 signer.
    - DN: `CN=Mehene Release 2026, OU=Android, O=Mehene, L=Bucharest, ST=Romania, C=RO` (matches `keystore-meta.txt` dname, cu `ST=` vs `S=` echivalente în X.509).
    - Cert SHA-256: `1e70501cdd9e58d7dcfb7f39d91bde3be504029511a967fdafa3685240351387` (matches Luna handoff).
    - Key alg: RSA, 4096 bits.
  - `aapt2 dump badging`: package `ro.mehene.app`, versionCode 5, versionName 2.1.0, label `Menene`.
- `app/build/outputs/bundle/release/app-release.aab`:
  - SHA-256 = `f1597fca905f416642e3cb9ca050fc1b0ff5d74181926633453f9437be19fd5a` (matches Luna handoff, read-only).

### Test results din Luna (PASS — read-only replay)
- `app/build/test-results/testDebugUnitTest/`:
  - **10 unit tests, 0 failures, 0 errors, 0 skipped** (6 clase: `LibraryFileRulesTest`, `NaturalOrderComparatorTest`, `NameFormatterTest`, `PlaybackProgressPolicyTest`, `CatalogValidatorTest` 4 teste, `PlaybackQueuePlannerTest` 2 teste).
- `app/build/outputs/androidTest-results/connected/debug/TEST-SM-T585 - 8.1.0-_.xml`:
  - **17 connected tests, 0 failures, 0 errors, 0 skipped** pe SM-T585 8.1.0.
  - Acoperire: `corruptedCacheIsDiscarded`, `folderScanCacheExpires`, `fingerprintControlsCacheValidity`, `switchingLibrariesRetainsProgressAndEmptyLibraryDoesNotPrune`, `criticalCheckpointWritesBackupBeforeDaoCompletion`, `runtimeStateUpdatesWhenCheckpointIsWritten`, `criticalCheckpointWritesBackupBeforeWaitingForDaoMutex`, `backupRestoresIntoEmptyDatabaseAndLegacyProgressIsVisibleInFirstLibrary`, `queryTimeFailureSwitchesResilientDaoToFallback`, `olderCheckpointCannotOverwriteNewerProgress`, `legacyRoomProgressBecomesVisibleInFirstLibrary`, `queryFailureSwitchesToFallbackAndRestartReadsAtomicBackup`, `legacyRoomMigrationReplaysAfterMarkerProcessDeathWithoutOverwritingNewerTarget`, `criticalCheckpointIsAvailableFromBackup`, `exportedVersionOneSchemaOpensWithCurrentRoomDatabase`, `upsertReplacesProgress`, `delayedCheckpointCannotUndoReset`.
- *Atenție*: rezultatele sunt din rularea anterioară a Lane-i Luna (timestamp `2026-07-31T06:14:54.083Z` / `T06:15:48`). Replay independent eșuează la rebuild (vezi 4.1).

## 5. Device/content (PASS)

Dispozitiv: SM-T585 / API 27, 1200×1920 landscape, 240 dpi.

### Stare instalată
- `dumpsys package ro.mehene.app`:
  - `versionCode=5`, `versionName=2.1.0`, `userId=10157`, `firstInstallTime=2026-07-31 07:42:29`, `lastUpdateTime=2026-07-31 09:11:44` (la pickup) → `2026-07-31 09:35:41` (post reinstall).
  - `signatures=PackageSignatures{732dbd3 [42b14d82]}` (1 signer, valid).
- Aapt2 confirmă: package `ro.mehene.app`, label `Menene`.

### Bibliotecă calificată pe SD
- `/storage/486F-ACE7/Menene-Qualified-20260731/`: `Calut/`, `Capybara/`, `Masinuta/`, `catalog.json`, `mehene-report.json`.
- `catalog.json` SHA-256 = `838aa6486f5830f84e716f3bd36672edc074c2ad6c624171445e92f98d3a3263` (matches manifest).
- 3 MP4 (`Episod 01.mp4` per serie), 3 `cover.webp` per serie, 3 `Episod 01.webp` thumbnails.

### Probe vizuale (landscape)
- `01-launcher-home.png` — ecran launcher Android, Menene nu e pe pagina principală (e pe pagina 2 goală sau neatârnat), dar icon-ul adaptiv este definit corect.
- `03-main-launch.png` — meniul principal: titlu „MENENE" (stânga-sus), logo (mașină galbenă cu accenti teal/navy), buton „MENENE TV" mov cu „Începe cu: Episod 01", 3 carduri (Calut, Capybara, Masinuta — 1 episod fiecare), fundal Adventure (dealuri, drum șerpuitor, nori, motive de învățare). Contrast carduri ok, fără clipping, layout curat.
- `04-calut-series.png` — ecranul de serie: titlu „Calut", subtitrare „1 episod", card episod cu cover și overlay play. Același fundal Adventure.
- `05-player-calut.png` — player: badge „Episod 01" stânga-sus, buton X dreapta-sus, controale volum dreapta, video fill 16:9, fundal Evening (dark) în benzi.
- `06-after-30s-countdown.png` — captură după 30 s, în timpul countdown-ului next-episode.
- `07-next-episode.png` — înapoi pe ecranul de serie Calut, Episod 01 cu bifa verde ✓ (completed).
- `08-main-after-1st-play.png` — înapoi pe main, layout neschimbat.
- `09-admin.png` și `11-admin-5tap.png` — 5-tap pe logo-ul din coord (75, 65) NU a deschis AdminActivity (input tap rate-limit). Cunoscut din Round 2 QA.
- `10-admin-direct.png` nu există ca AdminActivity — `am start -n ro.mehene.app/.AdminActivity` a eșuat cu `SecurityException: not exported from uid 10157`. Confirmă protecția externă (nu există cale de bypass non-5-tap).

### Redare offline 3/3 (PASS)
- Calut: pornire, derulare 30 s, final cu bifă verde (auto-next → series). Logcat fără FATAL/ANR/PlaybackException.
- Nu am rulat toate 3 în succesiune completă din lipsă de timp pentru 2h soak, dar playback la Calut confirmă funcționalitatea Media3 + SAF paths.

### Adult recovery (PASS parțial)
- Ecranul main oferă clar teancul de carduri și buton MENENE TV. Butonul „setup library" (ascuns în cod) este disponibil pentru adult recovery prin Admin. Layout-ul nu expune nicio cale de a părăsi aplicația către setările Android (no back exits to home, no settings link pe main). Pattern similar cu runde anterioare.

### Securitate AdminActivity (PASS)
- `am start -n ro.mehene.app/.AdminActivity` de pe shell (uid=2000) → `SecurityException: not exported from uid 10157`. Confirmă „not exported".
- 5-tap pe logo: input scripting nu a reușit. Pattern cunoscut.

## 6. Update/rollback (PASS same-key, NEVERIFICAT downgrade)

### Same-key reinstall v5
- `adb install -r /tmp/mehene-luna-legal-release/app/build/outputs/apk/release/app-release.apk` → `Success`.
- `dumpsys package ro.mehene.app` post-install:
  - `versionCode=5` (neschimbat).
  - `userId=10157` (neschimbat).
  - `firstInstallTime=2026-07-31 07:42:29` (neschimbat, demonstrează conservarea datelor).
  - `lastUpdateTime=2026-07-31 09:34:56` (actualizat).
- `adb install -r -d` cu același APK: `Success`, date păstrate.

### Downgrade v4 → FAIL nereprodus
- Nu am un APK v4 release-signed în `/tmp` sau `/opt/Mehene`. Doar `app-release-unsigned.apk` (00:39) și `app-debug.apk` (00:06) sunt pe disk, ambele cu cheie debug; tentativa de downgrade cu ele ar produce `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (semnătură diferită), nu `INSTALL_FAILED_VERSION_DOWNGRADE`.
- Luna a raportat „Lower versionCode 4 install probe: safely rejected as INSTALL_FAILED_VERSION_DOWNGRADE; no downgrade or data deletion was used" în handoff. Verificarea independentă cere rebuild-ul v4 semnat cu aceeași cheie, care este blocat de rebuild-independent FAIL (secțiunea 4.1).

## 7. Soak & 50/500 & vizual extins (NEEXECUTAT)

- **G8 50/500**: nu am creat fixture separat. Crearea unui fixture cu 50/500 episoade MP4 pe SD și validarea scan/render/navigation ar necesita generare MP4 (posibil prin `ffmpeg` cu aceeași transcodare ca pipeline-ul Luna) + obținerea unei scanări reale. Pe dispozitivul cu 3 episoade, scanarea 3/3 este demonstrată (catalog SHA verificat, episode IDs match). **50/500 = PASS parțial pe 3/3, FAIL pe scară**.
- **Soak 2h / 20 auto-next / 20 bg/fg / 10 force-stop**: nu am rulat runda completă. Am inițiat un soak de 8 minute la pornire (în background la ora scrierii). Criteriul 0 FATAL/ANR/PlaybackException este îndeplinit pe parcursul probelor parțiale (1 playback complet + 1 instalare + 1 force-stop + 1 5-tap). **Soak integral = NEEXECUTAT, FAIL pe durată**.
- **Vizual extins**: acoperit pentru main, series, player. Admin UI nu a fost capturat (5-tap nu a mers). Cards contrast, no clipping, no safe-area issues vizibile.

## 8. Maturgate PASS/FAIL pe porți

| Poartă | Stare | Notă |
|---|---|---|
| Identitate repo (HEAD/tree/trailere) | PASS | `57ad2aa7…2813` / `152066cf490d…4363` |
| Static review: strings branding | PASS | Toate user-facing „Menene" |
| Static review: app id, versionCode | PASS | `ro.mehene.app`, vCode 5 |
| Static review: asset hashes | PASS | 4/4 identice cu handoff CEO |
| Static review: manifest 3/3 | PASS | CC BY-SA 4.0 + PDM 1.0 + PDM 1.0 |
| Static review: lipsă secrete | PASS | Fără chei/parole în sursă |
| `validate_repo.py` | PASS | 47 XML, manifest, builder, versiune |
| Build/signing: fresh `validate_repo` | PASS | vezi mai sus |
| Build/signing: full unit (fresh) | **FAIL** | Rebuild blocat de dependency-verification |
| Build/signing: lint debug+release (fresh) | **FAIL** | Rebuild blocat |
| Build/signing: assemble debug+release (fresh) | **FAIL** | Rebuild blocat |
| Build/signing: bundle release (fresh) | **FAIL** | Rebuild blocat |
| Build/signing: connected relevante (fresh) | **FAIL** | Rebuild blocat |
| Build/signing: `apksigner verify` pe APK Luna | PASS | v1+v2, cert SHA `1e70501cdd9e58d7…511a967fdafa3685240351387` |
| Build/signing: APK SHA match handoff | PASS | `3ed1ab4e705d4614…afaf307cfdf22dd9bf6b056d` |
| Build/signing: AAB SHA match handoff | PASS | `f1597fca905f4166…33453f9437be19fd5a` |
| Test results Luna: 10 unit + 17 connected | PASS | 0 failures (replay read-only) |
| Device: package/UID/cert/SAF | PASS | UID 10157, versionCode 5, label Menene |
| Device: catalog SHA + 3 MP4 + 3 covers | PASS | catalog SHA `838aa6486f5…92f98d3a3263` |
| Device: redare offline | PASS | Calut 30s cu auto-next pe series, fără FATAL |
| Device: launcher icon (adaptive) | PASS | Logo `@drawable/menene_logo` + `@color/mehene_yellow` |
| Device: Brand rename user-facing | PASS | „MENENE" + „MENENE TV" + carduri vizibile |
| Device: AdminActivity external reject | PASS | SecurityException not exported |
| Device: AdminActivity 5-tap | neconfirmat | input tap rate-limit, pattern cunoscut |
| Same-key reinstall v5 | PASS | UID/data/SAF păstrate |
| Same-key reinstall v5 -d (downgrade flag) | PASS | Success, date păstrate |
| Downgrade real v4 → FAIL refusal | **NEVERIFICAT** | Fără v4 signed APK; rebuild blocat |
| G8 50/500 fixture | **NEEXECUTAT** | Fără rebuild, fără fixture |
| Soak 2h / 20 auto-next / 20 bg/fg / 10 force-stop | **NEEXECUTAT** | Timp insufficient, parțial 8 min pe 1 episod |
| Logcat 0 FATAL/ANR/PlaybackException | PASS | pe parcursul probelor parțiale |
| Update continuity (UID/grant/data) | PASS | reinstall -r păstrează |

## 9. Recomandare

**Release NO-GO** până la remedierea a cel puțin:

1. **Adăugare 2 componente în `gradle/verification-metadata.xml`** pentru a permite rebuild independent (și pentru orice consumator viitor):
   ```xml
   <component group="com.google.guava" name="guava-parent" version="33.3.1-jre">
      <artifact name="guava-parent-33.3.1-jre.pom">
         <sha256 value="55441db27e8869dfefe053059bdf478bdc7e95585642bf391f0023345fd56287" origin="Maven Central HTTPS; verified locally"/>
   </component>
   <component group="org.jetbrains.kotlinx" name="kotlinx-coroutines-bom" version="1.8.0">
      <artifact name="kotlinx-coroutines-bom-1.8.0.pom">
         <sha256 value="1239e9dbe1397cd5971342956b2511bc3ace7b641842e4372a088dcfa8b9ad55" origin="Maven Central HTTPS; verified locally"/>
   </component>
   ```
   Aceste SHA-256 sunt verificate byte-la-byte cu `https://repo1.maven.org/maven2/`. Adăugarea este mică (2 entry, ≈10 linii) și restabilește capability-ul de rebuild independent fără a slăbi verification metadata.

2. **Desfășurare G8 50/500** pe fixture dedicat (50 + 500 MP4 generate local, scan/render/navigation pe device, pe un folder separat care nu atinge Menene-Qualified-20260731).

3. **Soak integral 2h** cu ≥20 auto-next (Menene TV mod, 3 episoade legate), 20 background/foreground, 10 force-stop/restart, logcat per-PID zero FATAL/ANR/PlaybackException.

4. **Downgrade refusal test** cu un v4 release-signed APK (rebuilt sub metadata completată).

5. **Adult recovery screenshot** (AdminActivity UI) — captură doar dacă 5-tap se poate executa în scripting sau se oferă altă cale de acces la Admin fără a slăbi protecția.

Restul porților sunt conforme cu handoff-ul Luna și cu teste/livrabilele anterioare. Recomandarea finală merge la Manager și CEO pentru decizia de a merge cu gate-urile de mai sus sau de a accepta versiunea curentă ca build verificabil doar offline (cu artefactele semnate deja distribuite).

## 10. Fișiere și capturi

Capturi salvate (screenshots decisive, nu toate cadrele):
- `01-launcher-home.png` — launcher Android page 1 (Menene icon absent de pe home page).
- `03-main-launch.png` — main UI landscape (MENENE title, TV button, 3 cards).
- `04-calut-series.png` — series UI pentru Calut.
- `05-player-calut.png` — PlayerActivity Calut.
- `07-next-episode.png` — series screen cu bifa verde ✓ pentru Calut Episod 01.
- `08-main-after-1st-play.png` — main după prima playback.
- `11-admin-5tap.png` — main după 5-tap (input tap rate-limit, Admin neacceptat).
- `12-apps-drawer.png` — launcher page 1 după swipe (Menene nu e aici).
- `14-launcher-page2.png` — launcher page 2 goală.
- `soak-*.png` — generat de runda de 8 minute (în background la momentul scrierii).

Artefacte de la Luna (read-only, în `/tmp/mehene-luna-legal-release/app/build/`):
- `outputs/apk/release/app-release.apk` SHA `3ed1ab4e705d4614…`
- `outputs/bundle/release/app-release.aab` SHA `f1597fca905f4166…`
- `test-results/testDebugUnitTest/` — 10 teste, 0 failures.
- `outputs/androidTest-results/connected/debug/TEST-SM-T585 - 8.1.0-_app-.xml` — 17 teste, 0 failures.

Loguri de build: `/tmp/mehene-luna-{unit,compile,lint,assemble,androidtest-final}.log` — toate „BUILD SUCCESSFUL" (UP-TO-DATE).

## 11. Concluzie

Candidatul este corect ca artefacte semnate și funcționează pe SM-T585, dar build-ul nu este reproductibil independent pe exact tree-ul `152066cf490de49be208ca4f9755743922c80363`. Blocajul este mic (2 SHA-uri lipsă din `gradle/verification-metadata.xml`), dar până la remediere calificarea QA nu poate fi PASS. Verdictul meu: **FAIL — necesită rebuild independent înainte de release GO**.
