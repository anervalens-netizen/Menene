# Mehene

**Mehene** este o aplicație Android offline pentru o bibliotecă personală de desene animate, proiectată pentru **Samsung Galaxy Tab A 8.0 (2019)**: ecran 1280×800, Android 9, 2 GB RAM și stocare extensibilă prin microSD.

Nu conține episoade și nu descarcă nimic de pe internet. Părintele copiază fișierele video legal obținute într-un folder, iar aplicația construiește automat catalogul.

## Experiența copilului

- ecran principal cu carduri mari pentru toate serialele;
- un singur nivel intermediar cu episoadele serialului;
- player fullscreen cu numai X, volum +/− și pauză/redare prin atingerea imaginii;
- reluarea episodului de unde a rămas;
- fără setări, notificări, browser, reclame, conturi sau acces la internet;
- kiosk complet când aplicația este configurată ca Android Device Owner.

## Design

Interfața folosește culori luminoase, forme rotunjite, text mare și maximum trei carduri pe rând. Nu folosește elemente sau mărci Cartoon Network. Identitatea vizuală este originală, sub numele Mehene.

Implementarea este Android nativ în Kotlin, cu Views/XML și Media3 ExoPlayer. Nu folosește Compose sau framework-uri hibride, pentru a păstra consumul redus pe hardware-ul din 2019.

## Biblioteca

Alege un folder cu câte un subfolder pentru fiecare serial:

```text
Mehene/
├── Serialul 1/
│   ├── cover.jpg
│   ├── Episod 01.mp4
│   └── Episod 02.mp4
└── Serialul 2/
    ├── poster.png
    └── 01 - Pilot.mp4
```

Format video recomandat: **MP4 + H.264 Main + AAC stereo, maximum 1280×720**. Detalii și scripturi de conversie: [docs/LIBRARY_FORMAT.md](docs/LIBRARY_FORMAT.md).

## Prima pornire

1. Instalează APK-ul.
2. Apasă `Configurează biblioteca`.
3. Selectează folderul `Mehene` din memoria internă sau de pe microSD.
4. Aplicația scanează automat serialele și episoadele.
5. Atinge sigla de 5 ori pentru meniul părinte. PIN inițial: `2468`.
6. Schimbă PIN-ul.

## Build

Cerințe:

- Android Studio compatibil cu AGP 8.13;
- Android SDK 36;
- JDK 17;
- conexiune la internet numai pe PC, pentru prima descărcare a dependențelor.

```bash
./gradlew test assembleRelease
```

Pe Windows:

```powershell
.\gradlew.bat test assembleRelease
```

Scripturile `gradlew` incluse descarcă Gradle 8.13 la prima rulare. Aplicația rezultată nu are nevoie de internet.

APK:

```text
app/build/outputs/apk/release/app-release.apk
```

## Kiosk complet

Fullscreen simplu nu este suficient pentru a bloca toate gesturile Android. Pentru blocare reală, Mehene trebuie configurată drept `Device Owner` pe o tabletă dedicată:

```bash
adb shell dpm set-device-owner ro.mehene.app/.kiosk.MeheneDeviceAdminReceiver
adb shell am start -n ro.mehene.app/.MainActivity
```

Procedura completă și limitările Samsung: [docs/DEVICE_SETUP.md](docs/DEVICE_SETUP.md).

## Structură

```text
app/src/main/java/ro/mehene/app/
├── data/       # scanare bibliotecă, preferințe, progres
├── kiosk/      # Device Owner, Lock Task, boot
├── model/      # seriale și episoade
├── ui/         # adaptoare și cache imagini
├── MainActivity.kt
├── SeriesActivity.kt
└── PlayerActivity.kt
```

Detalii: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Confidențialitate

- nu există permisiune `INTERNET`;
- nu există analytics, reclame sau telemetrie;
- aplicația citește numai folderul ales explicit de părinte;
- toate datele și pozițiile de redare rămân local pe tabletă.

## Licență

Codul este disponibil sub licența MIT. Fișierele video și imaginile bibliotecii nu fac parte din repository și trebuie utilizate în conformitate cu drepturile aplicabile.
