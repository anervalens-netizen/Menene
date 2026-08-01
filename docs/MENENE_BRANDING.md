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

## Fallback-uri ilustrate 2.2

Pentru serialele adăugate fără `cover.webp`, aplicația folosește determinist unul dintre trei tile-uri originale generate prin skillul built-in imagegen. Coperta reală din bibliotecă are întotdeauna prioritate.

Prompturile finale:

- Story: original preschool storybook adventure with cottage, winding path, castle towers and open storybook; soft 3D clay-and-paper, ages 3–5, teal/yellow/coral/lavender, square, no text/logos/franchise characters.
- Cars: three original toy-like cars on a colorful countryside road; soft rounded 3D clay, ages 3–5, square, no text/brands/recognizable movie characters.
- Space: original gentle twilight adventure with moon balloon, round rocket and woodland animals; soft 3D clay-and-felt, ages 3–5, square, no text/logos/franchise characters.

Derivatele Android sunt 512×512 WebP quality 82:

- `menene_series_story.webp`: `963f8ed181ef165d1046cbe1dc2dbe59d7367f60ad89641bb4bb6baf501378c9`
- `menene_series_cars.webp`: `94d2b72c1cae99a7b49c3e5f6c9680dff42402bb6041439861cf292654e25573`
- `menene_series_space.webp`: `357a73fdfd45f2ace363f03fb6c426c0123d6ede01efdad1f4c523f44d43c209`

## Sistem vizual cinematic 2.3

Home-ul 2.3 emulează ierarhia referinței Disney fără a copia brandul sau
personajele: fundal navy-magenta discret, un hero dominant, acțiune play
circulară și o bibliotecă compactă sub hero. Miniatura reală a episodului are
prioritate în hero, iar imaginea generată este fallback.

Asset-urile au fost generate prin skillul built-in imagegen, fără text,
logo-uri, mărci sau personaje recognoscibile:

- Hero: vale de poveste la apus, castel în depărtare și vehicul galben original
  pe treimea dreaptă; realism de film 3D, materiale și lumină cinematografică,
  spațiu întunecat în stânga pentru UI, format 16:9.
- Story: carte veche deschisă într-o pădure cu castel luminat de lună; realism
  de film 3D, hârtie, piele, piatră și vegetație credibile, format pătrat.
- Cars: trei vehicule originale pe un drum de coastă; metal, sticlă, cauciuc și
  peisaj realist stilizat, fără fețe sau designuri de marcă, format pătrat.
- Space: navă sferică originală deasupra unei planete albastre; metal, sticlă,
  lumină orbitală și profunzime cinematică, format pătrat.

Derivatele finale WebP quality 82:

- `menene_hero_adventure.webp`, 1280×720:
  `0fe8673ad477ebc6db9c19205edd86e1abc7a47291ef1d0c3e4842548108e789`
- `menene_series_story.webp`, 512×512:
  `13ece130dfad791decf886642c178ff82bc04c4278e8bb2539f25b6135a45ca9`
- `menene_series_cars.webp`, 512×512:
  `20850a4fde4a9d2aff8c3e07e3a410d20f39a5e1e6345f0e2ca913202c9d9925`
- `menene_series_space.webp`, 512×512:
  `f26d7d74a477baf8ad85cf4dac4cfbe43d383ba34219c8c0f5ad1a7d4badcc7d`
