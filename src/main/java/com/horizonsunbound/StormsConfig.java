package com.horizonsunbound;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** config/horizons_unbound-common.toml: gameplay settings (the server's copy is the one that counts). */
public final class StormsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REQUIRE_VISIT, TRANSFORM_TERRAIN, END_EFFECTS, NETHER_EFFECTS, OVERWORLD_EFFECTS, DEEP_DARK_EFFECTS;
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL, PATCH_EVERY, MAX_STRUCTURES, PROTECT_RADIUS, FRAGMENT_SIZE;
    public static final ModConfigSpec.DoubleValue STRUCTURE_CHANCE, INTENSITY, MERGE_CHANCE, SPAWN_CHANCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> END_STRUCTURES, NETHER_STRUCTURES, OVERWORLD_STRUCTURES, DEEP_DARK_STRUCTURES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("spawning");
        REQUIRE_VISIT = b.comment("Natural Nether/End storms only appear for a player who has already been to that dimension.",
                "Until then only Overworld storms spawn naturally. /storm spawn ignores this.").define("requireVisit", true);
        SPAWN_INTERVAL = b.comment("Minutes of play between two chances for a storm to come (game time: it stands still while",
                "the game is paused or closed). A storm lasts 15 minutes, after a minute of omens.").defineInRange("spawnIntervalMinutes", 60, 1, 1440);
        SPAWN_CHANCE = b.comment("Chance at each of those moments that a storm actually comes.").defineInRange("spawnChance", .5, 0, 1);
        MERGE_CHANCE = b.comment("Chance per minute, while one storm is out, that a second storm of another kind rises near it;",
                "the two head for each other and merge into a mixed storm. 0 = never.").defineInRange("mergeChance", .08, 0, 1);
        b.pop().push("dimensionPieces");
        TRANSFORM_TERRAIN = b.comment("Storms replace patches of natural ground under their column with blocks of their dimension.")
                .define("transformTerrain", true);
        INTENSITY = b.comment("How strongly storms bring their dimension along, as one knob: 1 = normal, 0.5 = half as many and",
                        "smaller ground patches and half the structure chance, 2 = twice as much, 0 = nothing at all.")
                .defineInRange("intensity", 1.0, 0, 4);
        PATCH_EVERY = b.comment("Ticks between two ground patches of one storm.").defineInRange("patchEvery", 40, 5, 6000);
        PROTECT_RADIUS = b.comment("No ground patches or structures this close to the world spawn or a player's bed.")
                .defineInRange("protectRadius", 48, 0, 512);
        FRAGMENT_SIZE = b.comment("Storms bring only a fragment of a structure (a piece of a fortress, not the whole fortress):",
                        "roughly this many blocks across.").defineInRange("structureFragmentSize", 20, 6, 64);
        MAX_STRUCTURES = b.comment("Structure fragments one storm may bring during its life (0 = none).").defineInRange("maxStructuresPerStorm", 1, 0, 16);
        STRUCTURE_CHANCE = b.comment("Chance per 10-second try that a storm sets down a structure under its column.")
                .defineInRange("structureChance", .08, 0, 1);
        END_STRUCTURES = b.comment("Structures an End storm can bring (structure ids). Empty = none.")
                .defineListAllowEmpty("endStructures", List.of("minecraft:end_city"), () -> "", o -> o instanceof String);
        NETHER_STRUCTURES = b.comment("Structures a Nether storm can bring.")
                .defineListAllowEmpty("netherStructures", List.of("minecraft:fortress", "minecraft:bastion_remnant", "minecraft:ruined_portal_nether"), () -> "", o -> o instanceof String);
        DEEP_DARK_STRUCTURES = b.comment("Structures a Deep Dark (sculk) storm can bring.")
                .defineListAllowEmpty("deepDarkStructures", List.of("minecraft:ancient_city"), () -> "", o -> o instanceof String);
        OVERWORLD_STRUCTURES = b.comment("Structures an Overworld storm can bring into other dimensions.")
                .defineListAllowEmpty("overworldStructures", List.of("minecraft:ruined_portal"), () -> "", o -> o instanceof String);
        b.pop().push("effects");
        END_EFFECTS = b.comment("End storms: echoes, stalling projectiles, seams, distance jumps, gravity waves.").define("end", true);
        NETHER_EFFECTS = b.comment("Nether storms: heat waves, hot crust, ash air, soul fire, tempering.").define("nether", true);
        OVERWORLD_EFFECTS = b.comment("Overworld storms: weather bands, rain pockets, streams, charged ground, migration.").define("overworld", true);
        DEEP_DARK_EFFECTS = b.comment("Deep Dark storms: sounds muffled inside, the storm hears noise and answers.").define("deepDark", true);
        b.pop();
        SPEC = b.build();
    }

    public static List<? extends String> structures(StormType type) {
        return switch (type) {
            case END -> END_STRUCTURES.get();
            case NETHER -> NETHER_STRUCTURES.get();
            case OVERWORLD -> OVERWORLD_STRUCTURES.get();
            case DEEP_DARK -> DEEP_DARK_STRUCTURES.get();
        };
    }

    public static boolean effects(StormType type) {
        return switch (type) {
            case END -> END_EFFECTS.get();
            case NETHER -> NETHER_EFFECTS.get();
            case OVERWORLD -> OVERWORLD_EFFECTS.get();
            case DEEP_DARK -> DEEP_DARK_EFFECTS.get();
        };
    }
}
