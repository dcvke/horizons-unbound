package com.horizonsunbound.compat;

import com.horizonsunbound.effect.SculkEffects;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's voice as a noise for the Deep Dark storm (VoiceHearing decodes it; nothing here needs the voice chat
 * mod). Called on the voice chat's thread: decides fast and hands the noise to the server thread.
 */
public final class VoiceNoise {
    /** Peak a voice must reach to be heard, as a share of full scale (-30 dBFS: ordinary speech, not breathing or hiss). */
    static final double LOUD = Math.pow(10, -30 / 20.0);
    private static final long INSIDE_MS = 1000, EVERY_MS = 250; // how fresh "inside" must be; how often one voice is reported
    private static final Map<UUID, Long> REPORTED = new ConcurrentHashMap<>();

    private VoiceNoise() {}

    /** True if the player was inside a Deep Dark storm a moment ago: only then is their voice worth decoding. */
    public static boolean listening(ServerPlayer player) {
        Long inside = SculkEffects.LISTENED_TO.get(player.getUUID());
        return inside != null && System.currentTimeMillis() - inside <= INSIDE_MS;
    }

    /** One decoded frame of the player's voice (16-bit PCM): loud enough, it is a noise the storm hears. */
    public static boolean voice(ServerPlayer player, short[] pcm) {
        if (!listening(player) || !loud(pcm)) return false;
        long now = System.currentTimeMillis();
        Long last = REPORTED.get(player.getUUID());
        if (last != null && now - last < EVERY_MS) return false;
        REPORTED.put(player.getUUID(), now);
        player.getServer().execute(() -> SculkEffects.hear(player.serverLevel(), player));
        return true;
    }

    /** True if the frame's peak reaches LOUD. */
    static boolean loud(short[] pcm) {
        if (pcm == null) return false;
        int peak = 0;
        for (short s : pcm) peak = Math.max(peak, Math.abs((int) s));
        return peak >= LOUD * 32768;
    }
}
