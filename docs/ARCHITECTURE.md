# Arhitectură Mehene 2.1

## Principii

- offline real;
- interfață simplă pentru copil;
- administrare separată, fără autentificare;
- performanță pe Samsung SM-T585 cu Android 8.1/API 27 și aproximativ 2 GB RAM;
- conținut pregătit pe PC/server, nu procesat greu pe tabletă;
- cât mai puține straturi, dar responsabilități clare;
- degradare controlată: aplicația trebuie să rămână utilizabilă când un strat local eșuează.

## Componente Android

```text
MeheneApplication / AppContainer
├── LibraryRepository
│   ├── Storage Access Framework
│   ├── CatalogJsonCodec + CatalogValidator
│   └── CatalogCacheStore / AtomicFile
├── ProgressRepository
│   ├── runtime StateFlow
│   ├── Room / SQLite
│   ├── ProgressBackupStore / AtomicFile
│   └── InMemoryPlaybackProgressDao, fallback best-effort
├── SettingsRepository
│   └── Preferences DataStore + corruption handler
└── KioskController
```

`AppContainer` deține un scope de aplicație pentru checkpoint-uri și operații care trebuie să continue după distrugerea unui Activity.

## UI

```text
MainActivity     ← MainViewModel
SeriesActivity   ← SeriesViewModel
AdminActivity    ← AdminViewModel
PlayerActivity   ← Media3 + repositories + PlaybackQueuePlanner
```

Activity-urile desenează starea și trimit acțiuni. Scanarea, progresul și alegerea următorului episod nu sunt implementate în adaptoare sau layouturi.

ViewModel-urile anulează operația anterioară și folosesc o generație de request pentru a nu publica rezultate asincrone vechi.

## Catalog

Ordinea surselor:

1. catalog valid în memorie;
2. cache intern valid pentru URI, fingerprint și TTL;
3. `catalog.json` valid din bibliotecă;
4. scanarea folderelor ca fallback.

Schimbarea folderului este tranzacțională la nivelul aplicației: noul folder este validat și scanat înainte ca vechea bibliotecă să fie înlocuită.

Cache-ul intern folosește `AtomicFile`, checksum SHA-256 și validare semantică. Pentru cataloage generate, fingerprint-ul este hash-ul textului `catalog.json`; pentru scanarea folderelor se aplică TTL.

### Limită actuală

Library Builder 2.1 restaurează catalogul anterior după o eroare, dar nucleul legacy scrie candidatul în destinație înainte de validarea finală. Refactorizarea pe generații/staging este prevăzută în roadmap.

## Progres

Sursa runtime pentru UI este `StateFlow` din `ProgressRepository`.

Persistența folosește:

- Room ca bază principală;
- backup JSON atomic pentru checkpoint-uri critice;
- tombstone persistent la resetarea progresului;
- DAO în memorie ca fallback best-effort dacă obținerea Room/DAO eșuează imediat.

Room păstrează:

- ID episod;
- poziție;
- durată;
- terminat/început;
- ultima redare.

### Limită actuală

Progresul nu este încă separat prin `libraryId`. Schimbarea catalogului declanșează prune pentru ID-urile absente. Roadmapul cere namespace pe bibliotecă și eliminarea ștergerii automate globale.

De asemenea, Room poate deschide baza de date la prima interogare. Prin urmare, DAO-ul în memorie nu este o garanție de comutare pentru orice corupere apărută la query-time; continuitatea reală este oferită de runtime state și backup.

## Player

Media3 folosește lifecycle diferențiat:

- Android 7+: inițializare în `onStart`, eliberare în `onStop`;
- Android 6: inițializare în `onResume`, eliberare în `onPause`.

Playerul aplică:

- limba audio preferată;
- subtitrări sidecar;
- buffering timeout;
- checkpoint periodic și critic;
- restaurarea sesiunii și countdownului;
- coadă pentru continuarea serialului și Mehene TV.

## Kiosk

Kiosk are două niveluri:

- immersive/screen pinning pentru test;
- Device Owner + Lock Task + Home alias pentru utilizarea definitivă.

Ieșirea temporară în Android nu dezactivează preferința kiosk; Lock Task se reactivează la revenire. Rezultatul exact depinde de firmware-ul Samsung și trebuie calificat pe dispozitiv.

## Library Builder

```text
Sursă media
  ↓ ffprobe / conversie / imagini / subtitrări
Destinație + catalog candidat
  ↓ validator fail-closed
Catalog activ sau restaurarea celui anterior
```

Builderul are lock, validare și scriere atomică pentru fișiere individuale. Roadmapul prevede staging complet, heartbeat pentru lock și publicarea pe generații.

## Ce nu se introduce

- server runtime;
- conturi;
- internet;
- analytics;
- Compose doar pentru modernizare cosmetică;
- dependency injection framework fără nevoie demonstrată;
- autentificare/PIN;
- criptare specială;
- microservicii sau module Gradle inutile.

## Documente autoritative

- `docs/FINAL_AUDIT.md` — verdict și constatări;
- `docs/ROADMAP.md` — ordinea dezvoltării;
- `docs/STABILITY.md` — garanții și degradare;
- `docs/VALIDATION_PLAN.md` — porți de acceptare;
- `docs/NO_SECURITY.md` — decizia fără securitate.
