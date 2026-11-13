# Horizons Unbound

Huge dimensional storms bring the End, Nether, Overworld and Deep Dark into your world. Each storm has its own sky, sounds and effects, with cinematic encounters, merging storms, Reality Anchors and Hot Crust.

## Downloads and requirements

Use the jar for your Minecraft version and loader. Install Horizons Unbound on both the server and clients. Version **0.2.0** uses mod id `horizons_unbound` on every loader.

| Minecraft | Loader | Java | Required additional mods | Settings screen |
|---|---|---|---|---|
| 1.21.1 | NeoForge 21.1.265+ | 21 | None | Mods → Horizons Unbound → Config |
| 1.21.1 | Fabric Loader 0.20.1+ | 21 | Fabric API, Forge Config API Port 21.1.6+ | Mod Menu, optional |
| 1.20.1 | Forge 47.4.29+ | 17 | None | Mods → Horizons Unbound → Config |
| 1.20.1 | Fabric Loader 0.20.1+ | 17 | Fabric API 0.92.0+, Forge Config API Port 8.0.3+ | Mod Menu, optional |

Choose dependencies built for the same Minecraft version. Mod Menu is not required on dedicated servers. Architectury API is not required.

## Build from source

The `1.21.1` branch contains `common`, `neoforge` and `fabric`; the `1.20.1` branch contains `common`, `forge` and `fabric`. Shared code compiles against vanilla Minecraft. Loader adapters supply registration, networking, persistence, protection and events through services.

Install JDK 21 for the `1.21.1` branch or JDK 17 for `1.20.1`, then run:

```sh
./gradlew build
```

On Windows, use `.\gradlew.bat build`. Binary and source jars are in each loader module's `build/libs/`, named `horizons_unbound-<loader>-<minecraft>-0.2.0.jar` and `-sources.jar`.

Run `./gradlew :neoforge:runClient` or `:fabric:runClient` on `1.21.1`; use `:forge:runClient` or `:fabric:runClient` on `1.20.1`. Each loader also provides `runServer`, `runGameTestServer` and `runClientTest` tasks.

## Testing

```sh
./gradlew :neoforge:runGameTestServer :fabric:runGameTestServer
./gradlew :neoforge:runClientTest -Pscenario=birth
```

Replace `neoforge` with `forge` on `1.20.1`. Scripted client tests create a world with seed 1234, run commands, capture frames, log sounds with master volume muted, and exit automatically. Scenarios are in `tools/scenarios/`; `tools/run_scenarios.py` runs the full sequence in separate directories, and `tools/contact_sheet.py` makes visual comparisons. Existing test worlds and captures are archived before a fresh test. See [testing tools](docs/TESTING.md).

## Addons, modpacks and servers

See [docs/ADDONS.md](docs/ADDONS.md) for settings, tags, resource packs, commands, the Java API, native events, KubeJS and protection integration.

## License

[MIT](LICENSE), copyright 2026 dcvke.
