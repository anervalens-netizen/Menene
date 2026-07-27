# Instalare pe Samsung Galaxy Tab A 8.0 (2019)

## 1. Instalare normală pentru test

1. Activează `Developer options` și `USB debugging` pe tabletă.
2. Pentru test rapid, construiește și instalează varianta debug:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pentru instalarea kiosk definitivă folosește APK-ul release semnat, conform secțiunii `APK release semnat` din README:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

4. Deschide Mehene și selectează folderul bibliotecii.

În această etapă aplicația rulează fullscreen. Fără configurarea Device Owner, Android poate permite totuși ieșirea prin combinațiile sistemului.

## 2. Kiosk complet

Kiosk complet folosește Android Lock Task Mode și necesită ca Mehene să fie `Device Owner`.

> Recomandat: tabletă dedicată, fără conturi sau date importante. Operația poate necesita resetare la setările din fabrică.

Pași:

1. Resetează tableta și nu adăuga un cont Google.
2. Activează USB debugging cât mai devreme după configurarea inițială.
3. Instalează APK-ul release.
4. Rulează:

```bash
adb shell dpm set-device-owner ro.mehene.app/ro.mehene.app.kiosk.MeheneDeviceAdminReceiver
adb shell am start -n ro.mehene.app/ro.mehene.app.MainActivity
```

Dacă Android răspunde că dispozitivul este deja provisionat, repetă după resetare, înainte de adăugarea conturilor și a altor aplicații. Unele versiuni Samsung pot necesita provisioning prin QR/Knox în locul comenzii ADB.

După configurare:

- Mehene devine ecranul Home;
- Home, Recents, notificările și bara de navigare sunt blocate;
- aplicația pornește după restart;
- copilul poate naviga numai între seriale, episoade și player.

## Mod părinte

- atinge sigla Mehene de 5 ori în maximum 3 secunde;
- PIN inițial: `2468`;
- schimbă PIN-ul imediat după instalare;
- meniul permite alegerea folderului, rescanarea, activarea/dezactivarea kiosk și ieșirea temporară în Android.

Pentru a reactiva kiosk după ieșirea temporară, deschide din nou Mehene și activează opțiunea din meniul părinte.
