# Audit final Mehene 2.1

## Identificare

- Repository: `anervalens-netizen/Mehene`
- Branch auditat: `main`
- Commit fixat: `f12b7b4b3af94bdca2a5ba0e98fea0a3d197156a`
- Versiune aplicație: `2.1.0` / `versionCode 4`
- Data auditului: 29 iulie 2026
- Tip audit: static, independent de rapoartele anterioare; cod Android, UI, date locale, player, kiosk, Builder, build, teste și documentație

## Limitarea auditului

Nu a fost disponibil un mediu cu Android SDK și nici tableta Samsung. Prin urmare, nu sunt confirmate compilarea completă, R8, lint, testele instrumentate, decoderul hardware, microSD, Device Owner sau comportamentul după reboot.

## Verdict

### Cod sursă

**GO condiționat pentru etapa Release Candidate / build și calificare.**

Nu a fost identificat un defect P0 confirmat static care să justifice rescrierea arhitecturii. Structura aplicației este adecvată scopului, iar hardening-ul 2.1 tratează corect multe scenarii de corupere și process death.

### Instalare definitivă

**NO-GO până la închiderea porților obligatorii de release.**

Blocajele nu sunt funcții lipsă, ci absența dovezilor de build și hardware, plus câteva riscuri P1 care trebuie rezolvate înainte de a numi produsul complet matur.

## Evaluare

| Domeniu | Scor | Observație |
|---|---:|---|
| Concept și experiență copil | 9.0/10 | Flux foarte simplu, potrivit utilizării supravegheate |
| Front-end și design | 8.3/10 | Coerent și premium ca structură; necesită validare vizuală reală |
| Arhitectură Android | 8.8/10 | Responsabilități separate fără supra-arhitectură |
| Catalog și bibliotecă offline | 8.7/10 | Cache, validator, fallback și anulare bine gândite |
| Progres și recuperare | 8.5/10 | Runtime state + Room + backup atomic; rămâne problema namespace/prune |
| Player Media3 | 8.4/10 | Lifecycle diferențiat și checkpoint-uri; necesită test decoder/soak |
| Kiosk | 7.8/10 | Logică bună, dar rezultatul depinde de Samsung/firmware |
| Library Builder | 7.7/10 | Puternic și fail-closed logic; publicarea și lock-ul mai pot fi întărite |
| Testare și release engineering | 5.5/10 | Cel mai slab domeniu: buildul și testele hardware nu au fost executate |
| Pregătire pentru instalare definitivă | 6.8/10 | Cod avansat, dar produsul nu este încă calificat |

## Puncte forte confirmate

1. Aplicația rămâne complet offline și nu solicită infrastructură runtime.
2. Regula fără PIN, parolă sau autentificare este explicită și consecventă.
3. UI-ul separă clar experiența copilului de administrare.
4. ViewModel + StateFlow elimină mare parte din logica asincronă din Activity-uri.
5. Catalogul este validat, cache-ul este atomic și scanarea poate fi anulată.
6. Schimbarea bibliotecii este tranzacțională la nivel de URI și catalog.
7. Progresul are sursă runtime, persistență Room și backup atomic.
8. Playerul salvează checkpoint-uri critice în afara lifecycle-ului Activity.
9. Builderul validează fișierele și păstrează catalogul anterior în cazul erorilor.
10. Documentația declară corect că tableta fizică este poarta finală de acceptare.

# Constatări

## Porți P0 de release

### RC-001 — Buildul Android complet nu este confirmat

**Impact:** critic pentru release.

Nu există dovadă că `test`, `lintDebug`, `assembleDebug`, `lintRelease`, `assembleRelease`, KSP, Room schema export și R8 trec împreună. Versiunile dependențelor sunt declarate, dar nu au fost rezolvate și compilate în mediul real.

**Acțiune:** rulează validatorul Android într-un mediu curat, repară toate erorile și păstrează raportul de build drept dovadă de release.

### RC-002 — Tableta țintă nu este calificată

**Impact:** critic pentru funcția principală.

Nu sunt confirmate:

- H.264/AAC prin decoder hardware;
- subtitrări SRT/VTT;
- microSD prin Storage Access Framework;
- consumul de memorie pe aproximativ 2 GB RAM;
- redarea continuă și temperatura;
- Samsung Device Owner, Home alias, boot și Lock Task.

**Acțiune:** execută integral `docs/VALIDATION_PLAN.md` pe dispozitivul real.

### RC-003 — Semnarea, update-ul și rollback-ul nu au fost repetate

**Impact:** critic operațional.

Cheia de semnare este configurabilă, dar nu există dovadă pentru:

- backupul cheii;
- instalarea release;
- update peste versiunea anterioară;
- păstrarea progresului și setărilor;
- rollback documentat.

**Acțiune:** creează și păstrează cheia, generează checksum pentru APK, execută instalare și update controlat înainte de Device Owner.

## P1 — Stabilitate rămasă

### MEH-101 — Publicarea Builderului nu este complet izolată

`mehene_builder.py` apelează nucleul legacy, iar acesta scrie `catalog.json` înainte ca wrapperul să valideze și să decidă păstrarea sau restaurarea catalogului anterior. Rezultatul final este fail-closed, dar există o fereastră în care un consumator concurent poate vedea candidatul încă nevalidat.

**Recomandare:** Builderul trebuie să genereze totul într-un director de staging și să publice catalogul numai după validare, printr-un singur switch atomic de generație.

### MEH-102 — Lock-ul Builderului poate fi furat după șase ore

Lock-ul devine „stale” exclusiv după timpul modificării. Un build legitim mai lung de șase ore poate fi considerat abandonat, iar un al doilea proces poate porni concurent. Lock-ul nu are heartbeat și nici token de proprietate verificat la ștergere.

**Recomandare:** heartbeat periodic, verificare PID unde este posibil și token unic; procesul eliberează numai lock-ul pe care îl deține.

### MEH-103 — Progresul nu este separat pe biblioteci

Progresul este indexat numai după `episodeId`. La activarea altui catalog, `MainViewModel` elimină toate ID-urile care nu mai există. Selectarea temporară a unei biblioteci diferite sau a unui folder valid, dar gol, poate șterge istoricul bibliotecii anterioare.

**Recomandare:** adaugă `libraryId`/namespace la progres și înlocuiește prune imediat cu retenție sau curățare explicită din administrare. Un catalog gol nu trebuie să declanșeze automat ștergerea globală.

### MEH-104 — Fallback-ul Room este best-effort, nu comutare garantată

`AppContainer` încearcă DAO în memorie dacă obținerea Room/DAO aruncă excepție. Totuși, Room deschide frecvent baza de date la prima interogare, nu la construirea DAO-ului. Eșecurile ulterioare sunt izolate de `ProgressRepository`, dar nu determină automat înlocuirea DAO-ului defect.

**Recomandare:** health probe explicit la startup și un `SwitchablePlaybackProgressDao`, sau documentarea clară că runtime state + backup sunt mecanismul real de continuitate.

### MEH-105 — Nu există încă strategie de migrare Room

Schema este versiunea 1, iar directorul de scheme nu conține încă artefactul generat de un build real. Prima schimbare de structură poate deveni riscantă fără test de migrare.

**Recomandare:** generează și comite schema v1, definește politica „fără destructive migration” și adaugă teste pentru fiecare migrare înainte de creșterea versiunii.

### MEH-106 — Diagnosticul runtime nu este persistent

Erorile playerului, cache-ului, Room și kiosk sunt în principal în Logcat. După un incident pe tabletă, părintele nu are un istoric local ușor de extras.

**Recomandare:** jurnal local circular, limitat ca dimensiune, vizibil și exportabil din AdminActivity: ultimul boot, scanări, erori media, schimbări microSD, Room fallback și kiosk.

### MEH-107 — Acoperirea testelor UI și process-death este insuficientă

Există teste pentru catalog, cache, progres și planificarea cozii, dar lipsesc teste end-to-end Android pentru:

- prima configurare;
- revenirea din folder picker;
- recrearea PlayerActivity;
- auto-next;
- schimbarea sezonului;
- modurile de eroare;
- revenirea din Settings și kiosk.

**Recomandare:** suită instrumentată minimală orientată pe fluxurile critice, nu pe detalii vizuale fragile.

## P2 — Maturizare premium

### MEH-201 — Builderul nu detectează modificarea sursei în timpul rulării

Fingerprint-ul este calculat înainte de procesare. Dacă sursa se schimbă pe durata unui build lung, catalogul poate reprezenta o stare mixtă.

**Recomandare:** fingerprint înainte și după; publicare numai dacă sunt identice.

### MEH-202 — Builderul nu curăță fișierele orfane

Ștergerea unui episod din sursă îl scoate din catalog, dar fișierul vechi poate rămâne în destinație și consuma spațiu.

**Recomandare:** manifest al generației și comandă separată `--prune-orphans`, implicit dry-run.

### MEH-203 — Buildul folosește bootstrap Gradle personalizat

Scriptul verifică checksum-ul și este rezonabil de sigur, dar nu este wrapperul Gradle standard. Acest lucru poate surprinde Android Studio sau unelte care presupun `gradle-wrapper.jar`.

**Recomandare:** trecere la wrapper standard, checksum oficial și dependency verification/locking.

### MEH-204 — Compatibilitatea catalogului nu are matrice formală

Schema catalogului este versiunea 1, însă nu există o matrice Builder ↔ APK și nici politică de compatibilitate pentru schema 2.

**Recomandare:** documentează versiunile acceptate și păstrează minimum o versiune anterioară citibilă atunci când schema evoluează.

### MEH-205 — Performanța nu are praguri măsurabile

Planul cere măsurarea pornirii și memoriei, dar nu stabilește criterii de acceptare.

**Recomandare inițială pentru tableta țintă:**

- cold start până la catalog: maximum 3 secunde din cache;
- warm start: maximum 1,5 secunde;
- scroll fără blocaje vizibile majore;
- fără OOM cu 50 seriale / 500 episoade;
- redare 2 ore fără creștere continuă a memoriei.

Pragurile se ajustează după prima măsurătoare reală.

### MEH-206 — Calitatea vizuală și accesibilitatea nu sunt validate

Interfața este bine structurată, dar nu există capturi etalon, test pe densitatea reală, Accessibility Scanner sau verificare TalkBack.

**Recomandare:** golden screenshots pentru cele trei ecrane principale și o rundă de accesibilitate înainte de release final.

## Decizii care rămân corecte

- fără PIN, parolă, autentificare sau biometrie;
- fără server runtime;
- fără conturi, analytics sau internet;
- fără Compose doar pentru modernizare cosmetică;
- fără microservicii sau module Gradle inutile;
- fără funcții noi înainte de închiderea porților Release Candidate.

## Definiția produsului „gata de instalare”

Mehene poate fi considerată gata numai când:

1. buildul debug și release semnat trec din mediu curat;
2. schema Room v1 este exportată și verificată;
3. update-ul păstrează progresul și setările;
4. toate testele obligatorii pe Samsung sunt trecute;
5. Builderul este rulat pe biblioteca reală fără erori;
6. biblioteca de 500 episoade respectă pragurile de performanță;
7. cheia, APK-ul, checksum-ul și procedura de rollback sunt arhivate;
8. toate constatările P1 din roadmap sunt fie închise, fie acceptate explicit cu justificare.

## Concluzie

Mehene 2.1 este o aplicație bine proiectată și mult peste nivelul unui prototip. Următoarea etapă nu trebuie să fie adăugarea de funcții. Prioritatea este transformarea codului într-un Release Candidate demonstrat prin build, teste și calificarea tabletei. După aceea, puținele lucrări P1 rămase pot ridica produsul la un nivel personal premium și predictibil operațional.
