# Installation

The notebook can wait until the game starts.

Install Horizons Unbound on **both the server and every player's client**. In singleplayer, install it in your own instance. Match the Minecraft version and loader first; a source jar is for development, not for the mods folder.

| Minecraft | Loader | Java | Additional required mods |
|---|---|---|---|
| 1.21.1 | NeoForge 21.1.265+ | 21 | None |
| 1.21.1 | Fabric Loader 0.20.1+ | 21 | Fabric API 0.116.18+ and Forge Config API Port 21.1.6+ |
| 1.20.1 | Forge 47.4.29+ | 17 | None |
| 1.20.1 | Fabric Loader 0.20.1+ | 17 | Fabric API 0.92.0+ and Forge Config API Port 8.0.3+ |

Use dependency builds made for the same Minecraft version. Architectury API is not required. Mod Menu is optional on Fabric and provides a convenient route to the configuration screen; it is not needed on dedicated servers.

1. Download the correct binary jar from [Releases](https://github.com/dcvke/horizons-unbound/releases) or the [Modrinth project](https://modrinth.com/project/horizonsunbound) when a matching release is available there.
2. Put it in the instance's `mods` folder and install the dependencies listed above.
3. Install the same Minecraft/loader build on the server and its clients.
4. Start the instance. Open **Mods → Horizons Unbound → Config**, or edit the generated configuration files with the game/server stopped.

For a first look, use a separate test world. An operator can run `/storm omen end 150` to see an announced End storm, or `/storm spawn end 150` for immediate spawning. Commands alter the world: test terrain changes away from anything valuable.

## Updating an existing world

Keep a backup before changing a modded world's setup. The mod records player-placed blocks while it is installed. It cannot infer a complete history of buildings made before that record existed. Review protection settings and claims before enabling terrain changes around an older settlement.

If startup fails, check version matching before changing storm settings. For visual issues after a successful launch, read [compatibility notes](Server-and-Compatibility.md). Build instructions and development tools remain in the [README](../../README.md).

[Back to the guide](Home.md)
