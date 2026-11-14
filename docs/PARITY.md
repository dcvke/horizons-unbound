# Feature parity — 1.0.0

Reviewed on 2026-11-14. Minecraft versions have separate source branches; loaders within a branch compile the same common gameplay and resource files. Code using Minecraft or loader APIs necessarily differs between 1.20.1 and 1.21.1.

| Feature | NeoForge 1.21.1 | Fabric 1.21.1 | Forge 1.20.1 | Fabric 1.20.1 |
|---|---|---|---|---|
| Four kinds, omens, movement, merging and Great Storm | Included | Included | Included | Included |
| Dimensional terrain, structures, Reality Anchors and Hot Crust | Included | Included | Included | Included |
| End, Nether, Overworld and Deep Dark effects; tempering | Included | Included | Included | Included |
| Storm creatures, gathering warnings and configurable spawning | Included | Included | Included | Included |
| Cutscenes, player acting, skips, accessibility and height adjustment | Included | Included | Included | Included |
| Ten advancements and twelve languages with regional variants | Included | Included | Included | Included |
| JourneyMap, Xaero maps, Simple Voice Chat and Figura integration | Included | Included | Included | Included |
| Loader-neutral listeners, custom effects, queries and spawning | Included | Included | Included | Included |
| Native lifecycle events and KubeJS event bindings | NeoForge events | Neutral Java listeners | Forge events | Neutral Java listeners |
| Settings screen | Loader UI | Config API Port UI | Shared custom UI | Shared custom UI via Mod Menu |

"Included" records code and content availability. Runtime compatibility is limited to the combinations and tests described in [VERIFICATION.md](VERIFICATION.md); it is not a promise that every version of an optional mod works.

## Required port differences

- Java 21 game classes on 1.21.1; Java 17 game classes on 1.20.1. Both branches run Gradle and Loom with JDK 21.
- Modern custom payload codecs on 1.21.1; loader-specific packet transport behind `StormPacket` on 1.20.1. Both synchronize the same storm state and cutscene messages.
- Registry lookups, saved-data factories, game-event holders, skin access and vertex APIs use each Minecraft version's signatures.
- Tempering uses item components on 1.21.1. The 1.20.1 port uses NBT and item mixins to retain durability, glint and equipment bonuses, including vanilla attribute modifiers.
- The white thaw-smoke particle is built into 1.21.1. A registered particle with vanilla sprites supplies it on 1.20.1.
- The 1.20.1 port records requested client view distance and preserves it across respawns; 1.21.1 exposes it through Minecraft's player API.
- The 1.20.1 shader bridge also recognizes the older Iris/Oculus package name.
- Data directories use `advancement`, `loot_table`, `structure` and `tags/block` on 1.21.1, and their plural forms on 1.20.1. Advancement icons use `id` on 1.21.1 and `item` on 1.20.1.
- Block tags refer to each version's common tag conventions. Trial spawners and vaults are protected on 1.21.1; those blocks do not exist in 1.20.1.
- All translated gameplay strings match between branches. The custom 1.20.1 settings screen has one additional Client section label.
- The 1.20.1 tree contains additional port regression tests. The 1.21.1 `acting_big` scenario uses the scale attribute, which does not exist in vanilla 1.20.1; height adjustment itself is shared.

## Review scope

The comparison covered the shared Java tree, public API, optional-mod integration code and compile-time stubs, configuration definitions, translations, assets, advancements, tags and loader registration. No missing gameplay feature was identified between the four builds. Source differences were reviewed as the port adaptations above rather than forced to match byte for byte.

Builds, GameTests and release-jar checks are recorded separately in the verification notes. Real-microphone voice chat and a session with two real players remain outside the recorded checks.
