# Changelog

## 1.0.1 — Rendering and storm fixes

- Kept cutscene cameras within the loaded world and adjusted fog to hide its edge, preventing distant shots from showing cut-off terrain and empty space. The featured storm remains visible during its cutscene even at short render distances.
- Fixed storms spawning at the bottom of the world when announced over unloaded chunks. They now use nearby ground height until the terrain beneath them loads.
- Fixed glowing arcs beneath the cloud cap showing through distant hills and buildings.
- Fixed storm creatures remaining indefinitely after wandering away or being unloaded. With departure enabled, they now leave when no storm is nearby.
- Updated End storm echoes to use Sodium's optimized rendering path.
- Stopped rain around Fire Twisters, preventing their blazes from taking rain damage immediately after spawning.

## 1.0.0 — Initial release

Available for Minecraft 1.21.1 on NeoForge and Fabric, and Minecraft 1.20.1 on Forge and Fabric. All four builds include the same gameplay features, content and addon API. Download the jar matching your Minecraft version and loader; install it on both the server and clients.

### Dimensional storms

- Four storm kinds: the Void Rift (End), Fire Twister (Nether), Thunder Funnel (Overworld) and Deep Whisper (Deep Dark).
- Each storm has its own cloud column, glowing rings, sky, fog, particles and sounds, with effects both inside the column and in the surrounding area.
- Natural storms begin with omens: a darkening sky, rising wind, ringing village bells and a warning telling nearby players where the storm is gathering. They rise, drift across the landscape and eventually disperse.
- Storms can collide and merge, combining their kinds and effects. Meeting all four kinds unlocks the Great Storm encounter.
- Patches of dimensional terrain and fragments of structures appear around storms. Protection covers recorded player-placed blocks, spawn and bed areas, storm-proof blocks and supported claim hooks.
- Creatures associated with a storm's dimension appear nearby and can leave when it disperses. Their types, frequency, limits and departure are configurable.

### Effects and encounters

- End storms bring player echoes, suspended projectiles, spatial seams, distance jumps and gravity waves. Reality Anchors keep nearby space stable against End effects.
- Nether storms bring heat waves, Hot Crust, ash and soul fire. Equipment tempered in the storm's heart gains durability and an attack damage or armor toughness bonus.
- Overworld storms bring a calm eye, frost and thaw bands, temporary streams, plant growth and ground that warns of an approaching lightning strike.
- Deep Dark storms react to player vibrations. With Simple Voice Chat installed, audible speech inside a storm can also provoke a response; whispering is ignored.
- Cutscenes accompany births, first encounters, deaths and the finale, with player acting and camera movement. They can be disabled per world or per player, or skipped by holding Shift.
- Advancements track storm encounters, all four kinds, the Great Storm, entering the eye, tempering equipment and being heard by a Deep Dark storm.

### Settings, accessibility and languages

- In-game settings and configuration files control spawning, allowed dimensions and kinds, duration, speed, size, terrain changes, creatures, protection, rendering, sound and cutscenes.
- The game's Hide Lightning Flashes and Distortion Effects settings reduce storm flashes and cutscene camera motion.
- Cutscene close shots adapt to player height, and Figura avatars remain visible during external camera shots.
- Twelve languages: English, Russian, Spanish, Portuguese, French, German, Simplified Chinese, Japanese, Korean, Polish, Turkish and Ukrainian. Regional variants are included for Spanish, Portuguese and French; configuration tooltip translations are provided in English and Russian.

### Integrations and development

- Storm rendering supports Iris/Oculus shader setups and includes a selectable shader mode. Distant Horizons increases the default storm viewing range.
- Storm footprints appear in JourneyMap, Xaero's Minimap and Xaero's World Map when compatible versions are installed.
- Server commands provide manual spawning, omens, merging encounters, the Great Storm, clearing, ending and storm diagnostics.
- A loader-neutral Java API exposes storm queries, spawning, lifecycle listeners and custom effects. Forge and NeoForge also expose native events for addons and KubeJS scripts.
- Resource packs can replace visuals, sounds and text; data packs can extend protected and reshapable block tags or replace advancements.
- Source, build instructions, binary and source jars, addon documentation and verification notes accompany the release.
- Distributed under the Fogmare Mod License 1.0.

See [addon documentation](docs/ADDONS.md) for integration details and [verification notes](docs/VERIFICATION.md) for tested combinations and limits.

## Development history

`0.2.0` was the development baseline used for the multi-loader port and compatibility checks. It predates this initial release; its historical test results are retained in the verification notes.
