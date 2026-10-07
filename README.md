# Menene

Menene este un „Cartoon Network personal” complet offline pentru Samsung SM-T585, calificat pe Android 8.1/API 27, aproximativ 2 GB RAM și biblioteci pe microSD.

## Experiență

- catalog vizual cu seriale, sezoane și episoade;
- `Continuă` pentru ultimul episod început;
- `Menene TV`, cu alternarea serialelor și episoade nevăzute;
- moduri: un episod, continuă serialul sau Menene TV;
- player fullscreen cu X, pauză la atingere, bară glisabilă, salt ±10 secunde, volum, buffering, retry și auto-next;
- progres local: nevăzut, început, terminat;
- fără subtitrări, ca regulă de produs pentru copilul pre-lector; preferință audio locală;
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

## Biblioteca Menene recomandată

```text
Menene/
├── catalog.json
├── Bluey/
│   ├── cover.webp
│   ├── Season 01/
│   │   ├── Episod 01.mp4
│   │   └── Episod 01.webp
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

Builderul convertește numai ce este necesar, generează miniaturi și catalog, ignoră și elimină sidecar-urile `.srt`/`.vtt`, reutilizează fișiere valide și restaurează catalogul anterior dacă întâlnește erori.

Cu tableta conectată prin USB, adăugarea unei biblioteci noi se face într-o singură comandă:

```bash
./tools/add-to-tablet.sh "/folderul/cu/desene" Povesti
```

Comanda construiește într-un staging temporar, verifică rezultatul, copiază pe microSD într-un folder nou cu timestamp și verifică hash-ul catalogului. Dacă sunt conectate mai multe device-uri, setează mai întâi `ADB_SERIAL`.

Comanda nu schimbă automat folderul activ. După terminare, pe tabletă deschizi manual Administrare prin cinci atingeri pe siglă în cel mult opt secunde, alegi „Schimbă folderul” și selectezi folderul afișat.

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

Cerințe: JDK 17 și Android SDK Platform 37 (`sdkmanager "platforms;android-37"`). APK-ul debug apare în:

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

Versiunea **2.4.0** păstrează identitatea internă `ro.mehene.app` pentru update și date. Include Home cinematic cu hero 16:9, artwork original la rezoluție mare, player cu seek direct și salt ±10 secunde, recovery prin cinci atingeri și bibliotecă offline pregătită prin Builder. Verdictul exact al buildului, probelor pe SM-T585 și bibliotecii reale este în [raportul final Menene 2.4](docs/test-results/2026-08-01-menene-2.4-final.md).
