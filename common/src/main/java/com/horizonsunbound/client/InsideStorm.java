package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.ModSounds;
import com.horizonsunbound.ModContent;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormType;
import com.horizonsunbound.effect.HotCrustBlock;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * What it is like inside a storm's column, for the local player. Nothing here hurts; it is all for wonder.
 * <ul>
 * <li>The wall is an air cushion: crossing it, the storm smoothly pushes back, but lets you through.</li>
 * <li>Crossing it: a muffled whoosh and a flash of the storm's color around the screen edge.</li>
 * <li>End: silence (rain, thunder and creatures go quiet), a low hum, low gravity, stars over the column's mouth
 * (StormRenderer) and shards of the End slowly circling you.</li>
 * <li>Nether: a calm forge - sparks rising, crackling, distant Nether voices, updrafts over fire and magma that toss
 * you up, glowing footprints that fade.</li>
 * <li>Overworld: the eye of the storm - no rain and a clear sky above (ClientEffects, StormAtmosphere,
 * StormRenderer), birds, static sparks around you and arcs when you strike, the air slowly turning, aurora
 * under the cloud cap (StormRenderer).</li>
 * </ul>
 */

public final class InsideStorm {
    private static final ResourceLocation VIGNETTE = ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");
    private static final BlockState[] SHARDS = {Blocks.END_STONE.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
            Blocks.OBSIDIAN.defaultBlockState(), Blocks.CHORUS_FLOWER.defaultBlockState(), Blocks.END_STONE_BRICKS.defaultBlockState()};
    private static StormType type;        // the storm kind we are (or were last) inside
    private static boolean in;
    private static float weight;          // 0..1, eased: how far the inside effects have faded in
    private static float flash;           // 0..1 screen-edge flash when crossing the wall
    private static int flashColor;
    private static Loop loop;
    private static int cooldown;          // ticks until the next random sound
    private static int updraft;           // ticks until the next updraft may toss us
    private static final ArrayDeque<float[]> PRINTS = new ArrayDeque<>(); // {x, y, z, yaw, side, age}
    private static double lastPrintX, lastPrintZ;
    private static float side = 1;

    private InsideStorm() {}

    /** 0..1 how much the player is inside a storm of this kind right now (eased in and out). */
    public static float weight(StormType t) { return type == t ? weight : 0; }

    /** 0..1 how much the player is inside any storm. */
    public static float weight() { return weight; }

    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            in = false;
            weight = flash = 0;
            PRINTS.clear();
            return;
        }
        if (mc.isPaused()) return;
        Storm now = null;
        for (Storm s : ClientStorms.all()) if (s.inside(p.getX(), p.getY(), p.getZ())) now = s;
        if (now != null && type != now.type) { // another kind of storm: its own effects fade in from nothing
            type = now.type;
            weight = 0;
        }
        boolean nowIn = now != null && !p.isSpectator();
        if (nowIn != in && type != null) cross(mc, nowIn);
        in = nowIn;
        weight += ((in ? 1 : 0) - weight) * .08f;
        flash = Math.max(0, flash - .05f);
        for (Iterator<float[]> it = PRINTS.iterator(); it.hasNext(); ) if ((it.next()[5] += 1) > 60) it.remove();
        if (weight < .02f || type == null) return;
        if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) startLoop(mc);
        RandomSource rnd = mc.level.random;
        switch (type) {
            case END -> {}
            case NETHER -> forge(mc, p, rnd);
            case OVERWORLD -> eye(mc, p, rnd);
            case DEEP_DARK -> deepDark(mc, p, rnd);
        }
    }

    private static void cross(Minecraft mc, boolean entering) {
        flash = 1;
        flashColor = type.glow;
        mc.getSoundManager().play(SimpleSoundInstance.forUI((entering ? ModSounds.ENTER : ModSounds.EXIT).get(), 1, .7f));
    }

    private static void startLoop(Minecraft mc) {
        SoundEvent s = switch (type) {
            case END -> ModSounds.END_HUM.get();         // a deep hum in the silence
            case NETHER -> ModSounds.FORGE.get();        // the forge's rumble and crackle
            case OVERWORLD -> ModSounds.EYE_WIND.get();  // soft wind high up
            case DEEP_DARK -> ModSounds.SCULK_HEART.get(); // a slow heartbeat
        };
        loop = new Loop(s, type);
        mc.getSoundManager().play(loop);
    }

    // ---- Nether: the forge ---------------------------------------------------------------------------------------

    private static void forge(Minecraft mc, LocalPlayer p, RandomSource rnd) {
        ClientLevel level = mc.level;
        for (int i = 0, n = (int) (4 * weight); i < n; i++) { // sparks rising like over a fire
            level.addParticle(rnd.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                    p.getX() + (rnd.nextFloat() - .5) * 14, p.getY() - 1 + rnd.nextFloat() * 4, p.getZ() + (rnd.nextFloat() - .5) * 14,
                    (rnd.nextFloat() - .5) * .02, .04 + rnd.nextFloat() * .08, (rnd.nextFloat() - .5) * .02);
        }
        if (rnd.nextInt(12) == 0) sound(mc, p, rnd, SoundEvents.CAMPFIRE_CRACKLE, .5f, .8f + rnd.nextFloat() * .4f, 4);
        if (--cooldown <= 0) { // distant voices of the Nether, never close and never loud
            cooldown = 120 + rnd.nextInt(180);
            SoundEvent v = switch (rnd.nextInt(4)) {
                case 0 -> SoundEvents.GHAST_AMBIENT;
                case 1 -> SoundEvents.PIGLIN_AMBIENT;
                case 2 -> SoundEvents.SOUL_ESCAPE.value();
                default -> SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value();
            };
            sound(mc, p, rnd, v, .25f, .6f + rnd.nextFloat() * .2f, 24);
        }
        // glowing footprints
        if (p.onGround() && Mth.square(p.getX() - lastPrintX) + Mth.square(p.getZ() - lastPrintZ) > .45 && PRINTS.size() < 64) {
            lastPrintX = p.getX();
            lastPrintZ = p.getZ();
            side = -side;
            PRINTS.add(new float[] {(float) p.getX(), (float) p.getY(), (float) p.getZ(), p.getYRot(), side, 0});
        }
    }

    // ---- Overworld: the eye of the storm -------------------------------------------------------------------------

    private static void eye(Minecraft mc, LocalPlayer p, RandomSource rnd) {
        ClientLevel level = mc.level;
        if (rnd.nextInt(40) == 0) sound(mc, p, rnd, SoundEvents.PARROT_AMBIENT, .35f, 1.4f + rnd.nextFloat() * .5f, 16); // birds
        if (rnd.nextInt(3) == 0) { // static around you
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (rnd.nextFloat() - .5) * 1.2, p.getY() + rnd.nextFloat() * 2,
                    p.getZ() + (rnd.nextFloat() - .5) * 1.2, 0, 0, 0);
        }
        Storm s = stormAround(p);
        if (s == null) return;
        for (int i = 0, n = (int) (3 * weight); i < n; i++) { // the air slowly turning around the axis
            double x = p.getX() + (rnd.nextFloat() - .5) * 20, z = p.getZ() + (rnd.nextFloat() - .5) * 20;
            double dx = x - s.x, dz = z - s.z, d = Math.sqrt(dx * dx + dz * dz) + 1e-3;
            level.addParticle(ParticleTypes.WHITE_ASH, x, p.getY() + rnd.nextFloat() * 5 - 1, z, -dz / d * .25, 0, dx / d * .25);
        }
    }

    /** Striking inside an Overworld storm throws a little harmless arc of sparks to where you aim. */

    public static void onAttack() {
        Minecraft mc = Minecraft.getInstance();
        if (weight(StormType.OVERWORLD) < .5f || mc.player == null || mc.hitResult == null) return;
        Vec3 from = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(.6)).add(0, -.3, 0);
        Vec3 to = mc.hitResult.getType() == HitResult.Type.MISS ? mc.player.getEyePosition().add(mc.player.getLookAngle().scale(4)) : mc.hitResult.getLocation();
        RandomSource rnd = mc.level.random;
        for (int i = 0; i <= 12; i++) {
            Vec3 at = from.lerp(to, i / 12.0).add((rnd.nextFloat() - .5) * .25, (rnd.nextFloat() - .5) * .25, (rnd.nextFloat() - .5) * .25);
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, 0, 0);
        }
        mc.level.playLocalSound(to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, .4f, 1.8f, false);
    }

    // ---- movement: the wall's air cushion and each storm's air -------------------------------------------------------

    public static void onPlayerTick(LocalPlayer p) {
        if (p.isSpectator() || p.isPassenger()) return;
        Storm s = stormAround(p);
        if (s == null) return;
        Vec3 v = p.getDeltaMovement();
        double dx = p.getX() - s.x, dz = p.getZ() - s.z, d = Math.sqrt(dx * dx + dz * dz) + 1e-3, nx = dx / d, nz = dz / d;
        // The cushion: a soft band around the wall. Moving across it, part of the speed through the wall is taken
        // away, more the deeper into the band, and a gentle push sends you back the way you came.
        float wall = s.radius() * .97f, half = Math.max(3, s.radius() * .12f), x = (float) ((d - wall) / half);
        if (Math.abs(x) < 1 && p.getY() > s.y - 8 && p.getY() < s.y + s.radius() * 3) {
            float w = (1 - x * x) * (1 - x * x);
            double vr = v.x * nx + v.z * nz;
            v = v.add(-nx * vr * .35 * w, 0, -nz * vr * .35 * w).add(nx * Math.signum(x) * .012 * w, 0, nz * Math.signum(x) * .012 * w);
            if (Math.abs(vr) > .05 && p.level().random.nextInt(3) == 0)
                p.level().addParticle(ParticleTypes.CLOUD, p.getX() - nx * .6, p.getY() + 1 + p.level().random.nextFloat() * .6, p.getZ() - nz * .6, -nx * .05, 0, -nz * .05);
        }
        float in = weight(s.type);
        if (in > .01f && !p.getAbilities().flying && !p.isInWater()) {
            switch (s.type) {
                case END -> { if (!p.onGround()) v = v.add(0, .04 * in, 0); } // half the gravity: long, floating jumps
                case NETHER -> {
                    if (!p.onGround() && v.y < 0) v = v.add(0, .02 * in, 0); // warm air slows the fall
                    if (--updraft <= 0 && hotBelow(p)) { // an updraft over fire tosses you up, once, then lets you fall
                        updraft = 40;
                        v = new Vec3(v.x, Math.max(v.y, .75), v.z);
                        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, .4f, 1.6f, false);
                    }
                }
                case OVERWORLD -> v = v.add(-nz * .004 * in, 0, nx * .004 * in); // the turning air leads you a little
            }
        }
        p.setDeltaMovement(v);
    }

    private static boolean hotBelow(LocalPlayer p) {
        BlockPos.MutableBlockPos pos = p.blockPosition().mutable();
        for (int i = 0; i < 6; i++, pos.move(0, -1, 0)) {
            BlockState b = p.level().getBlockState(pos);
            if (b.is(Blocks.FIRE) || b.is(Blocks.SOUL_FIRE) || b.is(Blocks.MAGMA_BLOCK) || b.is(Blocks.LAVA) || b.is(Blocks.CAMPFIRE) || b.is(Blocks.SOUL_CAMPFIRE)
                    || b.is(ModContent.HOT_CRUST.get()) && b.getValue(HotCrustBlock.AGE) < 3) return true;
            if (!b.isAir() && i > 0) return false; // only open air between you and the heat
        }
        return false;
    }

    /** The storm whose column (and wall band) the player is in or next to, or null. */
    private static Storm stormAround(LocalPlayer p) {
        for (Storm s : ClientStorms.all()) if (s.dist(p.getX(), p.getZ()) < s.radius() * 1.2f) return s;
        return null;
    }

    // ---- sounds --------------------------------------------------------------------------------------------------

    /**
     * Inside an End storm the world goes quiet: rain, thunder, creatures and ambience don't reach you. Inside a
     * Deep Dark storm everything is muffled down to a quarter (the storm's own sounds excepted).
     */

    public static SoundInstance onSound(SoundInstance s) {
        if (s == null || s instanceof Loop || HorizonsUnbound.MODID.equals(s.getLocation().getNamespace())) return s;
        if (weight(StormType.END) > .5f) {
            SoundSource src = s.getSource();
            if (src == SoundSource.WEATHER || src == SoundSource.HOSTILE || src == SoundSource.NEUTRAL || src == SoundSource.AMBIENT) return null;
        } else if (weight(StormType.DEEP_DARK) > .5f && !(s instanceof net.minecraft.client.resources.sounds.TickableSoundInstance)
                && s.getSource() != SoundSource.MUSIC && s.getSource() != SoundSource.MASTER) {
            return new Muffled(s, .25f);
        }
        return s;
    }

    /** Another sound, played quieter. */
    private record Muffled(SoundInstance s, float scale) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return s.getLocation(); }
        @Override public net.minecraft.client.sounds.WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager m) { return s.resolve(m); }
        @Override public net.minecraft.client.resources.sounds.Sound getSound() { return s.getSound(); }
        @Override public SoundSource getSource() { return s.getSource(); }
        @Override public boolean isLooping() { return s.isLooping(); }
        @Override public boolean isRelative() { return s.isRelative(); }
        @Override public int getDelay() { return s.getDelay(); }
        @Override public float getVolume() { return s.getVolume() * scale; }
        @Override public float getPitch() { return s.getPitch() * .85f; }
        @Override public double getX() { return s.getX(); }
        @Override public double getY() { return s.getY(); }
        @Override public double getZ() { return s.getZ(); }
        @Override public Attenuation getAttenuation() { return s.getAttenuation(); }
    }

    // ---- Deep Dark: the listening dark ----------------------------------------------------------------------------

    private static void deepDark(Minecraft mc, LocalPlayer p, RandomSource rnd) {
        if (rnd.nextInt(3) == 0) { // souls drifting up out of the dark
            mc.level.addParticle(ParticleTypes.SCULK_SOUL, p.getX() + (rnd.nextFloat() - .5) * 16, p.getY() - 1 + rnd.nextFloat() * 3,
                    p.getZ() + (rnd.nextFloat() - .5) * 16, 0, .02 + rnd.nextFloat() * .02, 0);
        }
        if (rnd.nextInt(10) == 0) mc.level.addParticle(ParticleTypes.SCULK_CHARGE_POP, p.getX() + (rnd.nextFloat() - .5) * 8,
                p.getY() + rnd.nextFloat() * 2, p.getZ() + (rnd.nextFloat() - .5) * 8, 0, .01, 0);
    }

    private static void sound(Minecraft mc, LocalPlayer p, RandomSource rnd, SoundEvent e, float volume, float pitch, float dist) {
        float a = rnd.nextFloat() * Mth.TWO_PI;
        mc.level.playLocalSound(p.getX() + Mth.cos(a) * dist, p.getY() + 2, p.getZ() + Mth.sin(a) * dist, e, SoundSource.AMBIENT, volume * weight, pitch, false);
    }

    /** The storm's own ambience, following the player and fading with the inside weight. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final StormType kind;

        Loop(SoundEvent e, StormType kind) {
            super(e, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.kind = kind;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = .001f;
            pitch = 1;
        }

        @Override
        public void tick() {
            float w = weight(kind);
            volume = Math.max(.001f, w * (kind == StormType.OVERWORLD ? .35f : .6f) * StormConfig.VOLUME.get().floatValue());
            if (w < .01f) stop();
        }
    }

    // ---- drawing: End shards and Nether footprints -------------------------------------------------------------------

    public static void onRender(Vec3 cam, float partial) {
        if (weight < .02f || type == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = (mc.player.tickCount + partial) / 20f;
        if (type == StormType.END) shards(mc, cam, t);
        if (!PRINTS.isEmpty()) footprints(cam);
    }

    /**
     * Pieces of the End drifting around you, slowly turning: small cubes with the blocks' own textures, drawn in our
     * own pass (shared buffers are batched by other mods and may be flushed elsewhere).
     */
    private static void shards(Minecraft mc, Vec3 cam, float t) {
        Vec3 c = mc.player.getPosition(1).add(0, 1, 0);
        RenderType rt = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        BufferBuilder b = Tesselator.getInstance().begin(rt.mode(), rt.format());
        PoseStack pose = new PoseStack();
        for (int i = 0; i < 10; i++) {
            float rad = 3 + (i * 37 % 10) * .35f, ang = i * 2.39f + t * (.15f + (i % 3) * .05f), size = .32f + (i * 13 % 7) * .04f;
            pose.pushPose();
            pose.translate(c.x - cam.x + Mth.cos(ang) * rad, c.y - cam.y + (i % 5) * .7f - 1.2f + Mth.sin(t * .7f + i) * .3f, c.z - cam.z + Mth.sin(ang) * rad);
            pose.mulPose(Axis.YP.rotationDegrees(t * 40 + i * 30));
            pose.mulPose(Axis.XP.rotationDegrees(t * 25 + i * 50));
            float sc = size * weight;
            pose.scale(sc, sc, sc);
            cube(b, pose.last(), mc.getBlockRenderer().getBlockModel(SHARDS[i % SHARDS.length]).getParticleIcon());
            pose.popPose();
        }
        MeshData mesh = b.build();
        if (mesh != null) rt.draw(mesh);
    }

    /** A unit cube centred on the pose origin, every face showing the sprite. */
    private static void cube(BufferBuilder b, PoseStack.Pose pose, TextureAtlasSprite sprite) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float[][] faces = { // per face: normal, then 4 corners
                {0, 1, 0, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1}, {0, -1, 0, -1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1},
                {0, 0, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1}, {0, 0, -1, 1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1},
                {1, 0, 0, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1}, {-1, 0, 0, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1}};
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (float[] f : faces) {
            for (int k = 0; k < 4; k++) {
                b.addVertex(pose, f[3 + k * 3] * .5f, f[4 + k * 3] * .5f, f[5 + k * 3] * .5f).setColor(-1).setUv(uv[k][0], uv[k][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, f[0], f[1], f[2]);
            }
        }
    }

    /** Footprints glowing like hot metal, cooling over three seconds. */
    private static void footprints(Vec3 cam) {
        RenderType rt = StormRenderer.glowType();
        BufferBuilder b = Tesselator.getInstance().begin(rt.mode(), rt.format());
        for (int pass = 0; pass < 2; pass++) for (float[] f : PRINTS) { // twice: additive, so twice as bright
            float fade = 1 - f[5] / 60, k = fade * fade * weight;
            int color = 0xFF000000 | (int) (255 * k) << 16 | (int) (110 * k) << 8 | (int) (20 * k);
            float yaw = f[3] * Mth.DEG_TO_RAD, fx = -Mth.sin(yaw), fz = Mth.cos(yaw), sx = fz, sz = -fx; // forward, side
            float cx = f[0] - (float) cam.x + sx * .16f * f[4], cy = f[1] - (float) cam.y + .03f, cz = f[2] - (float) cam.z + sz * .16f * f[4];
            float lx = fx * .22f, lz = fz * .22f, wx = sx * .1f, wz = sz * .1f;
            vertex(b, cx - lx - wx, cy, cz - lz - wz, 0, 0, color);
            vertex(b, cx - lx + wx, cy, cz - lz + wz, 1, 0, color);
            vertex(b, cx + lx + wx, cy, cz + lz + wz, 1, 1, color);
            vertex(b, cx + lx - wx, cy, cz + lz - wz, 0, 1, color);
        }
        MeshData mesh = b.build();
        if (mesh != null) {
            rt.draw(mesh);
        }
    }

    private static void vertex(BufferBuilder b, float x, float y, float z, float u, float v, int color) {
        b.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 1, 0);
    }

    /** The flash of the storm's color around the screen edge when crossing its wall. */

    public static void drawCrossing(net.minecraft.client.gui.GuiGraphics g) {
                if (flash <= 0) return;
                float a = flash * flash;
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                g.setColor((flashColor >> 16 & 255) / 255f * a, (flashColor >> 8 & 255) / 255f * a, (flashColor & 255) / 255f * a, 1);
                g.blit(VIGNETTE, 0, 0, -90, 0, 0, g.guiWidth(), g.guiHeight(), g.guiWidth(), g.guiHeight());
                g.setColor(1, 1, 1, 1);
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
    }
}
