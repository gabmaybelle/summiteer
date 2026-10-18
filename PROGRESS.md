# Progress

## Where things stand

- **Done:** wind, cold and whiteouts with their settings (session 2, unit tested); the skeleton (session 1): the Fabric and NeoForge jars from one codebase, the settings
  file, and the height zones (`LOW`, `HIGH`, `SUMMIT`) every mountain feature will use. Unit tests for
  the bands; an in-game zone check on both loaders; CI runs the build and both in-game tests on their
  own, with Tectonic and with Terralith.
- **Broken:** nothing known.
- **Next:** the in-game checks for wind and cold (they need CI or a time Gabby is away), then the
  clothes and the gear (session 3),
  ropes, cairns and the tent (4), the Stone shrine (5), the Cairn and the 0.1.0 release (6). Gabby's
  `icon.ase` replaces the draft icon before release.

## Sessions

| Date | Session | What happened |
|---|---|---|
| 2026-10-18 | Skeleton and zones | The repo, from Beyond the Dragon's layout (common / fabric / neoforge, the `Platform` service) with the NeoForge in-game test runner from the multiloader template. `Config` writes `config/summiteer.properties` (treeline 70 and summits 120 blocks above sea level). `Zones.at` gives `LOW`, `HIGH` (a `#minecraft:is_mountain` or `#c:is_mountain` biome above the treeline) or `SUMMIT` (summit height, any biome), Overworld only. `ZonesTest`: 5 unit tests pass. Both jars build; both in-game tests compile; a NeoForge dedicated server loads the jar with 0 problem lines, alone and with Tectonic 3.0.31 and Terralith 2.6.5. CI (first push): the build and both in-game tests pass on their own (Fabric: settings file, mod loaded, 5 zone checks; NeoForge: every check). The Tectonic and Terralith runs first failed to start because both mods need the Lithostitched library, which Modrinth maven doesn't pull in by itself; it is now added, and all three runs pass on both loaders. Proof run (a throwaway branch with the mountain check returning false): every run on both loaders failed on exactly the check it should, "stony peaks above the treeline: expected HIGH, got LOW"; branch deleted. Draft ice axe icon in `assets/summiteer/icon.png`. |
| 2026-10-18 | Wind, cold and whiteouts | Gabby chose: gusts announced ahead and braced against by crouching; cold only in snowstorms up high or on summits at night; whiteout fog on summits in storms. Also decided: Curios (NeoForge) or Trinkets Updated (Fabric) is required for mittens and gloves, and there is no Forge build. `Wind`, `Cold` (rules, no game) and `Weather` (one look per player per second, gusts and freezing in between) on the server tick of both loaders; `Whiteout` plus a client mixin on `FogRenderer.setupFog`. Settings `wind`, `cold`, `whiteouts` (true/false). 16 unit tests pass (5 zones, 6 wind, 5 cold). Both jars build with the mixin. NeoForge and Fabric dedicated servers, alone and with Tectonic and Terralith: load with 0 problem lines. Not yet checked in a game: the push, the freezing and the fog (no in-game checks written yet). Open question for session 3: vanilla makes any leather armour piece freeze-proof, which would undercut warmth. |
