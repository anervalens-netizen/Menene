# Format `catalog.json`

Schema curentă: `1`.

```json
{
  "schemaVersion": 1,
  "generatedAtEpochMs": 1785250000000,
  "series": [
    {
      "id": "bluey-a1b2c3",
      "title": "Bluey",
      "path": "Bluey",
      "cover": "Bluey/cover.webp",
      "seasons": [
        {
          "number": 1,
          "title": "Sezonul 1",
          "episodes": [
            {
              "id": "bluey-s01e01-123abc",
              "number": 1,
              "sortOrder": 1,
              "title": "Episodul 01 - Pilot",
              "media": "Bluey/Season 01/Episodul 01.mp4",
              "artwork": "Bluey/Season 01/Episodul 01.webp",
              "subtitle": "Bluey/Season 01/Episodul 01.srt",
              "durationMs": 420000,
              "audioLanguage": "ron"
            }
          ]
        }
      ]
    }
  ]
}
```

Toate căile sunt relative la folderul selectat în Menene. ID-urile trebuie să fie stabile între reconstrucțiile bibliotecii, astfel încât progresul să fie păstrat.

Dacă `catalog.json` lipsește sau este invalid, aplicația scanează folderele. Scanarea fallback nu poate oferi toate metadatele Builderului.
