# Biblioteca Mehene

## Format recomandat

- container: MP4;
- video: H.264 Main, maximum 1280×720, 30 fps;
- audio: AAC stereo, 48 kHz;
- miniatură: WebP/JPG/PNG;
- subtitrare: SRT sau VTT cu același nume de bază.

## Structură

```text
Serial/
├── cover.webp
├── Season 01/
│   ├── 01 - Titlu.mp4
│   ├── 01 - Titlu.webp
│   └── 01 - Titlu.srt
└── Season 02/
```

În lipsa unui catalog generat, aplicația detectează numai MP4/M4V. MKV, AVI, MOV și WebM sunt raportate în administrare și trebuie convertite.
