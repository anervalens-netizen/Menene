# Plan de validare înainte de instalarea pe server

## Validare rapidă locală

```bash
python3 tools/validate_repo.py
```

Pe un calculator cu JDK 17 și Android SDK 36:

```bash
python3 tools/validate_repo.py --android
```

## Build Android

- `clean test lintDebug assembleDebug`;
- toate testele instrumentate Room/cache/progres;
- build release semnat;
- instalare update peste versiunea anterioară cu aceeași cheie;
- pornire după upgrade fără pierderea progresului sau setărilor;
- verificarea schemei Room exportate.

## Recuperare și concurență

- cache catalog trunchiat, JSON invalid și checksum greșit;
- DataStore corupt: revenire la valori implicite;
- Room indisponibil: UI și progres din backup atomic;
- checkpoint vechi sosit după unul nou: progresul nou rămâne;
- oprirea procesului imediat după X, Home și finalul episodului;
- anularea unei scanări în timp ce începe alta;
- schimbarea bibliotecii cu folder nou invalid: biblioteca veche rămâne activă;
- `catalog.json` invalid: fallback la scanarea folderelor și diagnostic vizibil.

## Library Builder

- două rulări consecutive: a doua reutilizează rezultatele;
- două procese simultane: al doilea este blocat;
- lock stale este recuperat;
- fișier video corupt: catalogul anterior nu este înlocuit;
- `--publish-partial` publică numai când este solicitat explicit;
- întrerupere în timpul copierii, transcodingului și scrierii JSON;
- ID-uri stabile după rebuild;
- nicio cale din catalog nu poate ieși din folderul destinație.

## Tabletă Samsung

- cold start și warm start;
- reboot complet de 10 ori;
- redare continuă minimum 2 ore;
- Android 9: background/foreground repetat și ecran stins/aprins;
- pauză manuală păstrată după revenire;
- countdown auto-next restaurat după recreare;
- scoatere/reintroducere microSD;
- fișier corupt și codec incompatibil;
- subtitrări SRT și VTT;
- audio română, engleză și „original”;
- Home, Recents, notification shade și power menu;
- ieșire temporară și revenire kiosk;
- restart în Device Owner și pornire automată.

## Bibliotecă mare

- minimum 50 seriale;
- minimum 500 episoade;
- scroll rapid;
- pornire din cache;
- expirarea cache-ului fallback;
- rescanare completă și anulată;
- reconstruirea catalogului cu ID-uri stabile;
- măsurarea duratei pornirii și a consumului de memorie.

Produsul nu este considerat final până când această listă este verificată pe dispozitivul real.
