# Configurarea tabletei

## Test inițial

1. Construiește APK debug.
2. Instalează:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

3. Configurează biblioteca.
4. Testează seriale, sezoane, subtitrări, volum, progres și Mehene TV.
5. Nu activa încă Device Owner.

## Instalare definitivă

Device Owner poate necesita resetarea tabletei. Păstrează cheia de semnare APK înainte de instalare.

```bash
adb install app/build/outputs/apk/release/app-release.apk
adb shell dpm set-device-owner ro.mehene.app/ro.mehene.app.kiosk.MeheneDeviceAdminReceiver
adb shell am start -n ro.mehene.app/ro.mehene.app.MainActivity
```

După pornire:

1. selectează biblioteca;
2. intră în administrare prin cinci atingeri pe siglă;
3. activează kiosk;
4. repornește tableta;
5. verifică pornirea automată și blocarea Home/Recents/notificări.

## Recuperare

Administrarea nu are PIN. `Deschide Android temporar` oprește Lock Task fără să dezactiveze configurația. La revenirea în Mehene, kiosk se reactivează.
