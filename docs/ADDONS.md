# Horizons Unbound for addon makers, modpack makers and server owners

Minecraft 1.21.1, NeoForge. Mod id `horizons_unbound`.

## Settings

Everything is in the in-game screen (Mods → Horizons Unbound → Config) or in the files:

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

Storms also never change blocks players placed, blocks near the world spawn and beds (`protectRadius`),
and, with `respectClaims` on, blocks in land claims (any claim mod that stops players from breaking blocks
there through NeoForge's `BlockEvent.BreakEvent`: FTB Chunks, Open Parties and Claims, ...).

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

Use only the `com.horizonsunbound.api` package; everything else may change between versions.
The sources jar is published next to the mod jar.

### Events

Posted on `NeoForge.EVENT_BUS`, on the server:

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

KubeJS can listen to the events directly (server scripts):

```js
NativeEvents.onEvent('com.horizonsunbound.api.StormEvent$Born', e => {
  e.getLevel().players().forEach(p => p.tell(`A ${e.getStorm().type.id} storm rises`))
})

NativeEvents.onEvent('com.horizonsunbound.api.StormEvent$Announce', e => {
  if (e.getLevel().players().size() < 2) e.setCanceled(true) // storms only when two or more play together
})
```

## Other mods

- **Iris shader packs**: the storm is drawn after the pack's particles. If it looks wrong with a pack (invisible,
  a dark hole, black squares around the glow), change "Shader pack mode" in the client settings. Photon is
  detected and gets the FORWARD mode by itself.
- **Distant Horizons**: storms are seen from at least 64 chunks away when "Storm view distance" is 0.
- **Cloud mods**: under a storm's sky the vanilla cloud pass is skipped and the storm draws its own cloud cap.
  Better Clouds and Simple Clouds were tested: their clouds don't cover the storm. Simple Clouds takes over
  the weather, so with it a storm brings no vanilla thunder and rain (set Simple Clouds to vanilla weather
  if you want both).
- **Dedicated servers**: the mod loads without client code on a server (checked by the GameTests).
