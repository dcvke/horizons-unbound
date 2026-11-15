# First encounter

> I could hear the bells from the far side of the river. By the time I reached the bridge, the village was behind me and the sound was still ahead.

## Before you follow it

A storm gives you warning before it rises: the sky changes, the wind builds, and nearby players can receive a directional warning above the hotbar. Village bells can ring during the omens. A warning is an opportunity to choose where you want to be when the column appears.

For a first encounter, stay where you can see the whole column. Let its colour tell you which entry to read. Bring equipment you can afford to risk. The visual edge of a storm is a useful landmark, but some effects extend into the surrounding area; standing just outside the cloud does not make every kind harmless.

Purple means [End distortions](Void-Rift.md). Orange means [Nether heat](Fire-Twister.md). Blue means [Overworld weather](Thunder-Funnel.md). Teal means [Deep Dark vibrations](Deep-Whisper.md).

## How long you have

The default lifetime is **15 minutes**, including birth and death. Storms drift; they do not remain fixed above the place where they formed. At the default speed, movement is roughly 12 blocks per minute. The default natural diameter ranges from 100 to 200 blocks.

Natural checks use a **45-minute interval** after the previous storm has gone, followed by an **80% chance**. The interval pauses while a storm is present and while play is paused or closed. A failed roll restarts the interval. Exploring for forty-five minutes is therefore not a promise that a storm will appear at the end.

With `requireVisit` enabled, natural End and Nether storms are gated by the relevant dimension visits, and Deep Dark storms require a recorded Deep Dark visit. The default natural dimension list contains only `minecraft:overworld`. Commands can create storms without following the natural spawning rules.

## When the camera moves

Births and first encounters can trigger cutscenes. Hold **Shift** to skip one. Turn off your own cutscenes in the client configuration if you want to retain camera control; a server can also disable them for everyone. Turning them off does not remove storms or prevent advancements.

If flashes or camera movement are uncomfortable, adjust Minecraft's **Hide Lightning Flashes** and **Distortion Effects**, then the mod's own client settings. These choices are part of the supported experience.

## If nothing happens

Check the server's common configuration, the allowed storm kinds, dimension list, biome exclusions and visit requirements. Mushroom fields are excluded by default. Natural placement also needs a suitable position away from protected locations. For a controlled demonstration in a test world, use the [operator commands](Settings-and-Commands.md).

[Back to the guide](Home.md) · [Read the journal](Field-Journal.md)
