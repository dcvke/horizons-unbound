# Testing tools

All commands below run from the repository root. Select `neoforge` / `fabric` on branch `1.21.1`, or `forge` / `fabric` on `1.20.1`. Python helpers need Python 3; contact sheets additionally need Pillow.

## GameTests

Run `./gradlew :neoforge:runGameTestServer :fabric:runGameTestServer` (replace `neoforge` with `forge` on 1.20.1). The four shared tests check persistence and lifecycle behavior. The 1.20.1 builds also check NBT-based tempering, retained vanilla equipment modifiers and repeated application.

## Scripted clients

```sh
python tools/run_scenarios.py --loader fabric --output output/fabric-check
```

This runs all eleven scenarios: birth, cutscene, discovery, death, grand, wall, phrases, acting, gather, cutscene_obstacles and baseline. Each run has its own game directory, muted master volume, a visible ordinary window, report and screenshots. Existing output requires `--resume`, which skips passed scenarios and archives failed runs. It never changes a launcher profile.

Use `--scenarios birth baseline` for a subset, `--mods <directory>` for local Modrinth jars and `--shaderpack <zip>` for a local shader pack. Both inputs are copied into the isolated run. `--kubejs` installs a Forge 1.20.1 startup listener and requires a Born event in the log. Neither mods nor packs belong in Git.

The `config` scenario opens the loader-registered settings factory, captures the screen and checks a saved client-volume edit, then restores it. On the custom 1.20.1 screen it also exercises value parsing and the screen's save path.

```sh
python tools/contact_sheet.py output/fabric-check/birth/screenshots --reference output/reference/birth/screenshots --output output/birth-comparison.png
```

Match captures by name and inspect geometry, layers, camera, fog and interface placement. World-loading timing, random storm effects and player UUIDs can produce different terrain, wet pockets and particle positions even with the same world seed. Frame comparisons require visual judgment; an FPS capture is a sample, not a benchmark.

## Saves and servers

`tools/run_persistence.py --loader <loader> --output output/save-check` creates a world, places a tracked block, saves an active storm and encounter memory, reopens it and checks the data. It then kills and respawns the player to check retained memory. `--old-world <world-directory>` imports a copy for the old NeoForge-world check. Source worlds are preserved.

`tools/run_server_smoke.py --loader <loader> --output output/server-check` starts a fresh dedicated server on loopback, waits for readiness, sends storm commands over a temporary RCON connection and stops it. Its isolated test server accepts the EULA; it does not edit other servers.

Use `--gradle-arg=-PneoOnly` or `--gradle-arg=-PforgeOnly` with helpers when intentionally excluding Fabric configuration from a native-loader check. Normal builds include both loaders. Logs, downloaded dependencies, saved worlds, reports and screenshots stay under ignored run or output directories.
