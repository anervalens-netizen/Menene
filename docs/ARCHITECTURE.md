# Arhitectură Mehene 2.0

## Principii

- offline real;
- interfață simplă pentru copil;
- administrare separată, fără autentificare;
- performanță pe Android 9 și 2 GB RAM;
- conținut pregătit pe PC/server, nu procesat greu pe tabletă;
- cât mai puține straturi, dar responsabilități clare.

## Componente Android

```text
MeheneApplication / AppContainer
├── LibraryRepository
│   ├── Storage Access Framework
│   ├── CatalogJsonCodec
│   └── CatalogCacheStore
├── ProgressRepository
│   └── Room / SQLite
├── SettingsRepository
│   └── Preferences DataStore
└── KioskController
```

## UI

```text
MainActivity     ← MainViewModel
SeriesActivity   ← SeriesViewModel
AdminActivity    ← AdminViewModel
PlayerActivity   ← Media3 + repositories + PlaybackQueuePlanner
```

Activity-urile desenează starea și trimit acțiuni. Scanarea, progresul și alegerea următorului episod nu sunt implementate în adaptoare sau layouturi.

## Catalog

Ordinea surselor:

1. catalog valid în memorie;
2. cache intern valid pentru URI-ul bibliotecii;
3. `catalog.json` din bibliotecă;
4. scanarea folderelor ca fallback.

Schimbarea folderului este tranzacțională: noul folder este validat și scanat înainte ca vechea bibliotecă să fie înlocuită.

## Progres

Room păstrează:

- ID episod;
- poziție;
- durată;
- terminat/început;
- ultima redare.

Aceasta permite Continue Watching, Mehene TV și curățarea progresului pentru episoade dispărute.

## Player

Media3 este creat în `onStart()` și eliberat în `onStop()`, pentru a elibera decoderul hardware pe tableta veche. Playerul aplică limba audio preferată, subtitrări sidecar, buffering timeout și coadă de redare.

## Kiosk

Kiosk are două niveluri:

- immersive/screen pinning pentru test;
- Device Owner + Lock Task + Home alias pentru utilizarea definitivă.

Ieșirea temporară în Android nu dezactivează preferința kiosk; Lock Task se reactivează la revenire.

## Ce nu se introduce

- server runtime;
- conturi;
- internet;
- analytics;
- Compose;
- dependency injection framework;
- autentificare/PIN;
- criptare specială;
- microservicii sau module Gradle inutile.
