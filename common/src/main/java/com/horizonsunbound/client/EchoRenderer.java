package com.horizonsunbound.client;

import com.horizonsunbound.effect.EchoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Draws an echo as its player, with their skin, as a violet-tinted see-through ghost. */
public class EchoRenderer extends LivingEntityRenderer<EchoEntity, PlayerModel<EchoEntity>> {
    public EchoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoEntity echo) {
        UUID id = echo.owner();
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = id == null || connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    public void render(EchoEntity echo, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(echo, yaw, partial, pose, type -> new Ghost(buffers.getBuffer(type)), light);
    }

    @Override
    protected RenderType getRenderType(EchoEntity echo, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(echo));
    }

    /** Not while it stands where the camera is (right after it appears): it would fill the whole view. */
    @Override
    public boolean shouldRender(EchoEntity echo, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return echo.distanceToSqr(x, y, z) > 2.2 * 2.2 && super.shouldRender(echo, frustum, x, y, z);
    }

    @Override protected boolean shouldShowName(EchoEntity echo) { return false; }

    /** Passes vertices through, tinting them violet and making them about half see-through. */
    private record Ghost(VertexConsumer vc) implements VertexConsumer {
        @Override public void endVertex() { vc.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { vc.defaultColor(r * 190 / 255, g * 160 / 255, b, a * 120 / 255); }
        @Override public void unsetDefaultColor() { vc.unsetDefaultColor(); }
        @Override public VertexConsumer vertex(double x, double y, double z) { vc.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { vc.color(r * 190 / 255, g * 160 / 255, b, a * 120 / 255); return this; }
        @Override public VertexConsumer uv(float u, float v) { vc.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { vc.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { vc.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { vc.normal(x, y, z); return this; }
    }
}
