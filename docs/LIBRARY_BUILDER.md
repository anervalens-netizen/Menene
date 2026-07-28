# Mehene Library Builder

## Scop

Builderul mută munca grea de pe tabletă pe PC/server:

- inspecție `ffprobe`;
- conversie selectivă și incrementală;
- selecție audio română;
- coperți optimizate și miniaturi;
- catalog;
- raport complet.

## Utilizare

```bash
python3 tools/mehene_library.py SOURCE DESTINATION --audio-language ron
```

Sursa și destinația trebuie să fie diferite, iar destinația nu poate fi în interiorul sursei.

## Reguli

Un fișier este copiat fără recodare dacă este:

- MP4/M4V;
- H.264;
- maximum 1280×720;
- maximum 30 fps;
- audio AAC sau fără audio.

Restul este convertit în H.264 Main Level 3.1, AAC stereo 128 kbps, `yuv420p` și `faststart`. Fișierele deja pregătite și mai noi decât sursa sunt validate și reutilizate fără recodare.

## Sezoane

Sunt recunoscute foldere precum:

- `Season 01`;
- `Sezonul 01`;
- `S01`.

Dacă nu există foldere de sezon, episoadele sunt atribuite sezonului 1.

## Rezultate

- `catalog.json` — folosit de aplicație;
- `mehene-report.json` — erori, avertismente și statistici;
- coperți și miniaturi WebP optimizate;
- rezultate publicate atomic pentru catalog și raport.

Builderul nu descarcă automat conținut și nu ocolește protecții DRM.
