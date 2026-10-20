# Art

The pixel art for Summiteer, as Aseprite files.

- `icon.aseprite`: the mod's icon, chosen by Gabby on 2026-10-20. A 64x64 sprite in layers (sky, far
  peaks, peak, drift, blizzard, ice axe, frost, frame), exported at 512x512 with nearest-neighbour to
  `common/src/main/resources/assets/summiteer/icon.png`, which is also the listing icon.
  `icon-palette.gpl` holds its 30 colours (load it from Aseprite's palette menu).
- The item sprites in `common/src/main/resources/assets/summiteer/textures/item` are still drafts;
  Gabby's own replace them before the first release, and their Aseprite files go here.

When the icon changes, export it again to `icon.png` and use the same picture on every listing.
Never replace Gabby's files with generated art.
