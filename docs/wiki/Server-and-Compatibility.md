# Server and compatibility notes

## What the storms may change

Terrain patches and structure fragments are restricted to eligible ground. The mod records blocks placed by players while it is installed, checks protected block tags, and protects areas around world spawn and beds. The default home-protection radius is 48 blocks.

Those rules have boundaries. Buildings made before installation do not acquire a placement history retroactively. Claim protection depends on the claim mod answering the relevant loader hook. On Forge/NeoForge, the mod posts a native block-break event using its storm fake player; on Fabric, it checks the before-break callback and Common Protection API when available. A protection system that bypasses those hooks may need an integration.

Before enabling terrain changes on a long-running server, check an expendable area with the actual claim setup. Add irreplaceable blocks to `horizons_unbound:storm_proof` through a data pack where appropriate. The [addon guide](../ADDONS.md) includes examples and version-specific tag paths.

## Shaders and distance

Iris/Oculus shader setups and Distant Horizons have integration support. If a shader makes a column invisible, leaves black squares around it or produces a dark hole, try the client **Shader pack mode** setting. Compatibility depends on the versions and pack used; support for one combination is not a test of every pack.

With Distant Horizons and a storm view distance of zero, the integration uses a long viewing distance of at least 64 chunks. The relevant client setting can be changed when performance or presentation calls for a different balance.

Cloud replacements may use a render path outside the vanilla cloud call that the mod suppresses under storm skies. Weather replacements may also change vanilla rain and thunder. Check [the verification snapshot](../VERIFICATION.md) before treating either result as a storm-generation failure.

## Maps, avatars and voice

JourneyMap and Xaero integrations show storms on supported maps. Xaero's integration depends on its internal chunk-highlight implementation, so an upstream update can affect it. A delayed or missing marker does not by itself mean the server has no storm; `/storm info` provides a separate check.

Figura avatars are supported during cutscene acting and camera changes. Simple Voice Chat can feed player voice activity into Deep Whisper's listening. Neither is a required dependency for ordinary play.

## When reporting a problem

Include Minecraft, loader and mod versions, whether the problem occurs with shaders disabled, the affected storm kind, and reproducible steps. For a server issue, distinguish what the server reports from what a client draws. Include relevant log excerpts after reviewing them for private server details.

Developers should use the binary and source jars matching the exact loader and Minecraft version. The [Java API and scripting reference](../ADDONS.md) describes listener threading, lifecycle events and supported hooks.

[Back to the guide](Home.md) · [Settings](Settings-and-Commands.md)
