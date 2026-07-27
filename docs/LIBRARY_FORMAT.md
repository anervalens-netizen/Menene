# Formatul bibliotecii Mehene

Aplicația citește un folder ales o singură dată de părinte prin Android Storage Access Framework. Folderul poate fi în memoria internă sau pe cardul microSD.

## Structură

```text
Mehene/
├── Bluey/
│   ├── cover.jpg
│   ├── Episod 01 - Titlu.mp4
│   ├── Episod 01 - Titlu.jpg       # opțional: miniatură episod
│   └── Episod 02 - Titlu.mp4
├── Mașini de curse/
│   ├── poster.png
│   ├── 01 - Start.mp4
│   └── 02 - Cursa.mp4
└── Alt serial/
    └── Episod 1.mp4
```

Reguli:

- fiecare subfolder devine un serial;
- episoadele sunt sortate natural: `1, 2, 3, 10`, nu `1, 10, 2`;
- imaginea serialului poate fi `cover`, `poster`, `folder` sau `serial`, în format JPG, PNG ori WebP;
- o imagine cu același nume ca episodul devine miniatura lui;
- formate video detectate: MP4, M4V, MKV, WebM, AVI și MOV;
- format recomandat pentru tableta țintă: MP4, H.264, AAC, maximum 1280×720.

## Conversie automată

Windows PowerShell:

```powershell
.\tools\convert-library.ps1 -Source "D:\Desene originale" -Destination "D:\Mehene"
```

Linux/macOS:

```bash
./tools/convert-library.sh "/media/desene-originale" "/media/Mehene"
```

Scripturile păstrează subfolderele și imaginile, dar convertesc toate episoadele într-un profil ușor de decodat de Galaxy Tab A 8.0 (2019).
