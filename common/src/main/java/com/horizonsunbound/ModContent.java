package com.horizonsunbound;

import com.horizonsunbound.effect.EchoEntity;
import com.horizonsunbound.effect.HotCrustBlock;
import com.horizonsunbound.effect.RealityAnchorBlock;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Blocks, items and entities the storms bring. */
public final class ModContent {

    /** Keeps space normal around itself under an End storm. */
    public static final Supplier<RealityAnchorBlock> REALITY_ANCHOR = Suppliers.memoize(() -> new RealityAnchorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3, 1200).lightLevel(s -> 10).sound(SoundType.AMETHYST)));
    /** Ground a Nether heat wave cracked open: glows hot, then cools down and turns safe. */
    public static final Supplier<HotCrustBlock> HOT_CRUST = Suppliers.memoize(() -> new HotCrustBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).strength(1.2f).sound(SoundType.BASALT).requiresCorrectToolForDrops()
                    .lightLevel(s -> 9 - 3 * s.getValue(HotCrustBlock.AGE))));
    public static final Supplier<BlockItem> REALITY_ANCHOR_ITEM = Suppliers.memoize(() -> new BlockItem(REALITY_ANCHOR.get(), new net.minecraft.world.item.Item.Properties()));

    public static final Supplier<EntityType<EchoEntity>> ECHO = Suppliers.memoize(
            () -> EntityType.Builder.<EchoEntity>of(EchoEntity::new, MobCategory.MISC).sized(.6f, 1.8f).clientTrackingRange(8)
                    .updateInterval(1).noSave().noSummon().build("echo"));

    public static final Supplier<net.minecraft.core.particles.SimpleParticleType> THAW_SMOKE = Suppliers.memoize(() -> new net.minecraft.core.particles.SimpleParticleType(false) {});

    private ModContent() {}
}
