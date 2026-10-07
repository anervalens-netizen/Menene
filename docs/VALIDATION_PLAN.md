# Plan de validare și porți de release

Acest document definește testele obligatorii înainte de instalarea definitivă. Un test fără dovadă nu este considerat trecut.

## Reguli

- fiecare rulare notează commitul, versiunea APK, dispozitivul, versiunea Android și rezultatul;
- erorile sunt reparate și testul se repetă integral;
- rezultatele se salvează în `docs/test-results/`;
- produsul rămâne NO-GO cât timp o poartă P0 este deschisă;
- Device Owner se activează numai după trecerea testelor fără kiosk.

# Gate G0 — Validare statică offline

```bash
python3 tools/validate_repo.py
```

## Trebuie să confirme

- XML valid;
- manifest fără permisiune internet;
- politica fără securitate;
- versiune coerentă;
- Builder și validator Python compilabile;
- testele interne ale Builderului;
- lock și publicare atomică la nivel de fișier.

## Dovadă

- output complet salvat;
- commit și data rulării.

# Gate G1 — Build Android curat

Pe un calculator cu JDK 17 și Android SDK Platform 37 (`sdkmanager "platforms;android-37"`), cu un emulator sau dispozitiv conectat:

```bash
python3 tools/validate_repo.py --android
```

Rulează echivalent:

```bash
./gradlew clean test lintDebug assembleDebug lintRelease assembleRelease connectedDebugAndroidTest
```

Pentru release:

```bash
./gradlew clean test lintRelease assembleRelease
```

Instrumentarea trebuie să aibă rezultat separat în raportul de validare. Fără un dispozitiv/emulator sau dacă instrumentarea nu rulează, G1 rămâne nevalidat.

## Criterii

- zero erori de compilare;
- zero erori lint;
- testele unitare trec;
- testele instrumentate Room/cache/progres trec;
- build debug și release sunt generate;
- R8 nu elimină componente necesare;
- schema Room versiunea 1 este generată și comisă;
- Android Studio sync trece.

## Dovadă

- logurile Gradle;
- APK-uri;
- schema Room;
- raport lint;
- SHA-256 al artefactelor.

# Gate G2 — Semnare și upgrade

## Teste

- build release cu cheia definitivă;
- verificarea semnăturii APK;
- backupul cheii în două locații;
- instalare curată;
- update peste versiunea anterioară cu aceeași cheie;
- pornire după upgrade;
- progresul și setările sunt păstrate;
- procedură de rollback verificată pe dispozitiv de test.

## Acceptare

Nicio pierdere de date și niciun conflict de semnătură.

# Gate G3 — Recuperare și concurență Android

- cache catalog trunchiat;
- JSON invalid;
- checksum greșit;
- cache fallback expirat;
- DataStore corupt → valori implicite;
- Room indisponibil la construire;
- Room care aruncă la query-time;
- checkpoint vechi după checkpoint nou;
- reset progres urmat de checkpoint întârziat;
- oprirea procesului imediat după X, Home și finalul episodului;
- anularea unei scanări în timp ce începe alta;
- schimbarea bibliotecii cu folder nou invalid;
- selectarea unui folder valid, dar gol, fără ștergerea progresului altor biblioteci după implementarea namespace-ului;
- `catalog.json` invalid → fallback și diagnostic.

## Acceptare

UI-ul rămâne utilizabil, iar datele mai noi nu sunt înlocuite de date vechi.

# Gate G4 — Library Builder

## Funcțional

- două rulări consecutive: a doua reutilizează rezultatele;
- audio preferat și fallback;
- copertă și miniaturi;
- sidecar-uri SRT/VTT în sursă: numărate în `subtitlesIgnored`, absente din output, `subtitle: null`;
- catalog și raport valide;
- ID-uri stabile după rebuild.

## Erori

- două procese simultane;
- lock stale;
- build mai lung decât pragul stale după implementarea heartbeat-ului;
- kill în timpul copierii;
- kill în timpul transcodingului;
- kill în timpul scrierii catalogului;
- video corupt;
- sursă modificată în timpul buildului;
- `--publish-partial` numai explicit;
- nicio cale nu iese din destinație;
- generația activă rămâne intactă la orice eșec după implementarea stagingului.

## Acceptare

Tableta vede numai un catalog complet valid, iar rollbackul la generația anterioară este demonstrat.

# Gate G5 — Samsung fără Device Owner

## Smoke test

- cold start și warm start;
- configurează biblioteca pe microSD;
- Home → serial → sezon → episod → X;
- Continuă;
- Mehene TV;
- auto-next și anulare;
- pauză manuală;
- retry;
- volum;
- bara afișează timpul curent și durata;
- glisarea la 25% și 75% mută redarea în poziția cerută;
- butoanele −10/+10 secunde respectă limitele început/final;
- poziția aleasă supraviețuiește background/foreground;
- schimbarea limbii audio;
- catalog cu `subtitle: null` și zero SRT/VTT în output;
- ecran stins/aprins;
- background/foreground repetat;
- process death/recreare;
- scoatere și reintroducere microSD;
- fișier corupt și codec incompatibil.

## Acceptare

Zero crash, ANR sau blocare fără cale de revenire.

# Gate G6 — Codec și redare de durată

## Matrice minimă

- H.264 Main Level 3.1;
- 720p la 24, 25 și 30 fps;
- AAC stereo 44,1/48 kHz;
- video fără audio;
- SRT cu diacritice și VTT în sursă: ignorate, fără fișiere/captions în redare;
- fișier trunchiat.

## Soak

- redare continuă minimum două ore;
- minimum 20 auto-next;
- 20 background/foreground;
- 10 opriri forțate și redeschideri;
- monitorizare RAM, temperatură și baterie.

## Acceptare

- fără crash/ANR/OOM;
- memoria nu crește monoton;
- progresul pierdut este cel mult ultimul interval de checkpoint;
- formatele recomandate folosesc decoderul disponibil și redau fluid.

# Gate G7 — Kiosk și reboot

După resetarea dispozitivului:

- provisioning Device Owner;
- activare Lock Task;
- Home și Recents;
- notification shade;
- power menu conform configurației;
- folder picker din administrare;
- ieșire temporară și revenire;
- dezactivare controlată kiosk;
- reboot complet de 10 ori;
- pornire automată după fiecare reboot.

## Acceptare

Copilul nu iese accidental, iar adultul poate recupera și administra dispozitivul fără reinstalare.

# Gate G8 — Bibliotecă mare și performanță

## Date

- minimum 50 seriale;
- minimum 500 episoade;
- mai multe sezoane;
- artwork complet și incomplet;
- sidecar-uri SRT/VTT în sursă, dar zero subtitrări în output.

## Teste

- cold start din cache;
- warm start;
- scroll rapid;
- deschidere serial mare;
- schimbare sezon;
- rescanare completă;
- anularea rescanării;
- rebuild Builder;
- comutare între două biblioteci după implementarea namespace-ului de progres.

## Praguri inițiale

- cold start până la catalog: maximum 3 secunde;
- warm start: maximum 1,5 secunde;
- fără OOM;
- fără degradare continuă a memoriei;
- scroll acceptabil vizual pe dispozitiv.

Pragurile pot fi ajustate o singură dată după prima măsurare, cu justificare documentată.

# Gate G9 — Visual QA și accesibilitate

- capturi etalon Home/Series/Player/Admin;
- verificare 1280×800;
- texte lungi;
- fallback fără artwork;
- contrast;
- touch targets;
- Accessibility Scanner;
- TalkBack;
- ordinea focusului;
- descrieri fără duplicare.

# Gate final

Produsul este considerat gata de instalare numai când G0–G9 sunt trecute sau când o abatere este acceptată explicit în `docs/test-results/ACCEPTED_RISKS.md` cu motiv, impact și plan de remediere.
