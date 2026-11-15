# Horizons Unbound

Huge dimensional storms bring the End, Nether, Overworld and Deep Dark into your world. Each storm has its own sky, sounds and effects, with cinematic encounters, merging storms, Reality Anchors and Hot Crust.

## Features

- **Four kinds of storm**: End, Nether, Overworld and Deep Dark, each a towering column of cloud with glowing rings, its own sky, fog, sounds and effects on the world. Two storms can collide and merge; meet them all and the Great Storm comes.
- **A storm's life**: omens first (a darkening sky, rising wind, ringing village bells and a warning of where it is gathering), then its birth, about a quarter of an hour drifting across the land, and its death.
- **Pieces of other dimensions**: patches of their ground and fragments of their structures, never on what players built, near spawn or beds, or in land claims.
- **Each kind acts differently**: echoes, seams and gravity waves in the End; heat waves, hot crust and tempered gear in the Nether; weather bands, streams and charged ground in the Overworld; a Deep Dark storm hears every step, and voices too.
- **Creatures of the storm**: mobs of its dimension appear around it and leave with it.
- **Cutscenes** for a storm's birth, the first meeting with each kind, its death and the finale, with the player acting them out. Each player can turn them off; holding Shift skips one.
- **Reality Anchors** keep space normal around them under End storms.
- **Advancements** for seeing each storm, the Great Storm, the eye of the storm, tempering and being heard.
- **Every setting adjustable** in game or in the config files: frequency, chance, kinds, dimensions, length, speed, size, creatures, terrain changes, protection, view distance and more.
- **Twelve languages**: English, Russian, Spanish, Portuguese, French, German, Chinese, Japanese, Korean, Polish, Turkish and Ukrainian.
- **Accessibility**: follows the game's "Hide Lightning Flashes" and "Distortion Effects" settings.
- **Works with** shader packs (Iris/Oculus, including Complementary and Photon), Distant Horizons, JourneyMap and Xaero's maps, Simple Voice Chat, Figura, claim mods, KubeJS and addons through a Java API.

## Downloads and requirements

Use the jar for your Minecraft version and loader. Install Horizons Unbound on both the server and clients. Version **1.0.2** uses mod id `horizons_unbound` on every loader.

| Minecraft | Loader | Java | Required additional mods | Settings screen |
|---|---|---|---|---|
| 1.21.1 | NeoForge 21.1.265+ | 21 | None | Mods → Horizons Unbound → Config |
| 1.21.1 | Fabric Loader 0.20.1+ | 21 | Fabric API 0.116.18+, Forge Config API Port 21.1.6+ | Mod Menu, optional |
| 1.20.1 | Forge 47.4.29+ | 17 | None | Mods → Horizons Unbound → Config |
| 1.20.1 | Fabric Loader 0.20.1+ | 17 | Fabric API 0.92.0+, Forge Config API Port 8.0.3+ | Mod Menu, optional |

Choose dependencies built for the same Minecraft version. Mod Menu is not required on dedicated servers. Architectury API is not required.

## Build from source

The `main` branch tracks `1.21.1`. The `1.21.1` branch contains `common`, `neoforge` and `fabric`; the `1.20.1` branch contains `common`, `forge` and `fabric`. Shared code compiles against vanilla Minecraft. Loader adapters supply registration, networking, persistence, protection and events through services. See the [feature parity review](docs/PARITY.md) for the differences required by each Minecraft version.

Install JDK 21 to run Gradle and Loom on either branch, then run:

```sh
./gradlew build
```

The `1.20.1` branch compiles and launches Minecraft with a Java 17 toolchain, which Gradle provisions automatically when needed. Its jars require Java 17 for players. The `1.21.1` branch uses Java 21 throughout.

On Windows, use `.\gradlew.bat build`. Binary and source jars are in each loader module's `build/libs/`, named `horizons_unbound-<loader>-<minecraft>-1.0.0.jar` and `-sources.jar`.

Run `./gradlew :neoforge:runClient` or `:fabric:runClient` on `1.21.1`; use `:forge:runClient` or `:fabric:runClient` on `1.20.1`. Each loader also provides `runServer`, `runGameTestServer` and `runClientTest` tasks.

## Testing

```sh
./gradlew :neoforge:runGameTestServer :fabric:runGameTestServer
./gradlew :neoforge:runClientTest -Pscenario=birth
```

Replace `neoforge` with `forge` on `1.20.1`. Scripted client tests create a world with seed 1234, run commands, capture frames, log sounds with master volume muted, and exit automatically. Scenarios are in `tools/scenarios/`; `tools/run_scenarios.py` runs the full sequence in separate directories, and `tools/contact_sheet.py` makes visual comparisons. Existing test worlds and captures are archived before a fresh test. See [testing tools](docs/TESTING.md).

The [verification snapshot](docs/VERIFICATION.md) records the tested versions, compatibility results and remaining limits for all four builds.

Release notes are in [CHANGELOG.md](CHANGELOG.md). [GitHub Releases](https://github.com/dcvke/horizons-unbound/releases) provide separate Minecraft-version releases, with loader-specific binary jars, source jars and SHA-256 checksums. A source jar is for development; install the jar without `-sources` to play.

## Addons, modpacks and servers

See [docs/ADDONS.md](docs/ADDONS.md) for settings, tags, resource packs, commands, the Java API, native events, KubeJS and protection integration.

Placement protection records blocks placed while the mod is installed. Existing builds in older worlds need spawn/bed protection, protected tags or a claim integration. Claim protection depends on the claim mod handling the loader hooks described in the addon guide.

## License

Horizons Unbound is source-available under the custom [Severed Tomorrow Mod License 1.0](LICENSE). The license covers playing, streaming, qualifying free modpacks, addons, resource packs and translations, and restricts redistribution, published modifications and ports. Read the license for the complete terms.

Contributions are welcome through pull requests against the relevant Minecraft branch. Include the loader, Minecraft version and steps to reproduce a bug. Contributions are subject to section 8 of the license.
