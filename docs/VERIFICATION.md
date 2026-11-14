# Verification snapshots

## Version 1.0.1 — 2026-11-14

1.0.1 fixes cutscene shots that reached past the loaded world, glowing arcs seen through distant hills, storms announced over unloaded chunks starting at the bottom of the world, storm creatures that never left, rain killing a Fire Twister's blazes and End echoes bypassing Sodium's fast rendering path. Both branches were rebuilt:

| Build | GameTests | Scripted scenarios | Ordinary dedicated server |
|---|---|---|---|
| NeoForge 1.21.1 | 4/4 | 15/15 | Startup, spawn/info/clear commands and clean shutdown passed |
| Fabric 1.21.1 | 4/4 | 15/15 (production jar) | Not repeated for 1.0.1 |
| Forge 1.20.1 | 5/5 | 15/15 | Startup, spawn/info/clear commands and clean shutdown passed |
| Fabric 1.20.1 | 5/5 | 15/15 (production jar) | Not repeated for 1.0.1 |

The eleven earlier scenarios gained four: far cutscene shots inside the loaded world (`edge`, also run at render distance 6), arcs behind near, mid-distance and far blocks (`occlusion`), End echoes (`echo`) and a Nether storm's creatures arriving and leaving with it (`mobs`). After the final change, keeping the rain off round Fire Twisters, the full set was repeated on NeoForge 1.21.1 and the creature scenario twice on each other build.

On NeoForge 1.21.1 a player avatar from Figura and the arcs were also checked with a typical client pack: Sodium, Iris without a shader pack, Figura, Entity Culling, ImmediatelyFast, Entity Model Features, Entity Texture Features, 3D Skin Layers, Better Third Person and BadOptimizations. The avatar is drawn whole in every cutscene shot.

Not checked for 1.0.1: Iris or Oculus with a shader pack enabled, Distant Horizons during a cutscene, two real players and a real microphone.
## Release preparation — 2026-11-14

Horizons Unbound 1.0.0 was rebuilt on both source branches before the first GitHub releases. These checks were repeated for this publication:

| Build | Gradle build | GameTests | Ordinary dedicated server |
|---|---|---|---|
| NeoForge 1.21.1 | Passed | 4/4 | Startup, spawn/info/clear commands and clean shutdown passed |
| Fabric 1.21.1 | Passed | 4/4 | Startup, spawn/info/clear commands and clean shutdown passed |
| Forge 1.20.1 | Passed | 5/5 | Startup, spawn/info/clear commands and clean shutdown passed |
| Fabric 1.20.1 | Passed | 5/5 | Startup, spawn/info/clear commands and clean shutdown passed |

The source comparison confirmed identical public API declarations, optional-mod integration implementations and compile-time stand-ins; common and client configuration definitions match after normalizing the config library API. All 42 shared asset/translation files match byte for byte or as parsed translation entries, allowing the additional 1.20.1 Client section label. All ten advancements match after adapting the icon field name. Other Java and data differences were reviewed as [Minecraft port requirements](PARITY.md).

All four binary jars and all four source jars were inspected as ZIP archives. Each includes every shared source/class and resource expected for its branch, the final Fogmare Mod License 1.0 and no compile-time stand-ins. Mod classes target Java 21 on 1.21.1 and Java 17 on 1.20.1. Fabric binaries use remapped production names; Forge includes its refmap and nested MixinExtras dependency. Metadata versions and license identifiers were checked. Each Minecraft-version release includes a SHA-256 checksum file for its four jars.

The supplied license was synchronized to both source branches and the jars, with the contact-placeholder clause removed and the remaining section 7 clauses renumbered at the author's request. Local test worlds, logs, downloaded compatibility mods, draft mod-page copy and assistant instructions are excluded from publication.

The graphical and optional-mod checks below are earlier recorded checks, not a new rerun of every compatibility combination on this date. Their limits, including the absence of a two-real-player session and a real-microphone test, still apply.

## Earlier verification — 2026-11-13

Horizons Unbound 0.2.0 was checked on Windows with scripted clients, integrated worlds, ordinary dedicated servers and GameTest servers. Test inputs and captures remain local; third-party jars and shader packs are not part of this repository.

## Core checks

| Build | GameTests | Scripted scenarios | Save/reopen, placed blocks, death memory | Settings screen |
|---|---|---|---|---|
| NeoForge 1.21.1 | 4/4 | 11/11 | Passed, including an imported pre-port world | Passed |
| Fabric 1.21.1 | 4/4 | 11/11 | Passed | Passed through Mod Menu |
| Forge 1.20.1 | 5/5 | 11/11 | Passed | Passed in English and Russian |
| Fabric 1.20.1 | 5/5 | 11/11 | Passed | Passed through Mod Menu |

The additional 1.20.1 GameTest verifies tempering, retained vanilla equipment modifiers and repeated application. Placement tests use the normal player item-use path, then verify both the block and its protection marker after reopening. The 1.20.1 respawn check also preserves a distinct client view-distance value of 7.

The NeoForge visual reference and eleven-scenario comparison used 21.1.265. The final dependency is 21.1.266, checked with GameTests, dedicated-server commands, settings, saves and the compatibility runs below. Forge uses 47.4.29. Both Fabric builds use Loader 0.20.1.

Default 1.21.1 configuration files match the original bytes. Configuration values also match on 1.20.1; the older config library formats comments differently. Original textures, sounds and translations are retained. The 1.20.1 particle backport uses the original smoke color and vanilla sprites.

## Compatibility runs

Each set ran both `birth` and `baseline`. Shader runs check that the requested pack is actually active. All rows below passed with no Horizons Unbound render/effect/cutscene failure. Fabric production runs use the remapped distributed jar and the normal loader.

| Set | Tested mods | Shader pack | Runtime | Last storm frame FPS / baseline median FPS |
|---|---|---|---|---|
| neoforge-121-shaders | sodium mc1.21.1-0.6.13-neoforge, iris 1.8.8+1.21.1-neoforge | Complementary Reimagined r5.9.3 | Development | 41 / 34 |
| neoforge-121-photon | sodium mc1.21.1-0.6.13-neoforge, iris 1.8.8+1.21.1-neoforge | Photon 1.4 | Development | 30 / 39 |
| fabric-121-shaders | sodium mc1.21.1-0.6.13-fabric, iris 1.8.8+1.21.1-fabric, lithium mc1.21.1-0.15.4-fabric | Complementary Reimagined r5.9.3 | Development | 119 / 118 |
| fabric-121-photon | sodium mc1.21.1-0.6.13-fabric, iris 1.8.8+1.21.1-fabric, lithium mc1.21.1-0.15.4-fabric | Photon 1.4 | Development | 119 / 118 |
| forge-120-shaders | oculus 1.20.1-1.8.0, embeddium 0.3.31+mc1.20.1 | Complementary Reimagined r5.9.3 | Development | 33 / 36 |
| fabric-120-shaders | sodium mc1.20.1-0.5.11, iris 1.7.5+1.20.1 | Complementary Reimagined r5.9.3 | Development | 120 / 118 |
| neoforge-121-dh | distanthorizons 3.3.3-1.21.1 | None | Development | 73 / 95 |
| fabric-121-dh | distanthorizons 3.3.3-1.21.1, lithium mc1.21.1-0.15.4-fabric | None | Production jar | 84 / 89 |
| forge-120-dh | distanthorizons 3.3.3-1.20.1 | None | Development | 79 / 86 |
| fabric-120-dh | distanthorizons 3.3.3-1.20.1 | None | Production jar | 108 / 118 |
| fabric-121-clouds | better-clouds 1.13.11+1.21.1-fabric-release, yacl 3.8.2+1.21.1-fabric, lithium mc1.21.1-0.15.4-fabric | None | Production jar | 117 / 110 |
| forge-120-addons | open-parties-and-claims forge-1.20.1-0.33.0, kubejs 2001.6.5-build.26+forge, rhino 2001.2.3-build.10+forge, architectury-api 9.2.14+forge | None | Development | 115 / 110 |

The Forge addon set received `StormEvent.Born` in a KubeJS startup script. The claim test created a real Open Parties and Claims claim, confirmed that the storm fake player was denied, checked rejection by the terrain-effect protection path, and retained the stone block. The unclaimed control was allowed.

All test mod downloads came from Modrinth, with SHA-512 verification. Bundled libraries were taken from those jars. Fabric 1.20.1 uses the stable Iris 1.7.5 / Sodium 0.5.11 pair; Iris 1.7.6 requires a Sodium beta. Development-only library and refmap issues were handled by the test setup, without editing third-party jars.

## Scope and limits

FPS values are samples from the game counter, capped at 120, with a requested 1280×720 window and render distance 12. They include shader, terrain-generation and other-mod costs. Runs can select different default GPUs: for example Fabric 1.20.1 with Sodium selected the RTX 4060, while several native-loader runs used Intel UHD. Compare each storm run with its matching baseline; these numbers are not a controlled cross-loader benchmark.

Paired captures were inspected for geometry, layers, camera motion, acting, titles, fog and birth/death effects. They are not pixel-identical: storm UUIDs, wet pockets, particles and terrain-loading timing vary. The pre-existing spawn-height fallback for an unloaded center chunk is retained; it can also change a scripted storm's initial altitude.

Dedicated-server smoke tests cover startup, storm commands and clean shutdown. A separate-machine multiplayer session was not performed. Solas, other unlisted shader packs, combined DH-plus-shader sets, Fabric claim mods and NeoForge KubeJS scripts were not separately exercised. Native NeoForge KubeJS syntax was checked against its implementation; the Forge startup binding was exercised.

Final binary/source jars were checked for Java targets, metadata, licenses, service providers, original assets, data resources, Fabric access wideners and Forge refmap/JarJar contents. `dist/` exports include SHA-256 checksums.

## 1.0.0 checks

The 1.0.0 additions were checked with scripted clients and GameTests on all four builds (development clients for NeoForge and Forge, the production loader for Fabric):

| Check | Result |
|---|---|
| GameTests | NeoForge and Fabric 1.21.1: 4/4; Forge and Fabric 1.20.1: 5/5 |
| Arcs under the cloud cap behind stone walls and a roof (`occlusion`) | hidden by blocks, still seen through the cloud |
| Figura 0.1.6 avatar in the birth cutscene (`figura`) | drawn whole, follows the acting |
| JourneyMap 6.0.7/6.0.9, Xaero's Minimap 26.6.0 and World Map 1.47.0 (`maps`) | circle shown, removed after the storm; Xaero's Minimap alone takes several seconds |
| Simple Voice Chat 2.6.27 (`voice`, made-up frames) | plugin registered; loud speech inside a Deep Dark storm heard, quiet frames and speech outside not |
| Advancements (`advancements`) | each kind, all kinds, the Great Storm and the eye granted |
| Storm creatures and the gathering warning (`mobs`) | creatures appear on dry ground and leave with the storm; warning names the direction |
| Hide Lightning Flashes, Distortion Effects 0 (`birth`) | flashes softened |
| Player 2.5 times taller (`acting_big`, 1.21.1 scale attribute) | close shots keep the framing |
| Chinese, Japanese, German cutscene lines (`phrases`) | shown and wrapped |

Not checked here: two real players on one server at once, and a real microphone with Simple Voice Chat.
