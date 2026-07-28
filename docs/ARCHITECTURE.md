# Arhitectură

## Obiective

- funcționare 100% offline;
- consum redus pe Android 9 și aproximativ 2 GB RAM;
- interfață simplă pentru copil mic;
- administrare discretă, fără autentificare;
- kiosk pentru prevenirea ieșirilor accidentale;
- fără server, bază de date, conturi sau sincronizare.

## Politica fără securitate

Mehene nu implementează PIN, parolă, autentificare, criptare proprie sau control parental securizat. Cinci atingeri pe siglă deschid direct meniul de administrare și reprezintă numai o convenție UI. Această decizie nu trebuie schimbată fără cererea explicită a proprietarului.

## Android nativ

Kotlin, Views/XML, RecyclerView și drawables native. Nu sunt folosite WebView, Compose, Flutter, React Native sau animații grele.

## Catalogul

`LibraryRepository` citește folderul acordat prin Storage Access Framework și returnează rezultate tipizate:

- succes;
- bibliotecă neconfigurată;
- permisiune pierdută;
- stocare indisponibilă;
- eroare neașteptată.

Numai MP4/M4V sunt publicate în catalog. Alte fișiere video sunt numărate în diagnosticul parental. O copertă de serial trebuie să se numească `cover`, `poster`, `folder` sau `serial`; nu se folosește arbitrar miniatura unui episod.

URI-ul de progres include URI-ul fișierului, dimensiunea și ultima modificare, astfel încât înlocuirea episodului să nu moștenească automat progresul versiunii anterioare.

## Player

Media3 ExoPlayer redă URI-urile locale și gestionează:

- reluarea poziției;
- salvare periodică la 10 secunde;
- diferența dintre pauza utilizatorului și pauza de lifecycle;
- buffering;
- retry după eroare;
- audio focus și deconectarea căștilor;
- stările nevăzut, început și terminat.

## Kiosk

Kiosk-ul are stări reale, nu un singur boolean vizual:

- dezactivat;
- fullscreen;
- screen pinning;
- Device Owner pregătit;
- Lock Task activ.

Rolul Home este declarat printr-un `activity-alias` dezactivat implicit și activat numai în Device Owner kiosk. Pentru schimbarea folderului, Lock Task este oprit temporar, DocumentsUI este deschis, apoi Mehene revine în kiosk.

În Device Owner sunt configurate allowlist-ul Lock Task, Home persistent și restricția `DISALLOW_CREATE_WINDOWS`.

## Memorie

Imaginile sunt decodate aproximativ la dimensiunea cardului, în RGB_565. Cache key include URI-ul și versiunea fișierului. Cererile simultane pentru aceeași imagine sunt deduplicate, iar holder-ele reciclate sunt detașate de requesturile vechi.
