# Stabilitate Menene 2.1

Menene este proiectată ca un aparat offline care trebuie să continue să funcționeze când un strat local eșuează. Acest document separă explicit garanțiile implementate de limitările care încă necesită dezvoltare sau test hardware.

## Garanții implementate

### 1. Catalog recuperabil

- `catalog.json` este validat structural și semantic înainte de utilizare;
- căile relative nesigure sau inexistente sunt respinse;
- cache-ul intern folosește `AtomicFile`, checksum SHA-256 și versiune de schemă;
- cache-ul catalogului generat este legat de fingerprint-ul exact al sursei;
- scanarea directă a folderelor are TTL;
- un `catalog.json` invalid declanșează fallback la scanarea folderelor;
- eroarea tehnică este păstrată în diagnostic;
- scanările și cererile ViewModel pot fi anulate fără publicarea unui rezultat vechi;
- schimbarea bibliotecii validează noul folder înainte de înlocuirea URI-ului vechi.

### 2. Progres fail-operational

- starea din memorie este sursa runtime pentru UI;
- Room este persistența principală;
- checkpoint-urile critice sunt salvate și într-un fișier atomic;
- backup-ul poate repopula Room;
- salvările vechi nu suprascriu progresul mai nou;
- resetarea scrie un tombstone pentru a respinge checkpoint-uri întârziate;
- erorile Room și backup sunt izolate și nu blochează UI-ul;
- dacă obținerea Room/DAO eșuează imediat, este disponibil un DAO în memorie.

### 3. Setări recuperabile

DataStore folosește handler de corupere și valori implicite. Modul „audio original” este reprezentat explicit prin valoarea goală și nu este transformat accidental în română.

### 4. Player rezistent la lifecycle

- Android 7+ inițializează playerul în `onStart` și îl eliberează în `onStop`;
- Android 6 folosește `onResume` / `onPause`;
- episodul, poziția, modul de redare și countdown-ul sunt restaurate după recreare;
- checkpoint-urile critice rulează în scope-ul aplicației;
- erorile și timeout-ul de buffering declanșează checkpoint;
- finalizarea episodului este persistată independent de lifecycle-ul Activity.

### 5. Library Builder fail-closed la rezultat final

- lock exclusiv pentru destinație;
- recuperare a lockului considerat abandonat;
- copiere și JSON scrise atomic la nivel de fișier;
- `fsync` înainte de publicare;
- fingerprint al sursei și versiunea Builderului;
- validarea ID-urilor și fișierelor catalogului;
- în caz de erori, catalogul anterior este restaurat implicit;
- catalog parțial numai prin `--publish-partial`.

### 6. Validare repetabilă

`tools/validate_repo.py` verifică offline XML-urile, manifestul, politica fără securitate, Builderul, lock-ul și versiunea. Cu `--android` rulează și testele, lint și APK-ul debug.

## Limitări cunoscute

### Room fallback

Room poate deschide baza la prima interogare. `InMemoryPlaybackProgressDao` este selectat numai dacă obținerea bazei/DAO aruncă imediat. Pentru erori apărute ulterior, continuitatea este asigurată de StateFlow și backup, dar DAO-ul defect nu este înlocuit automat.

### Progres între biblioteci

Progresul nu are încă namespace `libraryId`. Prune-ul după catalogul activ poate elimina istoricul bibliotecii anterioare, inclusiv după selectarea unui folder gol. Aceasta este constatare P1 și este planificată în `docs/ROADMAP.md`.

### Publicarea Builderului

Nucleul legacy poate scrie candidatul `catalog.json` în destinație înainte ca wrapperul 2.1 să finalizeze validarea și eventuala restaurare. Rezultatul final este fail-closed, dar nu există încă izolare completă față de un cititor concurent.

### Lock Builder

Lock-ul este considerat stale după șase ore și nu are heartbeat/token de proprietate. Un build legitim foarte lung poate permite concurență accidentală.

### Build și hardware

Nu sunt încă confirmate:

- buildul Android complet;
- schema Room generată;
- R8/lint release;
- decoderul hardware;
- microSD;
- Device Owner Samsung;
- reboot și redare de durată.

## Nivelul de garanție

Menene 2.1 oferă **reziliență proiectată și validare statică**, nu încă o garanție de producție. Garanția operațională apare numai după trecerea porților din `docs/VALIDATION_PLAN.md` și închiderea constatărilor P1 din `docs/ROADMAP.md`.
