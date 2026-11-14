package com.horizonsunbound.effect;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.ModContent;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormManager;
import com.horizonsunbound.StormsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Under a Nether storm the land is a forge: the column flares, then a heat wave rolls out and sets fire to
 * whatever stands under open sky, leaving hot crust that cools from white to dark; after it a short calm.
 * Soul fire flares up in chains at running feet and wakes the monsters around. Gear thrown into the storm's
 * heart is tempered by the wave passing over it. (Ash air is client-side: StormAtmosphere / ClientEffects.)
 */
public final class NetherEffects {
    private static final java.util.UUID TEMPERED = java.util.UUID.fromString("8c55b71f-4471-4d6c-902c-52cc2e749482");

    private NetherEffects() {}

    static void tick(ServerLevel level, StormManager m, Storm s, List<ServerPlayer> near) {
        if (near.isEmpty()) return;
        int ph = s.age % s.wavePeriod();
        if (ph == 0) for (ServerPlayer p : near) level.playSound(null, p.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.AMBIENT, 1.5f, .4f);
        if (ph == Storm.WAVE_CHARGE) for (ServerPlayer p : near) level.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 1.5f, .5f);
        if (s.age % 2 == 0) heatWave(level, s, near);
        if (s.age % 5 == 0) for (ServerPlayer p : near) soulFire(level, p);
    }

    private static void heatWave(ServerLevel level, Storm s, List<ServerPlayer> near) {
        float f = s.waveFront(s.age), prev = Math.max(0, s.waveFront(s.age - 2));
        if (f < 0) return;
        for (Entity e : StormEffects.around(level, near, Entity.class, 48)) {
            double d = s.dist(e.getX(), e.getZ());
            if (d <= prev || d > f || s.inside(e.getX(), e.getY(), e.getZ()) && !(e instanceof ItemEntity)) continue; // the eye is calm
            if (e instanceof ItemEntity item) {
                if (d < s.radius() * .5) temper(level, item); // only in the storm's heart
            } else if (e instanceof LivingEntity le && !le.isSpectator() && !le.fireImmune()
                    && level.canSeeSky(BlockPos.containing(le.getX(), le.getEyeY(), le.getZ()))) {
                le.setSecondsOnFire(4);
                crust(level, le.getBlockX(), le.getBlockZ(), 3, .45f);
            }
        }
        // and here and there along the front
        for (int i = 0; i < 2; i++) {
            float ang = level.random.nextFloat() * Mth.TWO_PI;
            int x = Mth.floor(s.x + Mth.cos(ang) * f), z = Mth.floor(s.z + Mth.sin(ang) * f);
            if (level.hasChunk(x >> 4, z >> 4)) crust(level, x, z, 2, .5f);
        }
    }

    /** Hot crust on open natural ground around x/z (only what the sky can see, so roofs and caves keep it out). */
    private static void crust(ServerLevel level, int x, int z, int radius, float density) {
        if (StormEffects.nearHome(level, new BlockPos(x, level.getSeaLevel(), z), StormsConfig.PROTECT_RADIUS.get())) return;
        BlockState hot = ModContent.HOT_CRUST.get().defaultBlockState();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || level.random.nextFloat() > density) continue;
                BlockPos top = StormEffects.ground(level, x + dx, z + dz);
                if (top == null || !StormEffects.reshape(level, top, level.getBlockState(top)) || !level.canSeeSky(top.above())) continue;
                level.setBlock(top, hot, Block.UPDATE_CLIENTS);
                level.sendParticles(ParticleTypes.LAVA, top.getX() + .5, top.getY() + 1, top.getZ() + .5, 1, .3, 0, .3, 0);
            }
        }
    }

    /** Running past soul fire sets it flaring in a chain from flame to flame, and the noise draws monsters in. */
    private static void soulFire(ServerLevel level, ServerPlayer p) {
        if (!p.isSprinting() || p.isSpectator()) return;
        long now = level.getGameTime();
        if (com.horizonsunbound.platform.Services.STORAGE.data(p).getLong("ds_soul") > now) return;
        List<BlockPos> lit = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(p.blockPosition());
        int reach = 6;
        while (!todo.isEmpty() && lit.size() < 16) {
            BlockPos from = todo.poll();
            for (BlockPos q : BlockPos.betweenClosed(from.offset(-reach, -3, -reach), from.offset(reach, 3, reach))) {
                if (lit.size() >= 16 || !isSoulFlame(level.getBlockState(q)) || !seen.add(q.immutable())) continue;
                lit.add(q.immutable());
                todo.add(q.immutable());
            }
            reach = 4; // links of the chain are shorter than the first spark
        }
        if (lit.isEmpty()) return;
        com.horizonsunbound.platform.Services.STORAGE.data(p).putLong("ds_soul", now + 40);
        for (BlockPos q : lit) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, q.getX() + .5, q.getY() + .6, q.getZ() + .5, 12, .2, .5, .2, .06);
            level.sendParticles(ParticleTypes.SOUL, q.getX() + .5, q.getY() + 1, q.getZ() + .5, 2, .2, .2, .2, .02);
        }
        level.playSound(null, lit.get(0), SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 2, .8f);
        for (Monster mob : level.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(24)))
            if (mob.getTarget() == null) mob.setTarget(p);
    }

    private static boolean isSoulFlame(BlockState s) {
        return s.is(Blocks.SOUL_FIRE) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH) || s.is(Blocks.SOUL_LANTERN) || s.is(Blocks.SOUL_CAMPFIRE);
    }

    /** Gear the wave passes over in the heart: +50% durability, +1 attack damage (weapons, tools) or +1 toughness (armor). */
    static void temper(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem().copy();
        if (!stack.isDamageableItem() || stack.getOrCreateTag().getBoolean("ds_tempered")) return;
        // AttributeModifiers replaces defaults in 1.20.1, so snapshot every slot before adding anything.
        if (!stack.getOrCreateTag().contains("AttributeModifiers", net.minecraft.nbt.Tag.TAG_LIST)) {
            var defaults = new java.util.EnumMap<net.minecraft.world.entity.EquipmentSlot,
                    com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier>>(net.minecraft.world.entity.EquipmentSlot.class);
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) defaults.put(slot, com.google.common.collect.ImmutableMultimap.copyOf(stack.getAttributeModifiers(slot)));
            defaults.forEach((slot, modifiers) -> modifiers.forEach((attribute, modifier) -> stack.addAttributeModifier(attribute, modifier, slot)));
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            stack.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TEMPERED, "Horizons Unbound tempered", 1, AttributeModifier.Operation.ADDITION), armor.getEquipmentSlot());
        } else if (stack.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)) {
            stack.addAttributeModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(TEMPERED, "Horizons Unbound tempered", 1, AttributeModifier.Operation.ADDITION), net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        stack.getOrCreateTag().putBoolean("ds_tempered", true);
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(Component.translatable("item.horizons_unbound.tempered").withStyle(ChatFormatting.GOLD))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        item.setItem(stack);
        level.sendParticles(ParticleTypes.LAVA, item.getX(), item.getY() + .3, item.getZ(), 12, .2, .2, .2, 0);
        com.horizonsunbound.StormAdvancements.tempered(level, item.getX(), item.getY(), item.getZ());
        level.playSound(null, item.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1, .6f);
    }
}
