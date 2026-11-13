package com.horizonsunbound.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Cracked ground behind a Nether heat wave. Age 0 glows white-orange and burns like magma; every few seconds it
 * darkens a step (the tint comes from the age); at age 3 it is cold, dark and safe and stays that way.
 */
public class HotCrustBlock extends Block {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    /** Tint per age: white-hot, orange, deep red, cold basalt gray. */
    public static final int[] COLORS = {0xFFD27A, 0xFF7A1E, 0x9A2A10, 0x3A3438};

    public HotCrustBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE); }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (state.getValue(AGE) < 3) level.scheduleTick(pos, this, 80 + level.random.nextInt(80));
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rnd) {
        if (state.getValue(AGE) < 3) level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        int age = state.getValue(AGE);
        if (age < 3 && !entity.isSteppingCarefully() && entity instanceof LivingEntity)
            entity.hurt(level.damageSources().hotFloor(), age == 0 ? 2 : 1);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        int age = state.getValue(AGE);
        if (age < 2 && rnd.nextInt(age + 2) == 0)
            level.addParticle(age == 0 ? ParticleTypes.LAVA : ParticleTypes.SMOKE, pos.getX() + rnd.nextFloat(), pos.getY() + 1.02, pos.getZ() + rnd.nextFloat(), 0, .02, 0);
    }
}
