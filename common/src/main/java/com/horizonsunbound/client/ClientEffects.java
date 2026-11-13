package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.ModContent;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormType;
import com.horizonsunbound.StormsConfig;
import com.horizonsunbound.effect.HotCrustBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import com.horizonsunbound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client side of the storm effects that need no server: Nether ash air (short sight, ash trails behind anything
 * that moves), the Overworld storm's rain pockets (rain fades in dry ones, sight shortens in wet ones), the blue
 * glow on ground about to be struck, and sparks around End seams.
 */

public final class ClientEffects {
    private static final DustParticleOptions CHARGE = new DustParticleOptions(new Vector3f(.35f, .75f, 1f), 1.3f);
    private static float ash, wet; // 0..1 at the camera, eased
    private static final it.unimi.dsi.fastutil.longs.LongOpenHashSet HEARD = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(); // pulses already shown

    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ash = wet = 0;
            Storm.clientRainFactor = 1;
            return;
        }
        if (mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        float ashT = 0, wetT = 0, rainT = 1;
        for (Storm s : ClientStorms.all()) {
            if (!StormsConfig.effects(s.type)) continue;
            float w = 1 - smooth((float) (s.dist(cam.x, cam.z) - s.zone() * .8f) / (s.zone() * .2f));
            if (s.type == StormType.NETHER) ashT = Math.max(ashT, w);
            if (s.type == StormType.OVERWORLD && w > 0) {
                float wt = s.wetness(cam.x, cam.z);
                rainT = Math.min(rainT, 1 - w * (1 - wt));
                wetT = Math.max(wetT, w * wt);
            }
            for (Storm.Mark m : s.marks) marks(level, rnd, s, m, cam);
        }
        // in the eye of any storm it doesn't rain, and the forge's air inside a Nether storm is clear
        rainT *= 1 - InsideStorm.weight();
        ashT *= 1 - InsideStorm.weight(StormType.NETHER);
        ash += (ashT - ash) * .05f;
        wet += (wetT - wet) * .05f;
        Storm.clientRainFactor += (rainT - Storm.clientRainFactor) * .05f;
        if (ash > .05f) ashAir(mc, level, rnd, cam);
    }

    private static void marks(ClientLevel level, RandomSource rnd, Storm s, Storm.Mark m, Vec3 cam) {
        double dx = m.x() + .5 - cam.x, dz = m.z() + .5 - cam.z;
        if (dx * dx + dz * dz > 80 * 80) return;
        if (m.kind() == Storm.Mark.SEAM) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, m.x() + .3 + rnd.nextFloat() * .4, m.y() + rnd.nextFloat() * 2.6, m.z() + .3 + rnd.nextFloat() * .4, 0, 0, 0);
        } else if (m.kind() == Storm.Mark.STRIKE && m.a() > s.age) { // the ground and its plants glow blue before the strike
            for (int i = 0; i < 8; i++) {
                int x = m.x() + rnd.nextInt(7) - 3, z = m.z() + rnd.nextInt(7) - 3;
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + rnd.nextFloat() * .4;
                level.addParticle(i < 6 ? CHARGE : ParticleTypes.ELECTRIC_SPARK, x + rnd.nextFloat(), y, z + rnd.nextFloat(), 0, .03, 0);
            }
        } else if (m.kind() == Storm.Mark.PULSE && HEARD.add(m.x() * 31L + m.z() * 961L + m.y() * 29791L + m.a() * 923521L)) {
            // a Deep Dark storm heard this: a vibration runs from the noise to the column, and the storm clicks back
            if (HEARD.size() > 256) HEARD.clear();
            BlockPos heart = BlockPos.containing(s.x, m.y(), s.z);
            level.addParticle(new VibrationParticleOption(new BlockPositionSource(heart), 30), m.x() + .5, m.y() + .5, m.z() + .5, 0, 0, 0);
            level.playLocalSound(m.x() + .5, m.y(), m.z() + .5, ModSounds.SCULK_PULSE.get(), SoundSource.AMBIENT, .7f, 1, false);
        }
    }

    /** Ash drifting around the camera, and trails and swirls behind every creature that moves through it. */
    private static void ashAir(Minecraft mc, ClientLevel level, RandomSource rnd, Vec3 cam) {
        for (int i = 0, n = (int) (14 * ash); i < n; i++) {
            level.addParticle(i % 3 == 0 ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, cam.x + (rnd.nextFloat() - .5) * 32,
                    cam.y + (rnd.nextFloat() - .3) * 16, cam.z + (rnd.nextFloat() - .5) * 32, 0, 0, 0);
        }
        if (level.getGameTime() % 2 != 0) return;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(cam, cam).inflate(28))) {
            double mx = e.getX() - e.xo, mz = e.getZ() - e.zo;
            if (mx * mx + mz * mz < .002 || e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
            for (int i = 0; i < 3; i++) {
                float a = rnd.nextFloat() * Mth.TWO_PI;
                level.addParticle(i == 0 ? ParticleTypes.SMOKE : ParticleTypes.WHITE_ASH, e.getX() - mx * 2 + Mth.cos(a) * .3,
                        e.getY() + rnd.nextFloat() * e.getBbHeight(), e.getZ() - mz * 2 + Mth.sin(a) * .3, -mx * .3 + Mth.cos(a) * .03, .01, -mz * .3 + Mth.sin(a) * .03);
            }
        }
    }

    /** Ash air and the rain of wet pockets close the view in (the storm itself keeps its own haze). */

    public static float fogDistance(float far) {
        if (ash < .01f && wet < .01f) return far;
        return Math.min(Mth.lerp(ash, far, Math.min(far, 40)), Mth.lerp(wet * .6f, far, Math.min(far, 64)));
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
}
