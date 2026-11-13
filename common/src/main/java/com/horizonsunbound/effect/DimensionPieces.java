package com.horizonsunbound.effect;

import com.horizonsunbound.ModContent;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormManager;
import com.horizonsunbound.StormType;
import com.horizonsunbound.StormsConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;

import java.util.List;

/**
 * Storms bring pieces of their own dimension along: under the column, patches of natural ground turn into that
 * dimension's ground (set into the terrain, not floating), and now and then a whole structure from the config list
 * is set down. Nothing happens in the storm's own dimension, near the world spawn or a bed, or in unloaded chunks.
 */
final class DimensionPieces {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int STRUCTURE_TRY = 200; // ticks between structure tries

    private DimensionPieces() {}

    static void tick(ServerLevel level, StormManager m, Storm s) {
        double intensity = StormsConfig.INTENSITY.get();
        if (intensity <= 0) return;
        if (s.age % Math.max(1, Math.round(StormsConfig.PATCH_EVERY.get() / intensity)) == 0) patch(level, s, intensity);
        if (s.age > 1200 && s.age % STRUCTURE_TRY == 0 && s.structures < StormsConfig.MAX_STRUCTURES.get()
                && level.random.nextDouble() < StormsConfig.STRUCTURE_CHANCE.get() * intensity) structure(level, s);
    }

    /** One blob of foreign ground somewhere under the column, a few blocks deep in its middle. */
    private static void patch(ServerLevel level, Storm s, double intensity) {
        RandomSource rnd = level.random;
        StormType type = s.mix != null && rnd.nextBoolean() ? s.mix : s.type; // a merged storm brings both its worlds
        if (level.dimension() == type.home) return;
        float ang = rnd.nextFloat() * Mth.TWO_PI, d = Mth.sqrt(rnd.nextFloat()) * s.radius() * 1.1f;
        int cx = Mth.floor(s.x + Mth.cos(ang) * d), cz = Mth.floor(s.z + Mth.sin(ang) * d);
        BlockPos c = StormEffects.ground(level, cx, cz);
        if (c == null || StormEffects.nearHome(level, c, StormsConfig.PROTECT_RADIUS.get())) return;
        int size = Math.max(2, (int) ((3 + rnd.nextInt(6)) * Mth.clamp(.5 + .5 * intensity, .5, 1.5))), theme = type == StormType.NETHER ? rnd.nextInt(5) : 0;
        long salt = rnd.nextLong();
        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                // ragged edge: distance plus per-column noise
                float n = Mth.sqrt(dx * dx + dz * dz) / size + (Mth.getSeed(cx + dx, (int) salt, cz + dz) >>> 40 & 255) / 255f * .4f;
                if (n >= 1) continue;
                BlockPos top = StormEffects.ground(level, cx + dx, cz + dz);
                if (top == null || Math.abs(top.getY() - c.getY()) > 8 || !StormEffects.reshape(level, top, level.getBlockState(top))) continue;
                BlockPos above = top.above();
                BlockState up = level.getBlockState(above);
                if (!up.isAir() && up.canBeReplaced() && up.getFluidState().isEmpty() && StormEffects.free(level, above)) level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                int depth = 1 + (int) ((1 - n) * 4);
                for (int k = 0; k < depth; k++) {
                    BlockPos p = top.below(k);
                    if (!StormEffects.reshape(level, p, level.getBlockState(p))) break;
                    level.setBlock(p, block(type, theme, k, rnd), Block.UPDATE_CLIENTS);
                }
                BlockState deco = deco(type, theme, rnd);
                if (deco != null && level.getBlockState(above).isAir() && deco.canSurvive(level, above) && StormEffects.free(level, above)) level.setBlock(above, deco, Block.UPDATE_CLIENTS);
            }
        }
        // End ground sometimes carries a reality anchor along
        if (type == StormType.END && rnd.nextFloat() < .08f && StormEffects.reshape(level, c, level.getBlockState(c)))
            level.setBlock(c, ModContent.REALITY_ANCHOR.get().defaultBlockState(), Block.UPDATE_ALL);
        ParticleOptions dust = switch (type) {
            case NETHER -> ParticleTypes.ASH;
            case END -> ParticleTypes.REVERSE_PORTAL;
            case DEEP_DARK -> ParticleTypes.SCULK_SOUL;
            case OVERWORLD -> ParticleTypes.HAPPY_VILLAGER;
        };
        level.sendParticles(dust, cx + .5, c.getY() + 1.5, cz + .5, 40, size / 2f, .6, size / 2f, .02);
        level.playSound(null, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.AMBIENT, .6f, .6f + rnd.nextFloat() * .3f);
    }

    /** Ground block k blocks below the patch surface. Nether themes: 0 netherrack, 1 crimson, 2 warped, 3 soul, 4 basalt. */
    private static BlockState block(StormType type, int theme, int k, RandomSource rnd) {
        float f = rnd.nextFloat();
        Block b = switch (type) {
            case END -> f < .03f && k == 0 ? Blocks.OBSIDIAN : Blocks.END_STONE;
            case OVERWORLD -> k == 0 ? Blocks.GRASS_BLOCK : k < 3 ? Blocks.DIRT : Blocks.STONE;
            case DEEP_DARK -> k == 0 ? (f < .78f ? Blocks.SCULK : Blocks.DEEPSLATE) : f < .7f ? Blocks.DEEPSLATE : Blocks.COBBLED_DEEPSLATE;
            case NETHER -> switch (theme) {
                case 1 -> k == 0 ? Blocks.CRIMSON_NYLIUM : Blocks.NETHERRACK;
                case 2 -> k == 0 ? Blocks.WARPED_NYLIUM : Blocks.NETHERRACK;
                case 3 -> k == 0 ? (f < .6f ? Blocks.SOUL_SAND : Blocks.SOUL_SOIL) : f < .04f ? Blocks.BONE_BLOCK : Blocks.SOUL_SOIL;
                case 4 -> k == 0 ? (f < .6f ? Blocks.BASALT : Blocks.BLACKSTONE) : f < .03f ? Blocks.GILDED_BLACKSTONE : Blocks.BLACKSTONE;
                default -> k == 0 ? (f < .1f ? Blocks.MAGMA_BLOCK : Blocks.NETHERRACK)
                        : f < .06f ? Blocks.NETHER_QUARTZ_ORE : f < .09f ? Blocks.NETHER_GOLD_ORE : Blocks.NETHERRACK;
            };
        };
        return b.defaultBlockState();
    }

    /** What may grow or burn on top of a patch block, or null. Soul fire is the only fire: it never spreads. */
    private static BlockState deco(StormType type, int theme, RandomSource rnd) {
        float f = rnd.nextFloat();
        Block b = switch (type) {
            case END -> f < .02f ? Blocks.CHORUS_FLOWER : null;
            case OVERWORLD -> f < .25f ? Blocks.GRASS : f < .28f ? Blocks.POPPY : null;
            // shriekers placed like this never summon a warden (can_summon is false)
            case DEEP_DARK -> f < .02f ? Blocks.SCULK_SENSOR : f < .03f ? Blocks.SCULK_SHRIEKER : null;
            case NETHER -> switch (theme) {
                case 1 -> f < .15f ? Blocks.CRIMSON_ROOTS : f < .19f ? Blocks.CRIMSON_FUNGUS : null;
                case 2 -> f < .15f ? Blocks.WARPED_ROOTS : f < .19f ? Blocks.WARPED_FUNGUS : null;
                case 3 -> f < .12f ? Blocks.SOUL_FIRE : null;
                default -> null;
            };
        };
        return b == null ? null : b.defaultBlockState();
    }

    /** Sets down a fragment of a structure from the storm's config list under its column, the way /place structure does. */
    private static void structure(ServerLevel level, Storm s) {
        RandomSource rnd = level.random;
        StormType type = s.mix != null && rnd.nextBoolean() ? s.mix : s.type;
        if (level.dimension() == type.home) return;
        List<? extends String> ids = StormsConfig.structures(type);
        if (ids.isEmpty()) return;
        String id = ids.get(rnd.nextInt(ids.size()));
        ResourceLocation key = ResourceLocation.tryParse(id);
        Structure structure = key == null ? null : level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        if (structure == null) {
            LOG.warn("Horizons Unbound: unknown structure '{}' in the config", id);
            return;
        }
        float ang = rnd.nextFloat() * Mth.TWO_PI, d = rnd.nextFloat() * s.radius() * .8f;
        BlockPos at = BlockPos.containing(s.x + Mth.cos(ang) * d, 0, s.z + Mth.sin(ang) * d);
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        StructureStart start = structure.generate(level.registryAccess(), gen, gen.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed() + s.seed() + s.structures, new ChunkPos(at), 0, level, b -> true);
        if (!start.isValid()) return;
        // Only a fragment comes along: a few pieces around one random piece, cut out by a few overlapping boxes so
        // the edge is broken like rubble, not square.
        List<StructurePiece> pieces = start.getPieces();
        if (pieces.isEmpty()) return;
        BlockPos core = pieces.stream().map(p -> p.getBoundingBox().getCenter()) // the piece nearest the storm's column
                .min(java.util.Comparator.comparingDouble(c -> Mth.square(c.getX() - at.getX()) + Mth.square(c.getZ() - at.getZ()))).orElseThrow();
        int half = StormsConfig.FRAGMENT_SIZE.get() / 2;
        List<BoundingBox> cuts = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int hx = Math.max(3, (int) (half * (.5f + rnd.nextFloat() * .5f))), hz = Math.max(3, (int) (half * (.5f + rnd.nextFloat() * .5f)));
            int ox = i == 0 ? 0 : rnd.nextInt(half + 1) - half / 2, oz = i == 0 ? 0 : rnd.nextInt(half + 1) - half / 2;
            cuts.add(new BoundingBox(core.getX() + ox - hx, level.getMinBuildHeight(), core.getZ() + oz - hz,
                    core.getX() + ox + hx, level.getMaxBuildHeight(), core.getZ() + oz + hz));
        }
        BoundingBox area = BoundingBox.encapsulatingBoxes(cuts).orElseThrow();
        // Set it on the actual ground: structures of other dimensions are generated for their own heights
        // (a fortress at y 48-70, a bastion deep down) and would end up buried or in the air.
        int low = Integer.MAX_VALUE;
        for (StructurePiece p : pieces) if (p.getBoundingBox().intersects(area)) low = Math.min(low, p.getBoundingBox().minY());
        BlockPos g = StormEffects.ground(level, core.getX(), core.getZ());
        if (g == null || low == Integer.MAX_VALUE) return;
        int dy = g.getY() - low;
        if (Math.abs(dy) > 3) for (StructurePiece p : pieces) p.move(0, dy, 0);
        ChunkPos from = new ChunkPos(area.minX() >> 4, area.minZ() >> 4), to = new ChunkPos(area.maxX() >> 4, area.maxZ() >> 4);
        for (int x = from.x; x <= to.x; x++) for (int z = from.z; z <= to.z; z++) if (!level.hasChunk(x, z)) return; // try again later
        if (StormEffects.nearHome(level, area.getCenter(), StormsConfig.PROTECT_RADIUS.get() + half)) return;
        BoundingBox keepOut = area.inflatedBy(8);
        for (ServerPlayer p : level.players()) if (keepOut.isInside(p.blockPosition())) return; // never bury anyone
        if (PlacedBlocks.anyIn(level, area.inflatedBy(2))) return; // never over anything a player built
        for (int x : new int[] {area.minX(), area.maxX()}) // nor into a claim (checked at the corners and the middle: claims come in whole chunks)
            for (int z : new int[] {area.minZ(), area.maxZ()}) if (!StormEffects.unclaimed(level, new BlockPos(x, area.minY(), z))) return;
        if (!StormEffects.unclaimed(level, area.getCenter())) return;
        for (BoundingBox cut : cuts) {
            ChunkPos.rangeClosed(new ChunkPos(cut.minX() >> 4, cut.minZ() >> 4), new ChunkPos(cut.maxX() >> 4, cut.maxZ() >> 4)).forEach(cp -> {
                BoundingBox column = new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), level.getMaxBuildHeight(), cp.getMaxBlockZ());
                BoundingBox part = intersection(column, cut);
                if (part != null) start.placeInChunk(level, level.structureManager(), gen, level.getRandom(), part, cp);
            });
        }
        s.structures++;
        BlockPos c = area.getCenter();
        level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 4, .7f);
        LOG.info("Horizons Unbound: a {} storm brought a fragment of {} to {}", s.type.id, id, c.toShortString());
    }

    private static BoundingBox intersection(BoundingBox a, BoundingBox b) {
        if (!a.intersects(b)) return null;
        return new BoundingBox(Math.max(a.minX(), b.minX()), Math.max(a.minY(), b.minY()), Math.max(a.minZ(), b.minZ()),
                Math.min(a.maxX(), b.maxX()), Math.min(a.maxY(), b.maxY()), Math.min(a.maxZ(), b.maxZ()));
    }
}
