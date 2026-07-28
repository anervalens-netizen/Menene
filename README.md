# Mehene

Mehene este un „Cartoon Network personal” complet offline pentru Samsung Galaxy Tab A 8.0 (2019), optimizat pentru Android 9, aproximativ 2 GB RAM și biblioteci pe microSD.

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
- Room + backup atomic pentru progres;
- DataStore cu recuperare la corupere;
- Media3 ExoPlayer cu lifecycle adaptat versiunii Android;
- Library Builder Python cu lock și publicare fail-closed.

Detalii: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) și [docs/STABILITY.md](docs/STABILITY.md).

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
python3 tools/mehene_builder.py "/Desene originale" "/CardSD/Mehene" --audio-language ron
```

Wrapper-ele recomandate:

```bash
./tools/convert-library.sh SOURCE DESTINATION --audio-language ron
```

```powershell
.\tools\convert-library.ps1 -Source "D:\Desene" -Destination "E:\Mehene" -AudioLanguage ron
```

Builderul convertește numai ce este necesar, generează miniaturi și catalog, reutilizează fișiere valide și păstrează catalogul anterior dacă întâlnește erori. Publicarea parțială necesită explicit `--publish-partial` sau `-PublishPartial`.

Detalii: [docs/LIBRARY_BUILDER.md](docs/LIBRARY_BUILDER.md).

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

Versiunea **2.1.0** implementează hardening pentru cache, progres, setări, player și Library Builder. Nu este declarată finală până la trecerea testelor pe tableta fizică, microSD, reboot și bibliotecă mare din [docs/VALIDATION_PLAN.md](docs/VALIDATION_PLAN.md).
