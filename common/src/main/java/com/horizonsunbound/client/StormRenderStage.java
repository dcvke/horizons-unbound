package com.horizonsunbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

/** Weather already applies the level pose to model-view; the earlier particle stage does not. */
public final class StormRenderStage {
    private StormRenderStage() {}
    public static void render(boolean particles, Vec3 camera, float partial, PoseStack pose) {
        if (particles != StormRenderer.particlesStage()) return;
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        if (particles) view.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        try {
            StormRenderer.onRenderLevel(camera, partial);
            InsideStorm.onRender(camera, partial);
        } finally {
            view.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }
}
