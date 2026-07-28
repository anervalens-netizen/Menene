# Stabilitate Mehene 2.1

Această iterație tratează aplicația ca pe un aparat offline care trebuie să continue să funcționeze chiar dacă un strat local eșuează.

## Plan implementat

### 1. Catalog recuperabil

- `catalog.json` este validat structural și semantic înainte de utilizare;
- căile relative nesigure sau inexistente sunt respinse;
- cache-ul intern folosește `AtomicFile`, checksum SHA-256 și versiune de schemă;
- cache-ul catalogului generat este legat de fingerprint-ul exact al sursei;
- scanarea directă a folderelor are TTL și nu poate rămâne permanent stale;
- un `catalog.json` invalid nu blochează aplicația: se folosește scanarea folderelor și eroarea apare în diagnostic;
- scanările și cererile ViewModel pot fi anulate fără a publica rezultate vechi.

### 2. Progres fail-operational

- starea din memorie este sursa runtime pentru UI;
- Room rămâne persistența principală;
- checkpoint-urile critice sunt salvate și într-un fișier atomic de backup;
- backup-ul poate repopula Room după o problemă locală;
- salvările vechi nu pot suprascrie progresul mai nou;
- un eșec Room sau backup este izolat și nu blochează navigarea ori playerul.

### 3. Setări recuperabile

DataStore folosește handler de corupere și valori implicite. Modul „audio original” rămâne reprezentat explicit prin valoarea goală și nu este convertit accidental în română.

### 4. Player rezistent la lifecycle

- Android 7+ inițializează playerul în `onStart` și îl eliberează în `onStop`;
- Android 6 folosește `onResume` / `onPause`;
- episodul, poziția, modul de redare și countdown-ul auto-next sunt restaurate după recrearea activității;
- progresul critic este trimis într-un scope al aplicației înainte de eliberarea playerului;
- erorile și timeout-ul de buffering declanșează checkpoint;
- finalizarea episodului este persistată independent de lifecycle-ul Activity.

### 5. Library Builder sigur

- lock exclusiv împotriva a două builduri simultane;
- lock stale recuperabil;
- copiere și JSON publicate atomic;
- `fsync` înainte de publicare;
- fingerprint al sursei și versiunea Builderului în raport;
- validarea tuturor ID-urilor și fișierelor catalogului;
- în caz de erori, catalogul anterior rămâne activ implicit;
- publicarea parțială este posibilă numai prin `--publish-partial`.

### 6. Validare repetabilă

`tools/validate_repo.py` verifică offline XML-urile, manifestul, politica fără securitate, Builderul, lock-ul și versiunea. Cu `--android` rulează și testele, lint și APK-ul debug.

## Ce rămâne obligatoriu

Validarea statică nu poate înlocui testarea pe Samsung Tab A: decoder hardware, microSD, reboot, Device Owner, două ore de redare și bibliotecă mare. Lista completă este în `docs/VALIDATION_PLAN.md`.
