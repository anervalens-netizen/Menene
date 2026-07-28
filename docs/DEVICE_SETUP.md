# Instalare pe Samsung Galaxy Tab A 8.0 (2019)

## Test normal

```bash
./gradlew clean test lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Deschide Mehene și configurează biblioteca. În această etapă aplicația este fullscreen, dar Android permite ieșirea normală.

## Kiosk complet

Lock Task complet necesită ca Mehene să fie `Device Owner`. Tableta trebuie tratată ca dispozitiv dedicat și poate necesita resetare din fabrică.

1. Resetează tableta.
2. Nu adăuga încă un cont Google.
3. Activează Developer options și USB debugging.
4. Instalează APK-ul release semnat.
5. Configurează Device Owner:

```bash
adb shell dpm set-device-owner ro.mehene.app/ro.mehene.app.kiosk.MeheneDeviceAdminReceiver
adb shell am start -n ro.mehene.app/ro.mehene.app.MainActivity
```

6. În Mehene, alege folderul bibliotecii și verifică redarea.
7. Atinge sigla de 5 ori și selectează `Activează kiosk`.

Kiosk-ul este dezactivat implicit tocmai pentru ca prima configurare a folderului să nu fie blocată de Lock Task.

## Administrare

Cinci atingeri pe siglă deschid direct meniul. Nu există PIN sau altă autentificare, deoarece tableta este folosită numai sub supraveghere.

Meniul permite:

- alegerea unui alt folder;
- rescanarea bibliotecii;
- activarea/dezactivarea kiosk;
- diagnosticul fișierelor ignorate;
- ieșirea în setările Android.

La alegerea unui folder nou, Mehene oprește temporar Lock Task, deschide selectorul Android și reactivează kiosk la revenire. Permisiunea persistentă pentru folderul vechi este eliberată după salvarea celui nou.

## După restart

Când Device Owner și kiosk sunt active:

- aliasul Mehene Home este activ;
- aplicația este allowlistată pentru Lock Task;
- Home, Recents și bara de navigare sunt blocate;
- BootReceiver pornește aplicația numai dacă biblioteca este configurată;
- overlay-urile externe sunt restricționate.

Unele versiuni Samsung pot necesita provisioning prin QR sau Knox în locul comenzii ADB.
