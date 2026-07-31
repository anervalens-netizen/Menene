# Configurarea tabletei Menene

## Precondiție obligatorie

Nu începe instalarea definitivă și nu activa Device Owner până când porțile G0–G6 din `docs/VALIDATION_PLAN.md` nu sunt trecute.

Trebuie să existe:

- APK release semnat;
- checksum SHA-256;
- backup verificat al cheii;
- build și lint fără erori;
- schema Room versiunea 1;
- test de update fără pierdere de date;
- smoke test și redare de durată pe tabletă fără Device Owner.

## Test inițial

1. Construiește APK debug.
2. Instalează:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

3. Configurează o bibliotecă de test.
4. Testează seriale, sezoane, subtitrări, volum, progres și Menene TV.
5. Testează process death, microSD și minimum două ore de redare.
6. Nu activa încă Device Owner.

## Instalare release de test

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Verifică:

- versiunea Menene afișată în Admin;
- progresul și setările;
- update peste versiunea anterioară;
- semnătura și checksum-ul artefactului.

## Instalare definitivă

Device Owner poate necesita resetarea tabletei. Păstrează cheia de semnare APK înainte de instalare.

```bash
adb install app/build/outputs/apk/release/app-release.apk
adb shell dpm set-device-owner ro.mehene.app/ro.mehene.app.kiosk.MeheneDeviceAdminReceiver
adb shell am start -n ro.mehene.app/ro.mehene.app.MainActivity
```

După pornire:

1. selectează biblioteca finală verificată;
2. intră în administrare prin cinci atingeri pe siglă;
3. activează kiosk;
4. repornește tableta de 10 ori în cadrul testului;
5. verifică pornirea automată și blocarea Home/Recents/notificări;
6. verifică ieșirea temporară și revenirea;
7. păstrează rezultatele în `docs/test-results/`.

## Recuperare

Administrarea nu are PIN. `Deschide Android temporar` oprește Lock Task fără să dezactiveze configurația. La revenirea în Menene, kiosk se reactivează.

Păstrează înainte de Device Owner:

- APK-ul release curent;
- APK-ul release anterior;
- checksum-urile;
- cheia de semnare;
- biblioteca și catalogul anterior;
- instrucțiunile ADB;
- copia progresului, dacă este exportată ulterior prin funcția planificată de diagnostic.

## Verdict operațional

Procedura de mai sus nu este considerată validată până la trecerea Gate G7 din `docs/VALIDATION_PLAN.md` pe modelul Samsung real.
