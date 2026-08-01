# Roadmap Mehene

> Document istoric pentru 2.1. Verdictul curent este în [raportul final Menene 2.4](test-results/2026-08-01-menene-2.4-final.md); stările și porțile de mai jos descriu exclusiv momentul auditului inițial.

## Scop

Acest roadmap pornește de la auditul commitului `f12b7b4b3af94bdca2a5ba0e98fea0a3d197156a` și descrie ordinea recomandată pentru a transforma Mehene 2.1 din cod premium necalificat într-un produs instalabil, stabil și ușor de întreținut.

## Reguli de execuție

1. Nu se adaugă funcții noi înainte de închiderea porților Release Candidate.
2. Fiecare etapă trebuie să lase dovezi: log de build, raport de test, checksum sau checklist semnat.
3. Politica fără PIN/parolă/autentificare rămâne obligatorie.
4. Aplicația rămâne offline și fără server runtime.
5. Nu se introduce complexitate arhitecturală fără un risc concret pe care îl rezolvă.
6. `main` trebuie să rămână într-o stare documentată; lucrările mari se integrează prin PR și squash.

# Starea istorică la 2.1

- Versiune: `2.1.0`
- Cod: avansat și coerent
- Verdict: GO pentru Release Candidate, NO-GO pentru instalare definitivă
- Următoarea etapă: **R1 — închiderea buildului Android**

# R0 — Audit și documentație

**Status: finalizat**

## Livrabile

- audit final fixat pe commit;
- roadmap detaliat;
- arhitectură și documentație de stabilitate corectate;
- plan de validare transformat în porți de release;
- limitările Builderului documentate explicit.

## Criteriu de ieșire

Documentația descrie exact ce este confirmat, ce este doar proiectat și ce rămâne nevalidat.

# R1 — Release Candidate build closure

**Prioritate: P0**  
**Complexitate: M**  
**Obiectiv:** obținerea primului APK debug și release demonstrat, fără a instala încă Device Owner.

## R1.1 — Mediu reproductibil

- pregătește JDK 17 și Android SDK 36 pe PC-ul de dezvoltare;
- rulează `python3 tools/validate_repo.py --android` dintr-un checkout curat;
- salvează versiunile Java, SDK, Gradle și OS în raport;
- verifică Android Studio import/sync;
- înlocuiește bootstrapul personalizat cu Gradle Wrapper standard;
- adaugă checksum oficial al distribuției;
- activează Gradle dependency verification;
- opțional, generează lockfiles pentru configurațiile principale.

### Acceptare

- checkout nou → o singură comandă produce același APK;
- nicio dependență nu este rezolvată din versiuni dinamice;
- wrapperul funcționează identic pe Windows și Linux.

## R1.2 — Compilare și analiză statică

Execută și repară până la zero erori:

```bash
./gradlew clean test lintDebug assembleDebug
./gradlew lintRelease assembleRelease
```

Verificări suplimentare:

- KSP Room;
- ViewBinding;
- resurse lipsă;
- R8/ProGuard;
- manifest merge;
- API 23 compatibility;
- warninguri Media3 relevante.

### Acceptare

- ambele APK-uri sunt generate;
- lint nu are erori;
- release minificat pornește într-un emulator sau dispozitiv de test;
- dimensiunea APK este înregistrată.

## R1.3 — Room schema și migrare

- generează și comite schema Room versiunea 1;
- verifică JSON-ul schemei;
- adaugă test de deschidere a bazei create de versiunea curentă;
- documentează regula: fără `fallbackToDestructiveMigration`;
- creează șablonul de test pentru migrarea 1→2 înainte de orice schimbare viitoare.

### Acceptare

- schema v1 există în repository;
- testele instrumentate Room trec;
- progresul supraviețuiește reinstalării de tip update.

## R1.4 — Semnare și artefacte

- generează cheia release;
- păstrează cheia și parolele în două locații controlate;
- construiește APK release semnat;
- generează SHA-256 pentru APK;
- salvează `versionName`, `versionCode`, commit și data buildului într-un manifest de release;
- instalează debug, apoi release într-un mediu de test separat;
- testează update release→release cu aceeași cheie.

### Acceptare

- APK semnat verificabil;
- update fără pierdere de date;
- cheia poate fi restaurată din backup;
- există procedură de rollback.

## Poarta R1

R1 este închis numai când există:

- log build;
- rezultate unit/instrumented;
- schema Room;
- APK debug;
- APK release semnat;
- checksum;
- test de update trecut.

# R2 — Închiderea riscurilor P1 din cod

**Prioritate: P1**  
**Complexitate: L**  
**Obiectiv:** eliminarea ultimelor scenarii care pot produce date pierdute sau comportament concurent imprevizibil.

## R2.1 — Builder cu staging real

Înlocuiește modelul wrapper + nucleu legacy cu un singur pipeline:

```text
SOURCE
  ↓
STAGING/generație-unică
  ↓ conversie + miniaturi + catalog candidat
VALIDARE COMPLETĂ
  ↓
PUBLICARE GENERAȚIE
```

Taskuri:

- nucleul media nu mai scrie `catalog.json` direct în biblioteca activă;
- toate fișierele JSON sunt generate în staging;
- catalogul candidat este validat înainte să devină vizibil;
- publicarea folosește un pointer/generation manifest atomic;
- tableta citește numai generația completă;
- catalogul anterior rămâne disponibil pentru rollback;
- elimină monkey-patching-ul funcției legacy;
- depreciază și apoi elimină `mehene_library.py`.

### Acceptare

- întreruperea în orice punct nu schimbă generația activă;
- un cititor concurent vede fie generația veche, fie cea nouă, niciodată un intermediar;
- rollbackul este o singură operație.

## R2.2 — Lock Builder robust

- token UUID în lock;
- PID și hostname;
- heartbeat periodic;
- stale numai dacă heartbeat-ul expiră și procesul nu mai există unde poate fi verificat;
- `__exit__` șterge lock-ul numai dacă tokenul coincide;
- opțiune `--force-unlock` explicită și logată;
- test cu build mai lung decât pragul stale;
- test kill -9 și recuperare.

### Acceptare

Două builduri nu pot modifica simultan aceeași destinație, indiferent de durată.

## R2.3 — Namespace de progres pe bibliotecă

Modifică modelul:

```text
libraryId + episodeId → progress
```

Taskuri:

- `libraryId` stabil în catalog;
- coloană `libraryId` în Room;
- migrare Room 1→2;
- backup atomic schema 2;
- progresul bibliotecilor inactive este păstrat;
- schimbarea bibliotecii nu șterge istoricul vechi;
- elimină prune global automat;
- adaugă acțiune explicită „Șterge progresul bibliotecilor inactive”;
- un catalog gol nu șterge nimic.

### Acceptare

Comutarea A→B→A restaurează progresul A integral.

## R2.4 — Room health și comutare runtime

- interfață DAO comutabilă;
- probe de citire/scriere la warm-up;
- dacă Room eșuează, comutare explicită la DAO în memorie + backup;
- rate-limit pentru logurile erorilor Room;
- buton Admin „Reîncearcă baza de date”;
- diagnostic clar: `Room normal`, `backup mode`, `in-memory mode`;
- test cu DAO care aruncă la prima și la a doua interogare.

### Acceptare

O bază Room coruptă nu produce erori repetate pe fiecare operație și nu blochează progresul runtime.

## R2.5 — Jurnal local de diagnostic

Adaugă un ring buffer local, maximum aproximativ 256 KB:

- boot și versiune;
- sursa catalogului și durata scanării;
- fallback catalog;
- microSD indisponibil;
- eroare Media3 clasificată;
- timeout buffering;
- Room/backup mode;
- activare/dezactivare kiosk;
- Builder report importat opțional.

AdminActivity:

- ultimele evenimente;
- copiere/export text;
- golire jurnal;
- fără trimitere automată sau internet.

### Acceptare

Un incident poate fi diagnosticat după restart fără Logcat.

## Poarta R2

- Builder staging și lock trec testele de concurență;
- progresul este namespaced și migrat;
- Room fallback este demonstrat prin teste;
- jurnalul local funcționează și rămâne limitat.

# R3 — Calificarea Samsung Tab A

**Prioritate: P0 pentru instalare**  
**Complexitate: M**  
**Obiectiv:** confirmarea comportamentului pe hardware-ul real.

## R3.1 — Smoke test fără Device Owner

- instalare APK release;
- configurare microSD;
- 10 seriale / minimum 30 episoade;
- coperți, sezoane, subtitrări;
- audio română/engleză/original;
- Continuă și Mehene TV;
- pauză, retry, X și volum;
- ecran stins/aprins;
- process recreation prin developer option „Don’t keep activities”.

### Acceptare

Zero crash, progres corect și UI complet utilizabil.

## R3.2 — Codec matrix

Testează mostre:

- H.264 Main Level 3.1, 720p/24/25/30;
- AAC stereo 44,1 și 48 kHz;
- fără audio;
- SRT cu diacritice;
- VTT;
- fișier trunchiat;
- card scos în redare.

### Acceptare

Formatele recomandate pornesc hardware-accelerat și erorile controlate revin în UI fără blocare.

## R3.3 — Soak și resurse

- redare continuă minimum 2 ore;
- minimum 20 tranziții auto-next;
- monitorizare RAM, temperatură, baterie și decoder;
- 20 background/foreground;
- 10 opriri forțate și redeschideri.

### Praguri inițiale

- fără crash/ANR/OOM;
- memoria nu crește monoton;
- progres pierdut maximum ultimul interval de checkpoint;
- interfața rămâne responsivă.

## R3.4 — Kiosk/Device Owner

După resetarea tabletei:

- provisioning Device Owner;
- Home, Recents, notifications;
- power menu conform deciziei produsului;
- reboot de 10 ori;
- pornire automată;
- ieșire temporară și revenire;
- folder picker după Lock Task;
- dezactivare controlată kiosk.

### Acceptare

Copilul nu iese accidental din aplicație, iar administrarea supravegheată rămâne recuperabilă.

## R3.5 — Bibliotecă mare

- 50 seriale;
- 500 episoade;
- catalog generat;
- pornire din cache;
- rescanare;
- scroll rapid;
- schimbare sezon;
- generare/reconstruire Builder.

### Praguri

- cold start din cache ≤ 3 s;
- warm start ≤ 1,5 s;
- fără OOM;
- scroll acceptabil vizual;
- rescanarea poate fi anulată fără stare veche publicată.

## Poarta R3

Checklistul complet este trecut și păstrat în `docs/test-results/` cu model, versiune Android, APK și commit.

# R4 — Finisare premium

**Prioritate: P2**  
**Complexitate: M**

## R4.1 — Visual QA

- capturi etalon pentru Home, Series, Player, Admin și erori;
- verificare 1280×800 reală;
- contrast;
- truncare texte lungi;
- coperți landscape/portrait;
- stări fără artwork;
- animații scurte numai dacă nu afectează dispozitivul.

## R4.2 — Accesibilitate

- Android Accessibility Scanner;
- TalkBack pentru carduri și controale;
- eliminarea descrierilor duplicate;
- descrierea stării „Continuă/Terminat”;
- verificare touch targets;
- ordinea focusului.

## R4.3 — Teste UI critice

Adaugă numai teste stabile pentru:

- prima configurare;
- Home→Series→Player→X;
- recrearea PlayerActivity;
- auto-next și anulare;
- schimbarea sezonului;
- eroare media/retry;
- Admin fără autentificare;
- revenire din folder picker/Settings.

## R4.4 — Opțiuni locale utile, numai după validare

Funcții opționale, nu obligatorii:

- limită maximă de volum configurabilă;
- oprire după un episod sau după o durată;
- ascunderea temporară a unui serial;
- ordine manuală a serialelor în catalog;
- temă vizuală suplimentară foarte ușoară.

Nu se adaugă profiluri, conturi sau sincronizare.

# R5 — Release și operare

**Prioritate: P1**  
**Complexitate: M**

## R5.1 — Comandă locală de release

```bash
./tools/release.sh 2.2.0
```

Trebuie să:

- verifice repository curat;
- ruleze validatorul complet;
- ruleze build release;
- verifice semnătura;
- genereze checksum;
- creeze manifest și changelog;
- copieze APK-ul într-un folder de artefacte;
- nu publice nimic automat pe internet.

## R5.2 — Runbook de instalare

Documentează:

- precondiții;
- backup cheie;
- build și verificare;
- instalare test;
- update;
- Device Owner;
- rollback;
- recuperare din kiosk;
- înlocuire microSD;
- reinstalare fără pierderea progresului, unde este posibil.

## R5.3 — Runbook bibliotecă

- build într-o destinație staging;
- verificare report;
- sincronizare pe microSD;
- verificare checksum/catalog;
- rescanare controlată;
- rollback la generația anterioară;
- curățare fișiere orfane numai după dry-run.

# R6 — Mentenanță ulterioară

**Prioritate: continuă**

- actualizări de dependențe numai după build și test pe tabletă;
- fiecare schimbare Room include migrare;
- fiecare schimbare catalog include matrice compatibilitate;
- fiecare release crește `versionCode`;
- păstrează minimum ultimul APK funcțional și catalogul anterior;
- audit scurt înainte de fiecare instalare majoră;
- audit complet numai după schimbări arhitecturale semnificative.

# Ordinea recomandată exactă

1. R1 — build complet și release semnat.
2. R2.1 + R2.2 — Builder staging și lock.
3. R2.3 — namespace progres și migrare Room.
4. R2.4 + R2.5 — fallback Room și diagnostic local.
5. R3 — calificarea tabletei.
6. R4 — finisare vizuală și teste UI.
7. R5 — automatizare locală și runbook.
8. R6 — mentenanță.

# Definiție finală de succes

Produsul este „gata” când poate fi instalat pe o tabletă resetată, pornește după fiecare reboot, redă biblioteca reală minimum două ore, nu pierde progres la update sau process death, poate fi recuperat fără Logcat și poate primi o bibliotecă nouă printr-un proces Builder atomic și reversibil.
