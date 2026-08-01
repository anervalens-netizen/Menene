# Menene TV

Client Samsung TV Web/Tizen pentru modelul 77S90C și server media local read-only.

## Arhitectură

- biblioteca canonică rămâne pe Dell în `/srv/menene/library/current`;
- `catalog.json` generat de Builder este baza de conținut, fără o bază duplicată;
- serverul expune health, catalog, artwork și media prin HTTP LAN cu byte ranges;
- televizorul păstrează în `localStorage` numai progresul de redare, indexat după ID-ul stabil al episodului;
- tableta rămâne independentă și continuă să folosească biblioteca de pe microSD.

## Rulare server în dezvoltare

```bash
python3 tv/server/menene_tv_server.py \
  --library /srv/menene/library/current \
  --bind 192.168.0.43 \
  --port 8765 \
  --allow-subnet 192.168.0.0/24
```

Verificare:

```bash
curl http://192.168.0.43:8765/api/v1/health
curl -H 'Range: bytes=0-1023' \
  http://192.168.0.43:8765/media/<cale-episod>
```

## Client Tizen

Țintă minimă: Tizen 7.0, aplicație 1920×1080. Clientul folosește AVPlay pe TV și HTML5 video în browser pentru QA. Toate funcțiile sunt accesibile din direcții, Select, Back și tastele media; volumul rămâne controlat de platformă.

`tv/app/config.js` conține adresa LAN a serverului. Pachetul WGT trebuie semnat cu un certificat Samsung/Tizen și instalat după activarea Developer Mode pe TV.

## Teste locale

```bash
/usr/bin/python3 -m unittest discover -s tv/server/tests -v
node --test tv/tests/*.test.js
node --check tv/app/js/core.js
node --check tv/app/js/player.js
node --check tv/app/js/app.js
```

## Referințe Samsung

- [platforme și upgrade OS](https://developer.samsung.com/smarttv/develop/specifications/general-specifications.html);
- [AVPlay și URI-uri HTTP](https://developer.samsung.com/smarttv/develop/guides/multimedia/media-playback/using-avplay.html);
- [telecomanda Samsung](https://developer.samsung.com/smarttv/develop/guides/user-interaction/remote-control.html);
- [conectarea unui TV în Developer Mode](https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/tv-device.html);
- [limita de 5 MB pentru Web Storage](https://developer.samsung.com/smarttv/develop/guides/data-handling/using-web-storage.html).
