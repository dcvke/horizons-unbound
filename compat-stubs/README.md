# Compile-time stand-ins

Declarations of the few classes Horizons Unbound calls in optional mods (JourneyMap's client API, Xaero's Minimap and World Map). They exist only so the integrations compile; they are never packaged, and at run time the installed mod supplies the real classes. Each declaration copies the member signatures of the real class as of JourneyMap API 2.0.0, Xaero's Minimap 26.6.0 and Xaero's World Map 1.47.0, for Minecraft 1.20.1 and 1.21.1.
