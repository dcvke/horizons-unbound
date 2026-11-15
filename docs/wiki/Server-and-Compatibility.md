# Server and compatibility notes

## What the storms may change

Terrain patches and structure fragments are restricted to eligible ground. The mod records blocks placed by players while it is installed, checks protected block tags, and protects areas around world spawn and beds. The default home-protection radius is 48 blocks.

Older buildings need a little care: the mod only remembers blocks placed while it is installed. Claim protection also depends on the claim mod. Test your usual claim setup in a spare area before letting storms loose near an established settlement.

Server owners can turn terrain transformation off or adjust the protected area around spawn and beds in the common configuration.

## Shaders and distance

You can use Horizons Unbound with Iris/Oculus shader setups and Distant Horizons. If a shader makes a column invisible, leaves black squares around it or produces a dark hole, try the client **Shader pack mode** setting. The best setting can vary between packs.

With Distant Horizons and a storm view distance of zero, the integration uses a long viewing distance of at least 64 chunks. The relevant client setting can be changed when performance or presentation calls for a different balance.

Cloud and weather mods can change how storm skies, rain and thunder look. If something seems wrong, try turning those mods off to narrow down the cause.

## Maps, avatars and voice

JourneyMap and Xaero's maps can show storm markers. Map updates sometimes affect those markers. If one is missing, an operator can use `/storm info` to check whether the storm is still present.

Figura avatars are supported during cutscene acting and camera changes. Simple Voice Chat can feed player voice activity into Deep Whisper's listening. Neither is a required dependency for ordinary play.

## When reporting a problem

Include Minecraft, loader and mod versions, whether the problem occurs with shaders disabled, the affected storm kind, and reproducible steps. For a server issue, distinguish what the server reports from what a client draws. Include relevant log excerpts after reviewing them for private server details.

[Back to the guide](Home.md) · [Settings](Settings-and-Commands.md)
