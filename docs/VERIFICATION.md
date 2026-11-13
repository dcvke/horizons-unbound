# Verification snapshot — 2026-11-13

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
