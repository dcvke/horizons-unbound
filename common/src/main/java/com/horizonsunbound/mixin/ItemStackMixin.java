package com.horizonsunbound.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    private boolean horizonsUnbound$tempered() {
        ItemStack stack = (ItemStack) (Object) this;
        return stack.hasTag() && stack.getTag().getBoolean("ds_tempered");
    }
    @ModifyReturnValue(method = "getMaxDamage", at = @At("RETURN"))
    private int horizonsUnbound$durability(int original) { return horizonsUnbound$tempered() ? original * 3 / 2 : original; }
    @ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
    private boolean horizonsUnbound$glow(boolean original) { return original || horizonsUnbound$tempered(); }
}
