package com.horizonsunbound.fabric.mixin;

import com.horizonsunbound.effect.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult;sidedSuccess(Z)Lnet/minecraft/world/InteractionResult;"))
    private void horizonsUnbound$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir,
                                        @Local(ordinal = 1) BlockPlaceContext placedContext) {
        if (placedContext == null || placedContext.getPlayer() == null
                || !(placedContext.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = placedContext.getClickedPos();
        BlockState state = level.getBlockState(pos);
        PlacedBlocks.mark(level, pos, true);
        if (state.getBlock() instanceof BedBlock) {
            BlockPos other = pos.relative(state.getValue(BedBlock.FACING));
            if (level.getBlockState(other).getBlock() == state.getBlock()) PlacedBlocks.mark(level, other, true);
        } else if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            if (level.getBlockState(other).getBlock() == state.getBlock()) PlacedBlocks.mark(level, other, true);
        }
    }
}
