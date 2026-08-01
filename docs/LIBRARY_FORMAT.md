# Biblioteca Menene

## Format recomandat

- container: MP4;
- video: H.264 Main, maximum 1280×720, 30 fps;
- audio: AAC stereo, 48 kHz;
- miniatură: WebP/JPG/PNG;
- subtitrări: nu se publică; sidecar-urile SRT/VTT sunt ignorate și eliminate din output.

## Structură

```text
Serial/
├── cover.webp
├── Season 01/
│   ├── 01 - Titlu.mp4
│   └── 01 - Titlu.webp
└── Season 02/
```

În lipsa unui catalog generat, aplicația detectează numai MP4/M4V. MKV, AVI, MOV și WebM sunt raportate în administrare și trebuie convertite.

Regula fără subtitrări este permanentă pentru Menene: produsul este destinat unui copil care încă nu citește.
