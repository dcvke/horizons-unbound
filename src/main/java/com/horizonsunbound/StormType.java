package com.horizonsunbound;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Storm kinds. All share the End storm's look; they differ only in color (0xRRGGBB):
 * band glow, band core, the ring at the storm's foot, and the tint of sky/fog/clouds around it.
 */
public enum StormType {
    END("end", Level.END, 0xD24BFF, 0xFFE6FF, 0xBFE6FF, 0x6A4A9A),
    NETHER("nether", Level.NETHER, 0xFF5A0A, 0xFFE27A, 0xFFB040, 0x8A4A2A),       // lava: yellow-hot core into orange glow
    OVERWORLD("overworld", Level.OVERWORLD, 0x2F9BFF, 0xEAF8FF, 0xB8F0FF, 0x3E5A8A), // lightning: white-blue core, electric blue glow
    DEEP_DARK("deep_dark", null, 0x12B5AE, 0xB4FFF6, 0x2E7F86, 0x0E2A33);       // sculk: dark teal glow, pale cyan core

    public final String id;
    public final ResourceKey<Level> home; // the dimension it brings pieces of (null: the Deep Dark, brought everywhere)
    public final int glow, core, base, sky;

    StormType(String id, ResourceKey<Level> home, int glow, int core, int base, int sky) {
        this.id = id;
        this.home = home;
        this.glow = glow;
        this.core = core;
        this.base = base;
        this.sky = sky;
    }

    public static StormType byId(String id) {
        for (StormType t : values()) if (t.id.equals(id)) return t;
        return END;
    }
}
