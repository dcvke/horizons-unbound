# Horizons Unbound for addon makers, modpack makers and server owners

Minecraft 1.21.1 (NeoForge and Fabric), Minecraft 1.20.1 (Forge and Fabric). Mod id `horizons_unbound`.

## Settings

Everything is in the in-game screen (Mods → Horizons Unbound → Config) or in the files:

On Fabric, install the matching Forge Config API Port and optionally Mod Menu to open the screen. On 1.21.1 the screen uses the loader's configuration UI; on 1.20.1 the shared screen supports sections, defaults, validated numbers, enums and editable lists. Changes are applied when Done is pressed.

| File | Side | What |
|---|---|---|
| `config/horizons_unbound-common.toml` | the world / the server | how often storms come, which kinds, cutscenes for everyone, what storms may change, land claims, biomes without storms |
| `config/horizons_unbound-client.toml` | each player | storm view distance, my cutscenes, storm volume, shader pack mode |

On a server only the server's common file counts for the world; each player keeps their own client file.

## Data pack tags

| Tag | Meaning |
|---|---|
| `#horizons_unbound:reshapable` | natural ground a storm may replace with blocks of its dimension (stone, dirt, sand, ...) |
| `#horizons_unbound:storm_proof` | blocks storms never touch, whatever else says so (bedrock, portals, spawners, chests, beds, signs, ...) |

Add your mod's ground to `reshapable`, and anything precious to `storm_proof`:

```json
// data/horizons_unbound/tags/block/storm_proof.json in your data pack
{ "replace": false, "values": ["mymod:altar", "#mymod:machines"] }
```

Use `tags/blocks/` instead of `tags/block/` in Minecraft 1.20.1 data packs. That version also uses `loot_tables/` and `structures/` instead of the singular 1.21.1 folder names.

Storms also never change blocks players placed or blocks near the world spawn and beds (`protectRadius`). With `respectClaims` on, a fake player named `[Horizons Unbound storm]` requests break permission. NeoForge and Forge post their native `BlockEvent.BreakEvent`; Fabric invokes `PlayerBlockBreakEvents.BEFORE` and, when installed, Common Protection API. Protection therefore depends on the claim mod handling that loader's hook. Claim mods which bypass those hooks need an integration; installing a claim mod alone is not a guarantee.

## Resource packs

All of it can be replaced by a resource pack under `assets/horizons_unbound/`:
- `lang/*.json`: every cutscene line (`cutscene.horizons_unbound.*`), storm names, settings;
- `sounds.json` and `sounds/`: the storms' sounds;
- `textures/`: the storm's glow, bands and effects.

## Commands (op level 2)

`/storm spawn <kind> [diameter]`, `/storm omen <kind> [diameter]` (omens first, it rises when someone comes close),
`/storm natural`, `/storm twin`, `/storm grand`, `/storm end` (every storm in the dimension starts to die),
`/storm clear`, `/storm info`, `/storm debug` (each storm's cost in ms per tick; a storm whose effects failed shows STOPPED).

Kinds: `end`, `nether`, `overworld`, `deep_dark`. Diameter 100 to 200 blocks.

## Java API

The public entry points are in `com.horizonsunbound.api`; callbacks expose the shared `Storm` and `StormType` types. Other implementation details may change between versions. Use the binary and source jars for the same loader and Minecraft version as your addon.

For local development, put the binary jar in your addon's `libs/` and add the appropriate dependency:

```groovy
// NeoForge with ModDevGradle
dependencies { implementation files('libs/horizons_unbound-neoforge-1.21.1-0.2.0.jar') }
// Forge with ModDevGradle legacyforge: use module coordinates to remap SRG names
repositories { flatDir { dirs 'libs' } }
dependencies { modImplementation group: 'local.mods', name: 'horizons_unbound-forge-1.20.1-0.2.0', version: 'local' }
// Fabric with Loom: use modImplementation so Loom remaps the production jar
dependencies { modImplementation files('libs/horizons_unbound-fabric-1.21.1-0.2.0.jar') }
```

Use `horizons_unbound-forge-1.20.1-0.2.0.jar` or the Fabric 1.20.1 jar on that version. Declare Horizons Unbound as a required dependency in your loader metadata if your addon directly references its classes.

### Events

Posted on `NeoForge.EVENT_BUS` on NeoForge and `MinecraftForge.EVENT_BUS` on Forge, on the server. Native `StormEvent` classes are loader-specific; Fabric uses the neutral listener below.

| Event | When | Cancel |
|---|---|---|
| `StormEvent.Announce` | a storm is about to come (its omens start) | yes: it never comes |
| `StormEvent.Born` | a storm rises (its birth starts) | no |
| `StormEvent.Dying` | a storm starts to come apart (its last 400 ticks) | no |
| `StormEvent.End` | a storm is gone (not posted for `/storm clear`) | no |
| `StormEvent.Merge` | two storms became one: `getFirst()`, `getSecond()`, `getStorm()` is the new one | no |

```java
NeoForge.EVENT_BUS.addListener((StormEvent.Announce e) -> {
    if (e.getStorm().has(StormType.NETHER) && isPeaceful(e.getLevel())) e.setCanceled(true);
});
NeoForge.EVENT_BUS.addListener((StormEvent.Born e) ->
    e.getLevel().players().forEach(p -> p.sendSystemMessage(Component.literal("A storm rises!"))));
```

`getStorm()` gives the live storm: read `type`, `mix` (a merged storm's second kind, or null), `grand`,
`x y z`, `diameter`, `radius()`, `zone()` (where its effects reach), `has(kind)`, `dying()`. Don't change it.

The same lifecycle is available on all four builds without an event bus:

```java
HorizonsUnboundApi.listen(new StormListener() {
    @Override public boolean announce(ServerLevel level, Storm storm) {
        return !(storm.has(StormType.NETHER) && isPeaceful(level));
    }
    @Override public void born(ServerLevel level, Storm storm) {
        level.players().forEach(p -> p.sendSystemMessage(Component.literal("A storm rises!")));
    }
});
```

Register once during initialization. `announce` returns false to cancel; `born`, `dying`, `end` and `merge` are notifications. `merge` receives the new storm followed by both original storms. Native and neutral announce decisions are combined: a cancellation by either prevents the announced storm.

### Your own storm effects

```java
HorizonsUnboundApi.addEffect(StormType.END, (level, storm, near) -> {
    // every tick, for every risen End storm (a merged storm runs both kinds' effects);
    // near: the players in its zone, may be empty
    for (ServerPlayer p : near) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40));
});
```

Call it once, for example from your mod's constructor. If your effect throws, the error is logged and that
storm's effects stop (the storm stays in the sky), so one broken addon never crashes the world.

### Other calls

- `HorizonsUnboundApi.storms(level)`: the storms that have risen in a level.
- `HorizonsUnboundApi.spawn(level, kind, pos, diameter, omens)`: brings a storm; null if a listener cancelled it.

## KubeJS

KubeJS is supported through native events on NeoForge and Forge. These examples are not Fabric entry points.

**NeoForge 1.21.1:** put this in `kubejs/server_scripts/horizons_unbound.js`:

```js
NativeEvents.onEvent('com.horizonsunbound.api.StormEvent$Born', e => {
  e.getLevel().players().forEach(p => p.tell(`A ${e.getStorm().type.id} storm rises`))
})

NativeEvents.onEvent('com.horizonsunbound.api.StormEvent$Announce', e => {
  if (e.getLevel().players().size() < 2) e.setCanceled(true) // storms only when two or more play together
})
```

**Forge 1.20.1:** use `ForgeEvents.onEvent` in **`kubejs/startup_scripts/horizons_unbound.js`**. KubeJS 2001 exposes `ForgeEvents` to startup scripts:

```js
ForgeEvents.onEvent('com.horizonsunbound.api.StormEvent$Born', e => {
  console.info('A ' + e.getStorm().type.id + ' storm rises')
})

ForgeEvents.onEvent('com.horizonsunbound.api.StormEvent$Announce', e => {
  if (e.getLevel().players().size() < 2) e.setCanceled(true)
})
```

Restart the game/server after changing startup scripts. The binding and class-string form are defined in KubeJS's [2001 Forge plugin](https://github.com/KubeJS-Mods/KubeJS/blob/2001/forge/src/main/java/dev/latvian/mods/kubejs/forge/BuiltinKubeJSForgePlugin.java) and [Forge event wrapper](https://github.com/KubeJS-Mods/KubeJS/blob/2001/forge/src/main/java/dev/latvian/mods/kubejs/forge/ForgeEventWrapper.java); the newer [native event wrapper](https://github.com/KubeJS-Mods/KubeJS/blob/2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/wrapper/NativeEventWrapper.java) implements the NeoForge API.

## Other mods

- **Iris / Oculus shader packs**: the storm is drawn after the pack's particles. If it looks wrong with a pack (invisible,
  a dark hole, black squares around the glow), change "Shader pack mode" in the client settings. Photon is
  detected and gets the FORWARD mode by itself.
- **Distant Horizons**: storms are seen from at least 64 chunks away when "Storm view distance" is 0.
- **Fabric 1.20.1 shader versions**: the tested stable pair is Iris 1.7.5 with Sodium 0.5.11. Iris 1.7.6's [required Sodium version](https://api.modrinth.com/v2/version/s5eFLITc) is the 0.5.12 beta; mixing it with stable Sodium 0.5.11 fails before a world loads.
- **Cloud mods**: under a storm's sky the vanilla cloud invocation is skipped and the storm draws its own cloud cap. A cloud mod which uses a separate render path may need its own integration. Weather replacements may suppress vanilla thunder and rain.
- **Dedicated servers**: each loader has ordinary server smoke tests as well as GameTests; no rendering classes are required on a server.
