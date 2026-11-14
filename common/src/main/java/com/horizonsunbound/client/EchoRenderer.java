package com.horizonsunbound.client;

import com.horizonsunbound.effect.EchoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Draws an echo as its player, with their skin, as a violet-tinted see-through ghost. */
public class EchoRenderer extends LivingEntityRenderer<EchoEntity, PlayerModel<EchoEntity>> {
    public EchoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false) {
            @Override // violet-tinted and about half see-through, whatever color the renderer asks for
            public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
                super.renderToBuffer(pose, vc, light, overlay, r * 190 / 255f, g * 160 / 255f, b, a * 120 / 255f);
            }
        }, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoEntity echo) {
        UUID id = echo.owner();
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = id == null || connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin();
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
}
