# Reality Anchors and Hot Crust

> The stone was small enough to carry. Around it, for eight paces or so, the world remembered how to stay put.

## Reality Anchors

Reality Anchors are rare blocks left in End-storm ground patches. You can carry one away and place it elsewhere. A placed anchor registers a calm area with the storm manager; removing it unregisters that position.

Its reach is an **eight-block sphere**, with particles suggesting the boundary. Relevant End effects — echoes, seams, distance jumps, stalled arrows and gravity waves — are suppressed within that area. This is a local stabiliser rather than a device that removes a storm.

Place it where you can actually remain within its reach. The protection is spherical, not an unlimited vertical column. Nor should it be treated as general protection from every Nether, Overworld or Deep Dark mechanic.

Anchors depend on the gameplay systems that produce them. Disabling terrain transformation can change how you encounter those blocks. Resource packs can change their appearance without changing what the server checks.

## Hot Crust

Hot Crust is produced by Nether heat waves on eligible, exposed natural ground. It cools from its hot appearance over time. It is part of the Fire Twister's warning language: the surface beneath your feet can tell you what just passed through the area.

Crust placement checks the mod's reshaping and home-protection rules. Open sky is required by its placement code; roofs and caves can keep it out. That does not make every roof a complete defence against all effects of every storm.

The block should not be confused with the tempering trigger. Equipment is tempered by a heat wave passing a dropped item in the storm's heart, not simply by putting it on an arbitrary patch of crust. See [Fire Twister](Fire-Twister.md) for the checked trigger and bonuses.

## For pack authors

Use `horizons_unbound:storm_proof` for blocks that must not be reshaped and `horizons_unbound:reshapable` for eligible natural material. The complete [data-pack reference](../ADDONS.md) explains the different folder conventions for Minecraft 1.20.1 and 1.21.1.

[Back to the guide](Home.md) · [Void Rift](Void-Rift.md) · [Fire Twister](Fire-Twister.md)
