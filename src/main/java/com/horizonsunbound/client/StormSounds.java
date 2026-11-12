package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.ModSounds;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What storms sound like from outside: each storm's deep rumble, coming from its wall, growing as you get closer
 * and dying down in its eye; and before a storm rises, a rising wind with distant thunder (and gusts you can see).
 */
@EventBusSubscriber(modid = HorizonsUnbound.MODID, value = Dist.CLIENT)
public final class StormSounds {
    private static final Map<UUID, Rumble> RUMBLES = new HashMap<>();
    private static final java.util.Set<UUID> BORN = new java.util.HashSet<>(); // births already heard
    private static Loop omenWind;

    private StormSounds() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            RUMBLES.clear();
            BORN.clear();
            omenWind = null;
            return;
        }
        if (mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Storm s : ClientStorms.all()) {
            // a storm just born: its birth sound, timed so the crack falls on the ignition
            if (s.age < 40 && BORN.add(s.id)) {
                float v = Mth.clamp(1.2f - (float) s.dist(cam.x, cam.z) / (s.radius() * StormRenderer.CAP * 2.5f), .15f, 1);
                mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.BIRTH.get().getLocation(), SoundSource.AMBIENT, v * StormConfig.VOLUME.get().floatValue(), 1,
                        SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, s.x, s.y + s.radius(), s.z, false));
            }
            Rumble r = RUMBLES.get(s.id);
            if ((r == null || r.isStopped()) && Rumble.volume(s, cam) > .01f) {
                r = new Rumble(s);
                RUMBLES.put(s.id, r);
                mc.getSoundManager().play(r);
            }
        }
        RUMBLES.values().removeIf(AbstractTickableSoundInstance::isStopped);
        float omen = StormAtmosphere.omen();
        if (omen > .02f && (omenWind == null || omenWind.isStopped())) {
            omenWind = new Loop(ModSounds.OMEN_WIND.get());
            mc.getSoundManager().play(omenWind);
        }
        if (omen > .02f) gusts(level, mc, omen, cam);
    }

    /** Wind you can see picking up, and now and then far thunder, the closer the storm is to rising. */
    private static void gusts(ClientLevel level, Minecraft mc, float omen, Vec3 cam) {
        RandomSource rnd = level.random;
        Storm o = null;
        for (Storm s : ClientStorms.omens()) if (o == null || s.dist(cam.x, cam.z) < o.dist(cam.x, cam.z)) o = s;
        if (o == null) return;
        double dx = o.x - cam.x, dz = o.z - cam.z, d = Math.sqrt(dx * dx + dz * dz) + 1e-3;
        for (int i = 0, n = (int) (6 * omen); i < n; i++) { // the wind blows towards the coming storm
            level.addParticle(ParticleTypes.WHITE_ASH, cam.x + (rnd.nextFloat() - .5) * 24, cam.y + (rnd.nextFloat() - .3) * 8,
                    cam.z + (rnd.nextFloat() - .5) * 24, dx / d * .6 * omen, .02, dz / d * .6 * omen);
        }
        if (omen > .4f && rnd.nextFloat() < .012f * omen) {
            level.playLocalSound(cam.x + dx / d * 60, cam.y + 20, cam.z + dz / d * 60, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, .35f * omen, .6f, false);
        }
    }

    /** One storm's rumble, positioned on its wall nearest the listener. */
    private static final class Rumble extends AbstractTickableSoundInstance {
        private final UUID id;

        Rumble(Storm s) {
            super(ModSounds.RUMBLE.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            id = s.id;
            looping = true;
            delay = 0;
            attenuation = Attenuation.NONE; // the falloff is ours (volume()): heard much farther than vanilla sounds
            volume = .001f;
            pitch = pitch(s.type) * (s.mix != null ? (pitch(s.mix) + 1) / 2 : 1);
        }

        private static float pitch(StormType t) {
            return switch (t) {
                case END -> .85f;
                case NETHER -> .95f;
                case OVERWORLD -> 1;
                case DEEP_DARK -> .7f;
            };
        }

        /** Heard from about the edge of its cloud cap, swelling up to its wall; much quieter in its eye. */
        static float volume(Storm s, Vec3 cam) {
            float wall = (float) Math.max(0, s.dist(cam.x, cam.z) - s.radius()), range = s.radius() * StormRenderer.CAP * 1.3f;
            float v = Mth.clamp(1 - wall / range, 0, 1);
            return v * v * .9f * (1 - .7f * InsideStorm.weight(s.type)) * StormBirth.presence(s.age / 20f) * (1 - s.dying()) * StormConfig.VOLUME.get().floatValue();
        }

        @Override
        public void tick() {
            Storm s = null;
            for (Storm st : ClientStorms.all()) if (st.id.equals(id)) s = st;
            Minecraft mc = Minecraft.getInstance();
            if (s == null || mc.player == null) {
                stop();
                return;
            }
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double dx = cam.x - s.x, dz = cam.z - s.z, d = Math.sqrt(dx * dx + dz * dz) + 1e-3, r = Math.min(d, s.radius());
            x = s.x + dx / d * r;
            z = s.z + dz / d * r;
            y = Mth.clamp(cam.y, s.y, s.y + s.radius() * 3);
            volume = Math.max(.001f, volume(s, cam));
            if (volume <= .002f) stop();
        }
    }

    /** The omen wind, swelling with the darkness of a coming storm. */
    private static final class Loop extends AbstractTickableSoundInstance {
        Loop(SoundEvent e) {
            super(e, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = .001f;
        }

        @Override
        public void tick() {
            float o = StormAtmosphere.omen();
            volume = Math.max(.001f, o * .8f);
            if (o <= .01f) stop();
        }
    }
}
