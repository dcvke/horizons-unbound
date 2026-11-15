# Settings and commands

Keep this page somewhere dry. It is the part of the notebook you may actually need while administering a server.

## Which file counts

| File | Controls |
|---|---|
| `config/horizons_unbound-common.toml` | The world/server: natural spawning, lifetime, kinds, dimensions, gameplay effects, terrain, mobs and protection |
| `config/horizons_unbound-client.toml` | Each player: rendering distance, personal cutscenes, volume and shader-pack mode |

The server's common file determines the world. Changing a client's copy does not override a multiplayer server's decisions. The in-game configuration screen offers the same settings; on Fabric, Mod Menu is an optional route to that screen.

## Useful defaults

| Setting | Default | Meaning |
|---|---|---|
| `minutesBetweenStorms` | 45 | Eligible play time before another natural roll |
| `stormChance` | 0.8 | Chance that roll succeeds |
| `secondStormChance` | 0.3 | Chance of the additional storm used for natural convergence |
| `stormMinutes` | 15 | Lifetime, including birth and death; range 3–120 |
| `speed` | 1.0 | About 12 blocks/minute; zero stops drifting |
| `minDiameter`, `maxDiameter` | 100, 200 | Natural size bounds, in blocks |
| `dimensions` | `minecraft:overworld` | Dimensions eligible for natural spawning |
| `requireVisit` | true | Visit gates for End, Nether and Deep Dark natural storms |
| `protectRadius` | 48 | Terrain protection around world spawn and beds |
| `respectClaims` | true | Ask supported claim/protection hooks |
| `intensity` | 1.0 | Scale dimension-pieces activity; zero removes that activity |

`transformTerrain` controls natural-ground transformation. Creature spawning has its own `mobs` section: enabled by default, a 30-second interval, a cap of four per storm, and `leaveWithStorm` enabled. Deep Dark's default creature list is empty. Terrain intensity and the four effect toggles are different controls; do not assume a terrain-only change disables all storm hazards.

For a gentler world, consider disabling terrain transformation, lowering dimension-piece intensity, adjusting creatures and lengthening the natural interval. Change one system at a time so you can tell which change produced the result you wanted.

## Operator commands

All require **permission level 2**. They act in the caller's dimension.

| Command | Behaviour |
|---|---|
| `/storm spawn <kind> [diameter]` | Spawn a storm immediately |
| `/storm omen <kind> [diameter]` | Announce it with omens; its arrival can use a cutscene |
| `/storm natural` | Attempt natural placement now; can fail if no suitable spot exists |
| `/storm twin` | Try to bring a different kind beside an eligible existing storm |
| `/storm grand` | Stage the Great Storm |
| `/storm end` | Start the death of storms in this dimension |
| `/storm clear` | Remove them immediately |
| `/storm info` | Print current storm information |
| `/storm debug` | Show effect cost per tick and stopped-effect diagnostics |

Kinds are `end`, `nether`, `overworld`, `deep_dark`. An explicit diameter must be **100–200**.

```mcfunction
/storm omen end 150
/storm info
/storm end
```

`end` lets a storm die through its lifecycle. `clear` is immediate removal and does not post the normal End lifecycle event. Addons and scripts that rely on those events should account for the difference. Spawn commands bypass natural eligibility rules; using one does not prove a natural-spawning configuration is correct.

## Personal comfort

Client cutscenes and storm volume can be adjusted independently. Minecraft's Hide Lightning Flashes softens flashes and flicker; Distortion Effects scales camera movement. The [addon documentation](../ADDONS.md) covers the full configuration and scripting surface.

[Back to the guide](Home.md)
