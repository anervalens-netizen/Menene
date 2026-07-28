# Formatul bibliotecii Mehene

Aplicația citește un folder ales prin Android Storage Access Framework, din memoria internă sau microSD.

## Structură

```text
Mehene/
├── Bluey/
│   ├── cover.jpg
│   ├── Episod 01 - Titlu.mp4
│   ├── Episod 01 - Titlu.jpg
│   └── Episod 02 - Titlu.mp4
└── Mașini de curse/
    ├── poster.png
    ├── 01 - Start.mp4
    └── 02 - Cursa.mp4
```

Reguli:

- fiecare subfolder cu minimum un MP4/M4V devine serial;
- episoadele sunt sortate natural: `1, 2, 3, 10`;
- coperta serialului: `cover`, `poster`, `folder` sau `serial`, JPG/PNG/WebP;
- imaginea cu același nume de bază ca episodul devine miniatură;
- formate publicate în catalog: MP4 și M4V;
- MKV, WebM, AVI și MOV sunt ignorate și raportate în diagnostic;
- profil recomandat: H.264 Main Level 3.1, yuv420p, AAC stereo, maximum 1280×720 și maximum 30 fps.

Limitarea catalogului la MP4/M4V evită situația în care un container este detectat, dar codecul său nu poate fi decodat hardware de tableta veche.

## Conversie automată

Windows:

```powershell
.\tools\convert-library.ps1 -Source "D:\Desene originale" -Destination "D:\Mehene"
```

Linux/macOS:

```bash
./tools/convert-library.sh "/media/desene-originale" "/media/Mehene"
```

Scripturile:

- refuză o destinație aflată în interiorul sursei;
- nu recodează fișierele deja valide și mai noi decât sursa;
- convertesc printr-un fișier temporar;
- validează rezultatul cu `ffprobe`;
- nu măresc rezoluția surselor mici;
- raportează fișierele reușite, păstrate și eșuate.
