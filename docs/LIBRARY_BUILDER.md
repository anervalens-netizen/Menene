# Mehene Library Builder

## Scop

Builderul mută munca grea de pe tabletă pe PC/server:

- inspecție `ffprobe`;
- conversie selectivă și incrementală;
- selecție audio preferată;
- coperți optimizate și miniaturi;
- catalog validat;
- eliminarea deterministă a subtitrărilor `.srt`/`.vtt` din output;
- raport complet și fingerprint al sursei.

`mehene_library.py` conține procesarea media legacy. Intrarea obligatorie este `mehene_builder.py`, care adaugă lock, validare și restaurarea catalogului anterior la eroare.

## Utilizare

```bash
python3 tools/mehene_builder.py SOURCE DESTINATION --audio-language ron
# profil TV, separat de profilul tabletă implicit
python3 tools/mehene_builder.py SOURCE DESTINATION --audio-language ron --media-profile tv
```

Linux/macOS:

```bash
./tools/convert-library.sh SOURCE DESTINATION --audio-language ron
```

Windows:

```powershell
.\tools\convert-library.ps1 -Source "D:\Desene" -Destination "E:\Mehene" -AudioLanguage ron
```

Sursa și destinația trebuie să fie diferite, iar destinația nu poate fi în interiorul sursei.

## Reguli media

Un fișier este copiat fără recodare dacă este:

- MP4/M4V;
- H.264;
- maximum 1280×720;
- maximum 30 fps;
- audio AAC sau fără audio.

Restul este convertit în H.264 Main Level 3.1, AAC stereo 128 kbps, `yuv420p` și `faststart`. Fișierele deja pregătite și mai noi decât sursa sunt validate și reutilizate.

Profilul `tablet` rămâne implicit și păstrează limita existentă de 1280×720/30. Profilul explicit `tv` acceptă H.264 până la 1920×1080/60. Pentru o sursă MKV H.264 care respectă această limită, Builderul mută fluxul video fără recodare (`-c:v copy`) într-un MP4 `faststart` și convertește numai pista audio selectată în AAC stereo 192 kbps. Sursele care depășesc limita video sunt transcodate în profilul TV; nicio pistă subtitle nu este mapată.

Subtitrările nu sunt publicate. Sidecar-urile `.srt`/`.vtt` din sursă sunt numărate în `subtitlesIgnored`, toate copiile SRT/VTT vechi sunt eliminate recursiv din destinație, iar pistele subtitle interne sunt omise prin maparea explicită video/audio. Numărul lor apare în `subtitleStreamsDropped`. Câmpul `subtitle` din catalog rămâne `null`; validatorul refuză fail-closed orice valoare nenulă.

## Stabilitate implementată

- `.mehene-build.lock` împiedică în mod normal două procese să modifice aceeași destinație;
- lock-urile mai vechi de șase ore sunt considerate abandonate;
- copierea fișierelor compatibile și JSON-urile folosesc înlocuire atomică la nivel de fișier;
- datele sunt sincronizate pe disc înainte de înlocuire;
- fingerprint-ul sursei este inclus în catalog și raport;
- ID-urile duplicate, căile ieșite din destinație și fișierele absente opresc rezultatul final;
- dacă un episod eșuează, `catalog.json` anterior este restaurat implicit;
- `mehene-report.json` explică erorile;
- `--publish-partial` permite explicit publicarea episoadelor reușite, dar nu este recomandat pentru biblioteca zilnică;
- PowerShell oferă aceeași opțiune prin `-PublishPartial`.

## Constrângeri operaționale 2.3

Până la implementarea stagingului din roadmap:

1. Nu rula Builderul direct pe o bibliotecă pe care tableta o citește simultan.
2. Construiește într-un folder offline/staging, verifică raportul, apoi sincronizează biblioteca pe microSD.
3. Nu porni două procese pentru aceeași destinație.
4. Un build poate dura peste șase ore pentru biblioteci foarte mari; lock-ul actual nu are heartbeat și poate fi considerat stale. Evită relansarea concurentă.
5. Nucleul legacy scrie candidatul `catalog.json` înainte ca wrapperul să finalizeze validarea. Catalogul este restaurat la finalul unui eșec, dar poate exista o fereastră tranzitorie pentru un cititor concurent.
6. Fingerprint-ul este calculat înainte de procesare. Nu modifica sursa pe durata buildului.
7. Fișierele șterse din sursă nu sunt eliminate automat din destinație; catalogul nu le mai referă, dar spațiul rămâne ocupat.
8. Nu șterge manual catalogul anterior până când noua generație nu a fost verificată.
9. Purgarea SRT/VTT din destinația staging este directă și nu se rollback-uiește dacă o eroare ulterioară restaurează catalogul anterior; de aceea destinația trebuie să fie staging offline, nu biblioteca activă a tabletei.

Aceste limitări sunt constatări P1/P2 în `docs/FINAL_AUDIT.md` și au remediere planificată în `docs/ROADMAP.md`.

## Flux recomandat acum

```text
SOURCE
  ↓
DESTINATION_STAGING
  ↓ Builder
mehene-report.json fără erori
  ↓ verificare catalog + mostre video
MICROSD/Mehene
  ↓ rescanare în administrare
TABLETĂ
```

Pentru copierea staging → microSD folosește o unealtă care poate verifica rezultatul și păstra vechea bibliotecă până la confirmare.

## Sezoane

Sunt recunoscute foldere precum `Season 01`, `Sezonul 01` și `S01`. Dacă nu există foldere de sezon, episoadele sunt atribuite sezonului 1.

## Rezultate

- `catalog.json` — folosit de aplicație;
- `mehene-report.json` — erori, avertismente, versiunea Builderului, fingerprint, profilul media, `videoStreamCopied`, `subtitleStreamsDropped` și statistici;
- coperți și miniaturi WebP optimizate.

## Roadmap Builder

Versiunea următoare trebuie să introducă:

- staging pe generații;
- publicare atomică a generației complete;
- heartbeat și token de lock;
- fingerprint înainte și după build;
- manifest al fișierelor și `--prune-orphans --dry-run`;
- eliminarea nucleului legacy/monkey-patching;
- rollback direct la generația anterioară.

Builderul nu descarcă automat conținut și nu ocolește protecții DRM.
