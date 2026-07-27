# Arhitectură

## Obiective

- funcționare 100% offline;
- consum redus pe 2 GB RAM și procesor quad-core;
- interfață utilizabilă de un copil mic;
- administrare ascunsă și controlată de părinte;
- fără bază de date, conturi, server sau sincronizare.

## Decizii

### Android nativ, Kotlin și Views

Nu se folosesc WebView, React Native, Flutter, Jetpack Compose sau animații Lottie. UI-ul este construit cu XML, RecyclerView și drawables native pentru memorie și pornire reduse.

### Catalog derivat din foldere

`LibraryRepository` scanează folderul acordat prin Storage Access Framework. Nu este necesar un fișier JSON și nici acces general la stocare. Modificarea conținutului înseamnă doar copierea sau ștergerea fișierelor.

### Player

AndroidX Media3 ExoPlayer redă URI-uri locale. Interfața standard este dezactivată și înlocuită cu:

- X pentru revenire la episoade;
- volum +/−;
- atingere pe video pentru pauză/redare;
- reluare automată de la poziția salvată.

### Kiosk

`KioskController` are două niveluri:

1. fullscreen immersive, disponibil imediat;
2. Lock Task complet, când aplicația este Device Owner.

Modul complet allowlistează numai Mehene și o setează ca aplicație Home persistentă.

### Memorie

Imaginile sunt decodate la dimensiune redusă, în RGB_565, într-un cache limitat la aproximativ 1/12 din heap. Scanarea folderelor și decodarea imaginilor rulează în thread-uri de fundal.
