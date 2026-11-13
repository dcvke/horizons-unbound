package com.horizonsunbound;

import net.minecraftforge.common.ForgeConfigSpec;


import java.util.List;

/** config/horizons_unbound-common.toml: gameplay settings (the server's copy is the one that counts). */
public final class StormsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue RESPECT_CLAIMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NO_STORM_BIOMES;
    public static final ForgeConfigSpec.BooleanValue CUTSCENES, ALLOW_END, ALLOW_NETHER, ALLOW_OVERWORLD, ALLOW_DEEP_DARK;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_VISIT, TRANSFORM_TERRAIN, END_EFFECTS, NETHER_EFFECTS, OVERWORLD_EFFECTS, DEEP_DARK_EFFECTS;
    public static final ForgeConfigSpec.IntValue SPAWN_INTERVAL, PATCH_EVERY, MAX_STRUCTURES, PROTECT_RADIUS, FRAGMENT_SIZE;
    public static final ForgeConfigSpec.DoubleValue STRUCTURE_CHANCE, INTENSITY, MERGE_CHANCE, SPAWN_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> END_STRUCTURES, NETHER_STRUCTURES, OVERWORLD_STRUCTURES, DEEP_DARK_STRUCTURES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("spawning");
        REQUIRE_VISIT = b.comment("Natural Nether/End storms only appear for a player who has already been to that dimension.",
                "Until then only Overworld storms spawn naturally. /storm spawn ignores this.").define("requireVisit", true);
        SPAWN_INTERVAL = b.comment("Minutes of play after a storm is gone before the next one may come. The count stands still while a",
                "storm is out (a storm lasts 15 minutes) and while the game is paused or closed.").defineInRange("minutesBetweenStorms", 45, 1, 1440);
        SPAWN_CHANCE = b.comment("Chance that a storm comes when that time is up; if not, the count starts over.").defineInRange("stormChance", .8, 0, 1);
        MERGE_CHANCE = b.comment("Chance that, while a storm is out, a second storm of another kind rises near it a few minutes later;",
                "the two head for each other and merge into a mixed storm. 0 = never.").defineInRange("secondStormChance", .3, 0, 1);
        ALLOW_OVERWORLD = b.comment("Which kinds of storm may come on their own (/storm spawn ignores this).").define("allowOverworld", true);
        ALLOW_NETHER = b.define("allowNether", true);
        ALLOW_END = b.define("allowEnd", true);
        ALLOW_DEEP_DARK = b.define("allowDeepDark", true);
        CUTSCENES = b.comment("Cutscenes on this world or server (a storm's birth, finding a new kind, its death, the finale). Off: storms",
                "rise right after their omens and nobody's camera is taken. Each player can also turn them off for themselves.").define("cutscenes", true);
        b.pop().push("protection");
        RESPECT_CLAIMS = b.comment("Storms leave land claims alone (FTB Chunks, Open Parties and Claims and other claim mods that stop players",
                "breaking blocks): asked through the same check a player's block breaking goes through.").define("respectClaims", true);
        NO_STORM_BIOMES = b.comment("Biomes storms never come to on their own: biome ids (minecraft:mushroom_fields) or biome tags (#minecraft:is_ocean).",
                "Blocks storms never touch: the data pack block tag horizons_unbound:storm_proof. Ground they may reshape: horizons_unbound:reshapable.")
                .defineListAllowEmpty(List.of("noStormBiomes"), () -> List.of("minecraft:mushroom_fields"), o -> o instanceof String);
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
                .defineListAllowEmpty(List.of("endStructures"), () -> List.of("minecraft:end_city"), o -> o instanceof String);
        NETHER_STRUCTURES = b.comment("Structures a Nether storm can bring.")
                .defineListAllowEmpty(List.of("netherStructures"), () -> List.of("minecraft:fortress", "minecraft:bastion_remnant", "minecraft:ruined_portal_nether"), o -> o instanceof String);
        DEEP_DARK_STRUCTURES = b.comment("Structures a Deep Dark (sculk) storm can bring.")
                .defineListAllowEmpty(List.of("deepDarkStructures"), () -> List.of("minecraft:ancient_city"), o -> o instanceof String);
        OVERWORLD_STRUCTURES = b.comment("Structures an Overworld storm can bring into other dimensions.")
                .defineListAllowEmpty(List.of("overworldStructures"), () -> List.of("minecraft:ruined_portal"), o -> o instanceof String);
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

    /** May a storm come to this biome on its own (noStormBiomes)? Bad entries are skipped (and logged once). */
    public static boolean biomeAllowed(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        for (String e : NO_STORM_BIOMES.get()) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(e.startsWith("#") ? e.substring(1) : e);
            if (id == null) {
                if (BAD.add(e)) org.slf4j.LoggerFactory.getLogger("horizons_unbound").warn("Horizons Unbound: '{}' in noStormBiomes is not a biome id or #tag", e);
                continue;
            }
            if (e.startsWith("#") ? biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, id)) : biome.is(id)) return false;
        }
        return true;
    }

    private static final java.util.Set<String> BAD = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean allowed(StormType type) {
        return switch (type) {
            case END -> ALLOW_END.get();
            case NETHER -> ALLOW_NETHER.get();
            case OVERWORLD -> ALLOW_OVERWORLD.get();
            case DEEP_DARK -> ALLOW_DEEP_DARK.get();
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
