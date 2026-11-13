package com.horizonsunbound.effect;

import com.horizonsunbound.StormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rare block End storms leave in their ground patches (players can carry it off and set it anywhere). Within
 * {@link StormManager#ANCHOR_RADIUS} blocks space stays normal: no echoes, seams, jumps, stalled arrows or gravity waves.
 */
public class RealityAnchorBlock extends Block {
    public RealityAnchorBlock(Properties props) { super(props); }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level instanceof ServerLevel sl && !old.is(this)) StormManager.get(sl).anchor(pos, true);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (level instanceof ServerLevel sl && !now.is(this)) StormManager.get(sl).anchor(pos, false);
        super.onRemove(state, level, pos, now, moved);
    }

    /** Sparks on the edge of the calm sphere, so its reach is visible. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        for (int i = 0; i < 3; i++) {
            float yaw = rnd.nextFloat() * Mth.TWO_PI, pitch = (rnd.nextFloat() - .5f) * Mth.PI, d = StormManager.ANCHOR_RADIUS;
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + .5 + Mth.cos(yaw) * Mth.cos(pitch) * d, pos.getY() + .5 + Mth.sin(pitch) * d,
                    pos.getZ() + .5 + Mth.sin(yaw) * Mth.cos(pitch) * d, 0, 0, 0);
        }
    }
}
