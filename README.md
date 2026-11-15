<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/782f5cf4f7c1fb626daae6711ff4d354cae265af.png" alt="Horizons Unbound" width="780">
</p>

<p align="center"><em>There was nothing on that hill yesterday.</em></p>

<p align="center">
  <a href="https://modrinth.com/project/horizonsunbound">Modrinth</a> ·
  <a href="https://github.com/dcvke/horizons-unbound/releases">Downloads</a> ·
  <a href="docs/wiki/Home.md">Field guide & lore</a> ·
  <a href="docs/ADDONS.md">Addon documentation</a> ·
  <a href="https://github.com/dcvke/horizons-unbound/issues">Report an issue</a>
</p>

# Horizons Unbound

The village bells start before you can see anything. Then the sky loses its usual colour, and something begins to gather beyond the trees. You have time to get home. You also have time to make a very bad decision.

Horizons Unbound brings enormous dimensional storms into Minecraft: moving columns of cloud and light that carry the End, Nether, Overworld and Deep Dark with them. They change the weather around you, bring creatures and fragments of other places, and turn a familiar journey into one you might remember.

[![A player watches the birth of the Void Rift](https://raw.githubusercontent.com/dcvke/horizons-unbound-media/main/posters/void-rift-birth.jpg)](https://dcvke.github.io/horizons-unbound-media/video/void-rift-birth.mp4)

*Watch the Void Rift arrive — click the image for the 22-second scene.*

## A reason to watch the horizon

A storm has a beginning, a life and an ending. Warnings lead into its birth; it then drifts across the landscape before coming apart. The default lifetime is fifteen minutes, including birth and death. Storms can meet and merge, combining their effects. Meeting all four kinds leads towards the Great Storm.

Their presence reaches beyond the column. The sky and fog change, sounds move around you, and the ground can acquire patches of another dimension. A storm may leave a fragment of a structure rather than an entire building. Creatures can arrive with it, then disappear when it dies.

Cutscenes follow births, first encounters, deaths and the finale. Your character takes part in them. Hold Shift to skip, or switch them off in your client settings; servers can disable them for everyone. Storms and advancement progress still work without them.

## Four ways for the weather to go wrong

![Storms and their effects](https://cdn.modrinth.com/data/cached_images/2a5ef9c5cf2462901bd4f60abb1abf1be91f260a_0.webp)

### Void Rift · End

Purple light, unsteady distances, arrows that seem to forget where they were going. The Void Rift brings echoes of players, spatial seams and gravity waves. A Reality Anchor offers a small place where those distortions stop.

![Purple rings around a Void Rift](https://raw.githubusercontent.com/dcvke/horizons-unbound-media/main/webp-compact/end-storm.webp)

[Read the field notes](docs/wiki/Void-Rift.md)

### Deep Whisper · Deep Dark

The air goes quiet. Your footsteps do not. This storm listens for vibrations and answers repeated noise with Darkness. With Simple Voice Chat installed, it can hear your voice as well. There are no storm-spawned Deep Dark creatures in the default configuration; the listening is enough.

![Teal light around the Deep Whisper](https://raw.githubusercontent.com/dcvke/horizons-unbound-media/main/posters/deep-dark-storm.jpg)

[Learn what it hears](docs/wiki/Deep-Whisper.md)

### Fire Twister · Nether

Watch the column charge, then watch the ground. Heat waves roll outward, open ground becomes Hot Crust, and running near soul flames can wake the things around you. There is a reason to approach: dropped equipment caught by a heat wave in the storm's heart can be tempered.

![Orange rings around a Fire Twister](https://raw.githubusercontent.com/dcvke/horizons-unbound-media/main/posters/nether-storm.jpg)

[Read about heat and tempering](docs/wiki/Fire-Twister.md)

### Thunder Funnel · Overworld

Frost, thaw, new streams, and ground that gives a brief warning before lightning finds it. The blue storm looks familiar from a distance. Its quiet eye is easier to appreciate once you have crossed the weather around it.

![Blue rings around a Thunder Funnel](https://raw.githubusercontent.com/dcvke/horizons-unbound-media/main/posters/overworld-storm.jpg)

[Read the weather notes](docs/wiki/Thunder-Funnel.md)

## The notebook from the western road

> The compass was still pointing north. That was the part I trusted least.

The [field guide](docs/wiki/Home.md) follows a traveller trying to understand what the storms are bringing through. Read the [recovered journal](docs/wiki/Field-Journal.md) in order, or turn straight to the practical entries on [surviving a first encounter](docs/wiki/First-Encounter.md), [anchors and Hot Crust](docs/wiki/Anchors-and-Hot-Crust.md), and [converging storms](docs/wiki/Convergence.md).

Journal passages supply the story; the playing notes explain the implemented mechanics. Server owners have a separate [settings and commands reference](docs/wiki/Settings-and-Commands.md).

## Bring it into your world

Install the matching build on the server and every client. Start with the requirements below, then adjust the storm settings to suit your world. Natural spawning defaults to the Overworld; the default check interval is 45 minutes of eligible play, with an 80% chance at each check. A failed roll starts that interval again. This is a chance-based event, not a guaranteed countdown.

Terrain changes respect recorded player placements, protected areas around spawn and beds, protected block tags, and supported claim hooks. Existing buildings from before the mod was installed may need extra protection. For the precise boundaries of those protections, see [server notes](docs/wiki/Server-and-Compatibility.md).

The mod also provides advancements, twelve languages, resource-pack overrides and an addon API. Minecraft's Hide Lightning Flashes and Distortion Effects settings apply to storm visuals and camera effects.

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

On Windows, use `.\gradlew.bat build`. Binary and source jars are in each loader module's `build/libs/`, named `horizons_unbound-<loader>-<minecraft>-<version>.jar` and `-sources.jar`.

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
