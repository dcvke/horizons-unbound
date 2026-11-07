package com.horizonsunbound;

import com.horizonsunbound.effect.EchoEntity;
import com.horizonsunbound.effect.HotCrustBlock;
import com.horizonsunbound.effect.RealityAnchorBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks, items and entities the storms bring. */
public final class ModContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HorizonsUnbound.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HorizonsUnbound.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HorizonsUnbound.MODID);

    /** Keeps space normal around itself under an End storm. */
    public static final DeferredBlock<RealityAnchorBlock> REALITY_ANCHOR = BLOCKS.register("reality_anchor", () -> new RealityAnchorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3, 1200).lightLevel(s -> 10).sound(SoundType.AMETHYST)));
    /** Ground a Nether heat wave cracked open: glows hot, then cools down and turns safe. */
    public static final DeferredBlock<HotCrustBlock> HOT_CRUST = BLOCKS.register("hot_crust", () -> new HotCrustBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).strength(1.2f).sound(SoundType.BASALT).requiresCorrectToolForDrops()
                    .lightLevel(s -> 9 - 3 * s.getValue(HotCrustBlock.AGE))));
    public static final DeferredItem<BlockItem> REALITY_ANCHOR_ITEM = ITEMS.registerSimpleBlockItem(REALITY_ANCHOR);

    public static final DeferredHolder<EntityType<?>, EntityType<EchoEntity>> ECHO = ENTITIES.register("echo",
            () -> EntityType.Builder.<EchoEntity>of(EchoEntity::new, MobCategory.MISC).sized(.6f, 1.8f).clientTrackingRange(8)
                    .updateInterval(1).noSave().noSummon().build("echo"));

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        com.horizonsunbound.effect.PlacedBlocks.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(ECHO.get(), Mob.createMobAttributes().build()));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(REALITY_ANCHOR_ITEM);
        });
    }
}
