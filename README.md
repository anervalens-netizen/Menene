# Mehene

**Mehene** este o aplicație Android complet offline pentru o bibliotecă personală de desene animate, proiectată pentru **Samsung Galaxy Tab A 8.0 (2019)**: ecran 1280×800, Android 9, aproximativ 2 GB RAM și stocare microSD.

Aplicația nu conține și nu descarcă episoade. Fișierele video obținute legal sunt copiate de proprietar într-un folder, iar Mehene construiește automat catalogul.

## Experiența copilului

- ecran principal cu carduri mari pentru seriale;
- serial → episoade → player fullscreen;
- player cu X, volum +/− și pauză/redare prin atingere;
- indicator pentru episoade începute și bifă pentru cele terminate;
- reluare automată de la ultima poziție;
- feedback pentru buffering, volum și erori de redare;
- fără browser, reclame, conturi, notificări sau acces la internet.

## Decizie explicită: fără securitate

Mehene este folosită exclusiv de un copil supravegheat. **Nu există și nu trebuie adăugate PIN, parolă, autentificare, biometrie sau alte mecanisme de securitate**, decât dacă proprietarul proiectului cere explicit ulterior schimbarea acestei decizii.

Meniul de administrare se deschide prin **5 atingeri pe sigla Mehene** numai pentru a păstra interfața copilului curată. Gestul nu reprezintă securitate. Kiosk-ul previne ieșirile accidentale, nu accesul intenționat al unui utilizator nesupravegheat.

Regula completă: [docs/NO_SECURITY.md](docs/NO_SECURITY.md).

## Biblioteca

```text
Mehene/
├── Bluey/
│   ├── cover.jpg
│   ├── Episod 01 - Titlu.mp4
│   ├── Episod 01 - Titlu.jpg
│   └── Episod 02 - Titlu.mp4
└── Mașini de curse/
    ├── poster.png
    └── 01 - Start.mp4
```

Format acceptat în catalog: **MP4/M4V cu H.264 și AAC**, maximum 1280×720 recomandat. Alte containere video sunt ignorate și apar în diagnosticul bibliotecii, pentru a evita episoade care apar în listă dar nu pot fi decodate de tabletă.

Conversie și reguli: [docs/LIBRARY_FORMAT.md](docs/LIBRARY_FORMAT.md).

## Prima pornire

1. Instalează APK-ul.
2. Deschide Mehene și apasă `Configurează biblioteca`.
3. Selectează folderul `Mehene` din memoria internă sau microSD.
4. Verifică serialele și episoadele.
5. Atinge sigla de 5 ori pentru meniul de administrare.
6. Activează kiosk numai după ce biblioteca funcționează corect.

După prima configurare, selectorul de foldere poate fi deschis numai din meniul de administrare. Dacă Lock Task este activ, Mehene îl oprește temporar pentru DocumentsUI și îl reactivează la revenire.

## Build

Cerințe:

- JDK 17;
- Android SDK 35;
- internet pe calculator numai pentru prima descărcare a Gradle și dependențelor.

```bash
./gradlew clean test lintDebug assembleDebug
```

Pe Windows:

```powershell
.\gradlew.bat clean test lintDebug assembleDebug
```

Scripturile de bootstrap descarcă Gradle 8.13 într-un fișier temporar, verifică SHA-256-ul oficial și publică arhiva în cache numai după validare.

APK debug:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### APK release semnat

```bash
keytool -genkeypair -v -keystore mehene-release.jks -alias mehene -keyalg RSA -keysize 2048 -validity 10000
```

Setează:

- `MEHENE_STORE_FILE`;
- `MEHENE_STORE_PASSWORD`;
- `MEHENE_KEY_ALIAS`;
- `MEHENE_KEY_PASSWORD`.

Apoi:

```bash
./gradlew clean test lintRelease assembleRelease
```

APK:

```text
app/build/outputs/apk/release/app-release.apk
```

Păstrează cheia de semnare în afara repository-ului și într-un backup sigur. Fără aceeași cheie, actualizările nu pot fi instalate peste versiunea existentă.

## Kiosk complet

Fullscreen nu este kiosk complet. Pentru Lock Task real, Mehene trebuie configurată drept `Device Owner` pe tableta dedicată:

```bash
adb shell dpm set-device-owner ro.mehene.app/ro.mehene.app.kiosk.MeheneDeviceAdminReceiver
adb shell am start -n ro.mehene.app/ro.mehene.app.MainActivity
```

Procedura completă: [docs/DEVICE_SETUP.md](docs/DEVICE_SETUP.md).

## Arhitectură

```text
app/src/main/java/ro/mehene/app/
├── data/       # SAF, rezultate tipizate, progres
├── kiosk/      # Device Owner, Home alias, Lock Task, boot
├── model/      # seriale și episoade
├── ui/         # adaptoare și cache imagini
├── util/       # sortare, titluri și grilă adaptivă
├── MainActivity.kt
├── SeriesActivity.kt
└── PlayerActivity.kt
```

Detalii: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Confidențialitate

- manifestul nu solicită permisiunea `INTERNET`;
- fără analytics, reclame sau telemetrie;
- se citește numai folderul ales explicit;
- catalogul și progresul rămân local pe tabletă.

## Licență

Cod MIT. Fișierele video și imaginile bibliotecii nu fac parte din repository.
