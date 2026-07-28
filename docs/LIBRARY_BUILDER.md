# Mehene Library Builder

## Scop

Builderul mută munca grea de pe tabletă pe PC/server:

- inspecție `ffprobe`;
- conversie selectivă și incrementală;
- selecție audio preferată;
- coperți optimizate și miniaturi;
- catalog validat;
- raport complet și fingerprint al sursei.

`mehene_library.py` conține procesarea media. Intrarea recomandată este `mehene_builder.py`, care adaugă lock, validare și publicare fail-closed.

## Utilizare

```bash
python3 tools/mehene_builder.py SOURCE DESTINATION --audio-language ron
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

## Stabilitate

- `.mehene-build.lock` împiedică două procese să modifice aceeași destinație;
- lock-urile mai vechi de șase ore sunt considerate abandonate;
- copierea fișierelor compatibile și JSON-urile sunt publicate atomic;
- datele sunt sincronizate pe disc înainte de înlocuire;
- fingerprint-ul sursei este inclus în catalog și raport;
- ID-urile duplicate, căile ieșite din destinație și fișierele absente opresc publicarea;
- dacă un episod eșuează, `catalog.json` anterior rămâne activ implicit;
- `mehene-report.json` este actualizat și explică erorile;
- `--publish-partial` permite explicit publicarea episoadelor reușite, dar nu este recomandat pentru biblioteca folosită zilnic;
- PowerShell oferă aceeași opțiune prin `-PublishPartial`.

## Sezoane

Sunt recunoscute foldere precum `Season 01`, `Sezonul 01` și `S01`. Dacă nu există foldere de sezon, episoadele sunt atribuite sezonului 1.

## Rezultate

- `catalog.json` — folosit de aplicație;
- `mehene-report.json` — erori, avertismente, versiunea Builderului, fingerprint și statistici;
- coperți și miniaturi WebP optimizate.

Builderul nu descarcă automat conținut și nu ocolește protecții DRM.
