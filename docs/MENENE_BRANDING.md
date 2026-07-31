# Menene branding — 2026-07-31

## User-facing result

Launcher label, home title, Menene TV, adult administration, version label, kiosk description, splash icon and adaptive/round launcher foreground now display Menene. Main, series, player and adult-recovery surfaces use the new warm visual system. The application identity remains stable for update continuity.

## Imagegen provenance

The source visuals were created with the built-in imagegen workflow and supplied as the canonical Manager handoff in /home/andrei/.buzz/OUTBOX/MENENE_VISUALS/. The source files were read-only and their hashes were checked before copy.

Prompt set used by the canonical handoff:

- Logo: Original simple preschool emblem for Menene; rounded yellow toy vehicle, teal accent, navy wheels; flat friendly geometric illustration; no text, no characters, no watermark; transparent-ready chroma background.
- Adventure: Original warm preschool landscape for Menene; rolling hills, winding road, rounded clouds and simple learning motifs; calm negative space for white UI text; no characters, no text, no logos.
- Evening: Original calm low-detail evening landscape for the Menene player and adult recovery; deep blue sky, soft moon, rounded hills, dark lower band for controls/subtitles; no characters, no text, no logos.

Canonical source hashes:

- MENENE_LOGO_TRANSPARENT_V1.png: a0f650540a08affc5dd0b0c6b88234324b0b59d24f5fe380bc68fe3183127230, 1254x1254 RGBA.
- MENENE_BACKGROUND_ADVENTURE_V1_CANONICAL.png: 64274e72e5685b432ae86d3cd819067fddb8c478625095953dfecb0116b83721, 1568x1003 RGB.
- MENENE_BACKGROUND_EVENING_V1_CANONICAL.png: b21e557bb80f68dde4d3c3a15391ee6bf27896cc6693e4150573ce274a7e289a, 1586x992 RGB.

## Android derivatives

- app/src/main/res/drawable-nodpi/menene_logo.png is copied unchanged from the canonical RGBA logo; final SHA-256 is a0f650540a08affc5dd0b0c6b88234324b0b59d24f5fe380bc68fe3183127230.
- menene_bg_main.webp is Adventure resized/cropped to 1280x800 and encoded WebP quality 82; SHA-256 d19c071456388ebd55d48e4aa087be709c518f652781c05c1346c3c92652ba91.
- menene_bg_player.webp is Evening resized/cropped to 1280x800 and encoded WebP quality 82; SHA-256 d921444305f4fbf61e9d41c3683dbb15001e9885e231e8f0214dfb7839782770.
- menene_bg_admin.webp is Evening darkened (brightness=-0.16, saturation=0.85), resized/cropped to 1280x800 and encoded WebP quality 82; SHA-256 ce8830f7cd7e10d02964c11d07eb476202d9023298b3980bcb2c2db85f9c8b42.

No raster text, animation or extra overdraw was added. The adaptive and round icons use the same padded logo bitmap, while the old vector remains unused for rollback/reference.

## Intentional legacy identifiers

The following remain unchanged because they are package, component, storage, test or update-compatibility identifiers rather than visible branding: ro.mehene.app, MeheneApplication, MeheneHomeActivity, MeheneDeviceAdminReceiver, Theme.Mehene, ic_mehene_logo, mehene_* resource/color names, Room database identifiers, Builder filenames and the existing release application ID. No user-facing string retains “Mehene”.

## QA focus

The final visual gate must verify landscape rendering on SM-T585/API27, text/card contrast, clipping and safe-area behavior of the adaptive icon, adult recovery readability, and no perceptible performance regression.
