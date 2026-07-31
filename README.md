# Mehene

Mehene este un „Cartoon Network personal” complet offline pentru Samsung SM-T585, calificat pe Android 8.1/API 27, aproximativ 2 GB RAM și biblioteci pe microSD.

## Experiență

- catalog vizual cu seriale, sezoane și episoade;
- `Continuă` pentru ultimul episod început;
- `Mehene TV`, cu alternarea serialelor și episoade nevăzute;
- moduri: un episod, continuă serialul sau Mehene TV;
- player fullscreen cu X, pauză la atingere, volum, buffering, retry și auto-next;
- progres local: nevăzut, început, terminat;
- subtitrări `.srt`/`.vtt` și preferință audio;
- kiosk Android și pornire după restart în Device Owner;
- fără internet, reclame, conturi sau telemetrie.

## Fără securitate

Mehene este utilizată de un copil supravegheat. Nu există PIN, parolă, autentificare sau criptare specială. Administrarea se deschide prin cinci atingeri pe siglă numai pentru a păstra interfața curată. Regula obligatorie este în [docs/NO_SECURITY.md](docs/NO_SECURITY.md).

## Arhitectură

- Views/XML;
- ViewModel + StateFlow;
- Storage Access Framework;
- `catalog.json` generat extern, scanare foldere ca fallback;
- cache intern atomic, verificat prin checksum, fingerprint și TTL;
- runtime StateFlow + Room v2 + backup atomic pentru progres, namespace-uit pe biblioteca;
- health probe Room la startup si DAO comutabil la erori de query/read/write;
- DataStore cu recuperare la corupere;
- Media3 ExoPlayer cu lifecycle adaptat versiunii Android;
- Library Builder Python cu lock și rezultat final fail-closed.

Detalii: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) și [docs/STABILITY.md](docs/STABILITY.md).

## Audit și roadmap

Auditul final este fixat pe commitul `f12b7b4b3af94bdca2a5ba0e98fea0a3d197156a`.

- [Audit final](docs/FINAL_AUDIT.md)
- [Roadmap detaliat](docs/ROADMAP.md)
- [Porți de validare](docs/VALIDATION_PLAN.md)

Verdict curent:

- **GO condiționat** pentru build și etapa Release Candidate;
- **NO-GO** pentru instalarea definitivă până la buildul complet, testele Samsung și închiderea constatărilor P1.

## Biblioteca recomandată

```text
Mehene/
├── catalog.json
├── Bluey/
│   ├── cover.webp
│   ├── Season 01/
│   │   ├── Episod 01.mp4
│   │   ├── Episod 01.webp
│   │   └── Episod 01.srt
│   └── Season 02/
└── Mașini de curse/
    ├── cover.jpg
    └── Episod 01.mp4
```

Format: MP4, H.264 Main, AAC stereo, maximum 1280×720 și 30 fps.

## Library Builder

```bash
python3 tools/mehene_builder.py "/Desene originale" "/Folder staging/Mehene" --audio-language ron
```

Wrapper-ele recomandate:

```bash
./tools/convert-library.sh SOURCE DESTINATION --audio-language ron
```

```powershell
.\tools\convert-library.ps1 -Source "D:\Desene" -Destination "E:\Mehene-Staging" -AudioLanguage ron
```

Builderul convertește numai ce este necesar, generează miniaturi și catalog, reutilizează fișiere valide și restaurează catalogul anterior dacă întâlnește erori. În versiunea 2.1 se recomandă construirea într-o destinație staging, nu direct într-o bibliotecă citită simultan de tabletă.

Detalii și limitări: [docs/LIBRARY_BUILDER.md](docs/LIBRARY_BUILDER.md).

## Validare

Validare offline rapidă:

```bash
python3 tools/validate_repo.py
```

Build Android complet:

```bash
python3 tools/validate_repo.py --android
```

Echivalent Gradle:

```bash
./gradlew clean test lintDebug assembleDebug
```

Cerințe: JDK 17 și Android SDK 36. APK-ul debug apare în:

```text
app/build/outputs/apk/debug/app-debug.apk
```

APK-ul release necesită cheia configurată prin `MEHENE_STORE_FILE`, `MEHENE_STORE_PASSWORD`, `MEHENE_KEY_ALIAS` și `MEHENE_KEY_PASSWORD`.

## Kiosk

```bash
adb install app/build/outputs/apk/release/app-release.apk
adb shell dpm set-device-owner ro.mehene.app/ro.mehene.app.kiosk.MeheneDeviceAdminReceiver
adb shell am start -n ro.mehene.app/ro.mehene.app.MainActivity
```

Procedura completă: [docs/DEVICE_SETUP.md](docs/DEVICE_SETUP.md).

## Stadiu

Versiunea **2.1.0** implementează funcțiile premium și hardening pentru catalog, progres, setări, player și Builder. Următoarea etapă obligatorie este R1 din roadmap: build Android reproductibil, schema Room, APK release semnat și test de update. Nu se recomandă adăugarea de funcții noi înainte de trecerea porților Release Candidate.

Pre-release Android gates: Room v2 namespacează progresul pe bibliotecă, backupul atomic este sursa de recuperare, iar DAO-ul are health probe și fallback la erori de query/read/write. Migrarea legacy rămâne nedistructivă, sticky și reluabilă idempotent după process-death în prima bibliotecă activă; connected/device gates sunt PASS pe SM-T585, iar release-ul semnat rămâne blocat până la update/rollback, performanță, soak și kiosk.
