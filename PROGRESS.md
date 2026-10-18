# Progress

## Where things stand

- **Done:** the skeleton (session 1): the Fabric and NeoForge jars from one codebase, the settings
  file, and the height zones (`LOW`, `HIGH`, `SUMMIT`) every mountain feature will use. Unit tests for
  the bands; an in-game zone check on both loaders; CI runs the build and both in-game tests on their
  own, with Tectonic and with Terralith.
- **Broken:** nothing known.
- **Next:** session 2: wind, cold and whiteouts, with tests. Then the clothes and the gear (session 3),
  ropes, cairns and the tent (4), the Stone shrine (5), the Cairn and the 0.1.0 release (6). Gabby's
  `icon.ase` replaces the draft icon before release.

## Sessions

| Date | Session | What happened |
|---|---|---|
| 2026-10-18 | Skeleton and zones | The repo, from Beyond the Dragon's layout (common / fabric / neoforge, the `Platform` service) with the NeoForge in-game test runner from the multiloader template. `Config` writes `config/summiteer.properties` (treeline 70 and summits 120 blocks above sea level). `Zones.at` gives `LOW`, `HIGH` (a `#minecraft:is_mountain` or `#c:is_mountain` biome above the treeline) or `SUMMIT` (summit height, any biome), Overworld only. `ZonesTest`: 5 unit tests pass. Both jars build; both in-game tests compile; a NeoForge dedicated server loads the jar with 0 problem lines, alone and with Tectonic 3.0.31 and Terralith 2.6.5. Not yet run: the in-game tests themselves (they open a game window) and their proof run with the mountain check switched off. Draft ice axe icon in `assets/summiteer/icon.png`. |
