package com.horizonsunbound.effect;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.ModSounds;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormManager;
import com.horizonsunbound.StormType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/**
 * A Deep Dark storm listens. Inside its column sounds are muffled (client side, InsideStorm), and every noise a
 * player makes there - steps unless sneaking, jumps, hits, placed or broken blocks - is heard: the storm answers
 * with a pulse that runs from the noise to the column (clients show it, see the PULSE marks). Keep making noise and
 * it grows annoyed: a few seconds of Darkness, never any damage.
 */

public final class SculkEffects {
    private static final int HEARD_FOR = 40;   // ticks a pulse mark stays
    private static final int PATIENCE = 4;     // noises within 10 s before the storm answers with Darkness

    private SculkEffects() {}

    static void tick(ServerLevel level, StormManager m, Storm s, List<ServerPlayer> near) {
        if (s.age % 10 == 0 && s.marks.removeIf(k -> k.kind() == Storm.Mark.PULSE && s.age - k.a() > HEARD_FOR)) m.resync();
    }

    public static void onGameEvent(ServerLevel level, net.minecraft.world.entity.Entity cause, net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event) {
        if (!(cause instanceof ServerPlayer p) || p.isSpectator()) return;
        if (!event.is(GameEventTags.VIBRATIONS)) return;
        if (p.isSteppingCarefully() && event.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) return;
        StormManager m = StormManager.get(level);
        Storm s = null;
        for (Storm st : m.storms()) if (st.has(StormType.DEEP_DARK) && st.inside(p.getX(), p.getY(), p.getZ())) s = st;
        if (s == null) return;
        long now = level.getGameTime();
        CompoundTag d = com.horizonsunbound.platform.Services.STORAGE.data(p);
        if (d.getLong("ds_heard") > now) return; // one pulse per half second is plenty
        d.putLong("ds_heard", now + 10);
        s.marks.add(new Storm.Mark(Storm.Mark.PULSE, p.getBlockX(), p.getBlockY() + 1, p.getBlockZ(), s.age));
        m.resync();
        // patience: noises counted within a 10 second window
        int count = now - d.getLong("ds_noise_since") > 200 ? 1 : d.getInt("ds_noise") + 1;
        if (count == 1) d.putLong("ds_noise_since", now);
        d.putInt("ds_noise", count);
        if (count >= PATIENCE) {
            d.putInt("ds_noise", 0);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
            level.playSound(null, p.blockPosition(), ModSounds.SCULK_PULSE.get(), SoundSource.AMBIENT, 1.4f, .6f);
        }
    }
}
