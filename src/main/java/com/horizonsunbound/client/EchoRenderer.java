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
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.getDefaultTexture();
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
        @Override public VertexConsumer addVertex(float x, float y, float z) { vc.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { vc.setColor(r * 190 / 255, g * 160 / 255, b, a * 120 / 255); return this; }
        @Override public VertexConsumer setUv(float u, float v) { vc.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { vc.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { vc.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { vc.setNormal(x, y, z); return this; }
    }
}
