# Summiteer

A Fabric and NeoForge mod for Minecraft 26.3 (mod ID `summiteer`): mountains worth climbing. Above
the treeline the weather fights back (wind, cold, whiteouts, avalanches), players gear up like real
mountaineers (clothes, gloves, crampons, an ice axe, ropes and anchors, goggles, trail cairns, a base
camp tent), and three mountain temples each hold a boss guarding the key to the next. Vanilla-like:
vanilla blocks, vanilla-style mobs, no new models.
`PROGRESS.md` says what's done, what's broken and what's next, with a row per session: read it
first, and add this session's row before ending.

## The design, in short

- **Zones** (`zones/`): every mountain feature asks `Zones.at(level, pos)`: `LOW`, `HIGH` (a mountain
  biome above the treeline) or `SUMMIT` (summit height, any biome). Heights are measured from the
  world's sea level and are settings; "mountain" is `#minecraft:is_mountain` or `#c:is_mountain`.
  **Never use a fixed Y**: terrain mods (Tectonic, Terralith, Amplified) make mountains much taller.
- **Weather** (`weather/`, `client/Whiteout`): one look per player per second (zone, sheltered,
  snowing, fire near); the server decides wind and cold, the client draws the whiteout fog from the
  synced weather and biome (no packets). Nothing scans the world. `Wind` and `Cold` hold the rules,
  free of the game, with unit tests; `Weather` applies them. Gusts are announced 2 s ahead (whoosh
  and snowflakes from upwind), crouching takes three quarters off, a roof stops them; the wind
  turns once every four days, the same for everyone. Cold: above the treeline in a snowstorm (not
  under a roof), or on a summit at night; a fire or lit campfire within 3 blocks stops it.
  Whiteout: summit plus snowstorm, fades over 3 s, only ever thickens the fog
  (`mixin/client/FogRendererMixin`).
- **Cold**: vanilla's freezing ticks (5 a second net, so about 28 s to fully frozen), held off by
  **warmth** (planned, `Cold.perSecond(warmth)`: one point takes one off): each piece of the mountain
  clothes (beanie, parka, snow pants, wool socks; dyeable wool armour, leather-level protection) and
  mittens add some; campfires and fire warm you back up.
- **Accessory slots** (decided 2026-10-18): Summiteer **requires** a slot mod for the clothing
  that isn't armour: Curios on NeoForge, Trinkets Updated on Fabric (neither is bundled). Mittens and
  gloves go in its hands slot. If one falls behind on a version we need, we make an unofficial port
  of it rather than drop the requirement. No Forge build (too few Forge downloads, and Curios has no
  Forge build on 26.x).
- **Gear that straps on** (planned): crampons onto any boots, goggles onto any helmet, the gale
  cloak onto any chestplate. A marker on the item, not a new slot, so modded armour works too.
- **Temples and bosses** (planned): three shrines placed on real summits by Summiteer's own
  structure type (it samples the ground, like Beyond the Dragon's End ruins); each boss is woken by
  ringing the shrine's vanilla bell, has one simple rule (Aether-style) and gets faster below a third
  of its health. Stone shrine: the Cairn (stunned when it rolls into a wall). Frost shrine: the Old Ram
  (its ice breaks only to fire). Summit shrine: the Gale (can only be hit when it lands). Each drops the
  key to the next shrine's locked door.

## Layout

One codebase, two jars (the same layout as Beyond the Dragon):

- `common/`: everything that doesn't care which loader it runs on. `Summiteer.init()` is the one
  entrypoint; `Config` is the settings file (`config/summiteer.properties`); `platform/` is the tiny
  loader abstraction (`Platform`, found through `ServiceLoader`); `zones/` the height bands. All data
  files live in `common/src/main/resources`. Never import Fabric or NeoForge here; `./gradlew :common:build`
  fails if you do.
- `fabric/` and `neoforge/`: the entrypoints and a `Platform` each. Their `build.gradle` compiles
  `common`'s sources straight into their jar.
- `fabric/src/gametest/`: the Fabric client game test (`SummiteerClientTest`).
- `neoforge/src/clienttest/`: the NeoForge in-game test: `ClientTest` runs steps one tick at a time
  and crashes the game on a failed check; `SummiteerClientTest` holds the same checks as the Fabric one.
- `art/`: Gabby's own pixel art (Aseprite). The icon is a draft until her `icon.ase` replaces it.

Registering anything (items, blocks, entities) goes through `Services.PLATFORM.register(...)`,
never `Registry.register` directly: NeoForge only accepts registrations in its registry event, so
its `Platform` queues them and the result is a `Supplier`. The same goes for creative tabs.

## Build and test

- `./gradlew build`: jars in `fabric/build/libs/` and `neoforge/build/libs/`, plus the unit tests
  in `common/src/test` (the height bands).
- `./gradlew :fabric:runClientGameTest`: the settings file and the in-game checks in a real game
  window (a minute or two). `./gradlew :neoforge:runClientTest`: the same on the NeoForge jar.
  Add `-PtestTectonic` or `-PtestTerralith` to either to play it with that mod installed (fetched
  from Modrinth's maven by version id in `gradle.properties`, never bundled); CI runs all three.
- Zone check: the test paints stony peaks and plains beside the player with `/fillbiome` and asks
  the zone at heights measured from the world's own sea level, plus the Nether (always `LOW`).

## Gotchas

- **The common sources are compiled twice**, once per loader. A class that compiles on Fabric but
  not NeoForge (or the reverse) is a common class that leaked a loader import.
- **Versions**: freezing, powder snow, goats, goat horns and display entities exist on 26.x, 1.21.1
  and 1.20.1; the mace and the breeze only from 1.21, so the Gale needs its own look on 1.20.1 and
  the Cairn must never need a mace.
- Vanilla names changed a lot in 26.x: check the merged jar rather than older docs. Data files too:
  copy the shape from a vanilla file in the merged jar (`data/minecraft/...`) before writing a new
  kind of data file.

## Gabby's mods: who does what (the same in every repo)

One job per mod, decided 2026-10-15, so nothing is done twice and each repo tells the same story:

- **Cozy Core** (cozy-core): the builder rules, each an ordinary game rule: builds stay built (mob
  explosions, endermen, fire), no phantoms, keep your hotbar on death, the Hearth Lantern, cheap
  wood in the stonecutter, re-dyeing and builder trades. Fabric and NeoForge jars, plus a plain
  data pack of the recipes.
- **Espresso Flavoured Core** (espresso-flavoured): the café: coffee you grow and brew, mugs,
  baristas, Bean Coins, the café charms, and Drowsy (coffee's reason to exist). Longer days stay a
  Core setting for now. The survival rules Cozy Core now covers left Core in 0.4.0 (its PLAN-0.4.0.md).
- **Spawn Gazebo** (spawn-gazebo): the gazebo at the world spawn and its starter chest.
- **Nightfall Clock** (nightfall-clock): the clock above the hotbar and the dusk warning.
- **Pocket List** (pocket-list): the gathering checklist.
- **Not Now, Shield** (not-now-shield): the shield drawn lower in first person; on 26.x also
  tools that change blocks while a shield is in the off-hand.
- **Beyond the Dragon** (beyond-the-dragon): the end-game after the Ender Dragon: ruins at the
  edge of the End, a dragon that comes back harder, and the aphelite metal.
- **Summiteer** (summiteer): mountains worth climbing: wind, cold, whiteouts and avalanches up
  high, mountaineering gear and clothes, and three mountain temples with a boss each.
- **Espresso Flavoured** (espresso-flavoured-pack): the modpack that puts them together. Since
  pack 0.5.0 (2026-10-15) it carries all five: Cozy Core, Espresso Flavoured Core (back as the café
  once it stopped doing Cozy Core's jobs), Spawn Gazebo, Nightfall Clock and Pocket List.
- **Cozy Core Pack** (cozy-core-pack): the builder-friendly modpack around Cozy Core, with Spawn
  Gazebo, Nightfall Clock, Pocket List and Not Now, Shield.

A new feature goes in the mod whose job it is. If none fits, ask Gabby before adding it anywhere.

## Working on Gabby's mods (the same in every repo)

Gabby makes these mods with help from Claude. Several Claude sessions work on them: one on her
computer and others in the cloud. These rules keep their work fitting together.

**One session per repo at a time.**
- A cloud session works on a `claude/...` branch and never pushes to `main`. Gabby's local
  session reviews the branch, runs its tests and merges it as one commit.
- Start from the latest `main` (`git pull`), and say in the branch's last commit or a short note
  what is finished and what isn't.

**Commits are made one at a time, by hand.** Never commit or push from a chained script. Run
the test, read its result (and the screenshot, for anything visual), then commit that one
branch, then push it. A cherry-pick that conflicts stops the work until it is resolved by hand.
A worldgen or balance change is tested on every terrain in the branch's matrix before it goes
to any other branch. (Learned 2026-10-14, when scripted chains committed a conflicted
cherry-pick, put one branch's files on another, and sent an untested ore change to all three.)
For a Gradle game test use a game watcher that reports a verdict (in the world, error screen, crashed or hung), not a wait loop.

**Commits.**
- Author and committer are always `gabmaybelle <211247938+gabmaybelle@users.noreply.github.com>`:
  `git -c user.name=gabmaybelle -c user.email=211247938+gabmaybelle@users.noreply.github.com commit ...`.
  Never a personal email, and never `Claude <noreply@anthropic.com>` as the author. Claude goes in
  a `Co-Authored-By:` line at the end of the message.
- Ask Gabby before committing or pushing when she's around. Finish the build and the tests first.
- Commit messages say what changed for players, in plain sentences.

**Privacy.** These repos are public. Don't add personal email addresses, real names other than
"Gabby", or anything about her other private projects and modpacks: not in code, docs, commits
or test names. Check the diff for these before every push.

**Tests.**
- `./gradlew build` and `./gradlew :fabric:runClientGameTest` must pass before a commit. The game
  test opens a real game window for a minute or two.
- A new or changed check should fail with the feature switched off: run it that way once, then
  put the code back. A check that compares against the setting it tests proves nothing.
- A data change (worldgen, loot, a template) gets the same proof: run the part of the test that
  sees it, on every terrain in the branch's matrix, and quote the numbers in the progress row.
- On Linux without a screen, run game tests under `xvfb-run -a`. 26.3 also needs
  `SDL_VIDEO_FORCE_EGL=1` with `libegl1 libegl-mesa0` installed, or the game hangs at startup.

**Writing.**
- README and CHANGELOG are for players: plain, friendly, no jargon. New player-facing changes
  go in the CHANGELOG's newest section.
- Keep the credit line: "made by Gabby with help from Claude, an AI assistant".
- Gabby publishes on Modrinth herself. Don't upload anything there.
**Minecraft.** Fabric and NeoForge (Mojang names; Loom 1.18.4, ModDevGradle 2.0, Java 25).
`main` is the newest Minecraft (26.3). Ports to 1.21.1 (Fabric + NeoForge) and 1.20.1 (Fabric
only: no Forge build) get a branch each once all three temples are out; behaviour changes land on `main` first.
Vanilla names changed a lot in 26.x: check the merged jar rather than older docs (for example
`iron_chain`, `pink_petals[flower_amount=N]`, timelines instead of `time query daytime`).
