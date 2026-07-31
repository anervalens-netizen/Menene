# Menene

Menene este un „Cartoon Network personal” complet offline pentru Samsung SM-T585, calificat pe Android 8.1/API 27, aproximativ 2 GB RAM și biblioteci pe microSD.

## Experiență

- catalog vizual cu seriale, sezoane și episoade;
- `Continuă` pentru ultimul episod început;
- `Menene TV`, cu alternarea serialelor și episoade nevăzute;
- moduri: un episod, continuă serialul sau Menene TV;
- player fullscreen cu X, pauză la atingere, volum, buffering, retry și auto-next;
- progres local: nevăzut, început, terminat;
- subtitrări `.srt`/`.vtt` și preferință audio;
- kiosk Android și pornire după restart în Device Owner;
- fără internet, reclame, conturi sau telemetrie.

## Fără securitate

Menene este utilizată de un copil supravegheat. Nu există PIN, parolă, autentificare sau criptare specială. Administrarea se deschide prin cinci atingeri pe siglă numai pentru a păstra interfața curată. Regula obligatorie este în [docs/NO_SECURITY.md](docs/NO_SECURITY.md).

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

## Biblioteca Menene recomandată

```text
Menene/
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

Pe tabletă, Builderul publică MP4/H.264/AAC la maximum 1280×720 și 30 fps. Ca sursă acceptă containerele video uzuale pe care FFmpeg le poate decoda, inclusiv MP4/M4V, MKV, WebM, AVI, MOV, MPEG/MPG, TS/MTS/M2TS, VOB, WMV, FLV, 3GP, OGV și RM/RMVB.

## Library Builder

```bash
python3 tools/mehene_builder.py "/Desene originale" "/Folder staging/Menene" --audio-language ron
```

Wrapper-ele recomandate:

```bash
./tools/convert-library.sh SOURCE DESTINATION --audio-language ron
```

```powershell
.\tools\convert-library.ps1 -Source "D:\Desene" -Destination "E:\Menene-Staging" -AudioLanguage ron
```

Builderul convertește numai ce este necesar, generează miniaturi și catalog, reutilizează fișiere valide și restaurează catalogul anterior dacă întâlnește erori.

Cu tableta conectată prin USB, adăugarea unei biblioteci noi se face într-o singură comandă:

```bash
./tools/add-to-tablet.sh "/folderul/cu/desene" Povesti
```

Comanda construiește într-un staging temporar, verifică rezultatul, copiază pe microSD într-un folder nou cu timestamp și verifică hash-ul catalogului. La final, deschide Administrare prin cinci atingeri pe siglă în cel mult opt secunde și alege folderul afișat.

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

Versiunea **2.2.0** păstrează identitatea internă `ro.mehene.app` pentru update și date, dar afișează Menene peste tot. Include recovery prin cinci atingeri, catalog mare accelerat pe microSD, fallback-uri ilustrate originale, grid adaptiv și fluxul de adăugare pe tabletă. Verdictul exact al buildului și probelor pe SM-T585 este în cel mai nou raport din `docs/test-results/`.
