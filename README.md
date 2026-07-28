# Mehene

Mehene este un „Cartoon Network personal” complet offline pentru Samsung Galaxy Tab A 8.0 (2019). Aplicația este Android nativ, optimizată pentru Android 9, aproximativ 2 GB RAM și biblioteci stocate pe microSD.

## Experiență

- catalog vizual cu seriale, sezoane și episoade;
- card `Continuă` pentru episodul început;
- `Mehene TV`, care alternează serialele și alege episoadele nevăzute;
- moduri de redare: un episod, continuă serialul sau Mehene TV;
- player fullscreen cu X, pauză la atingere, volum, buffering, retry și auto-next controlat;
- progres local: nevăzut, început, terminat;
- subtitrări `.srt`/`.vtt` cu același nume ca episodul;
- kiosk Android și pornire după restart când aplicația este Device Owner;
- zero acces la internet, reclame, conturi sau telemetrie.

## Fără securitate

Mehene este utilizată de un copil supravegheat. Nu există PIN, parolă, autentificare sau criptare specială. Administrarea se deschide prin cinci atingeri pe siglă doar pentru a păstra interfața copilului curată. Regula este documentată în [docs/NO_SECURITY.md](docs/NO_SECURITY.md).

## Arhitectură

- Views/XML, fără Compose sau framework hibrid;
- ViewModel + StateFlow pentru starea ecranelor;
- Storage Access Framework pentru memoria internă și microSD;
- `catalog.json` generat extern, cu scanarea folderelor ca fallback;
- cache intern pentru pornire rapidă;
- Room pentru progres și istoric local;
- DataStore pentru modul de redare și limba audio preferată;
- Media3 ExoPlayer pentru playback;
- Library Builder Python pentru conversie, validare, miniaturi și catalog.

Detalii: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

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

Format video: MP4, H.264 Main, AAC stereo, maximum 1280×720 și 30 fps.

## Mehene Library Builder

Pe calculator sau server:

```bash
python3 tools/mehene_library.py "/Desene originale" "/CardSD/Mehene" --audio-language ron
```

Windows:

```powershell
.\tools\convert-library.ps1 -Source "D:\Desene originale" -Destination "E:\Mehene"
```

Builderul:

- copiază fișierele deja compatibile;
- convertește numai ce este necesar și reutilizează rezultatele valide la rulările următoare;
- preferă pista audio românească;
- optimizează coperțile și generează miniaturi WebP;
- păstrează subtitrările sidecar;
- creează `catalog.json` și `mehene-report.json`;
- continuă procesarea și raportează toate erorile.

Detalii: [docs/LIBRARY_BUILDER.md](docs/LIBRARY_BUILDER.md).

## Build Android

Cerințe:

- JDK 17;
- Android SDK 36;
- internet pe calculator numai pentru prima descărcare a dependențelor.

```bash
./gradlew clean test lintDebug assembleDebug
```

APK debug:

```text
app/build/outputs/apk/debug/app-debug.apk
```

APK release necesită cheia de semnare configurată prin:

- `MEHENE_STORE_FILE`;
- `MEHENE_STORE_PASSWORD`;
- `MEHENE_KEY_ALIAS`;
- `MEHENE_KEY_PASSWORD`.

```bash
./gradlew clean test lintRelease assembleRelease
```

## Instalare kiosk

Procedura Device Owner necesită, de regulă, resetarea tabletei înainte de configurarea conturilor:

```bash
adb install app/build/outputs/apk/release/app-release.apk
adb shell dpm set-device-owner ro.mehene.app/ro.mehene.app.kiosk.MeheneDeviceAdminReceiver
adb shell am start -n ro.mehene.app/ro.mehene.app.MainActivity
```

Procedură și checklist: [docs/DEVICE_SETUP.md](docs/DEVICE_SETUP.md).

## Stadiu

Versiunea 2.0 implementează arhitectura și funcțiile premium. Înainte de instalarea definitivă trebuie validate buildul Android, redarea pe tableta fizică, reboot-ul, microSD și o bibliotecă mare. Vezi [docs/VALIDATION_PLAN.md](docs/VALIDATION_PLAN.md).
