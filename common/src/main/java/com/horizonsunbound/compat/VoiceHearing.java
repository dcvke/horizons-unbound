package com.horizonsunbound.compat;

import com.horizonsunbound.HorizonsUnbound;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat: a Deep Dark storm hears players talking inside it, the same as their steps (VoiceNoise).
 * Whispering is not heard, as sneaking isn't. Only the voices of players inside such a storm are decoded.
 * Simple Voice Chat finds this class itself (the annotation on NeoForge and Forge, the "voicechat" entrypoint on
 * Fabric); without it the class is never loaded.
 */
@ForgeVoicechatPlugin
public final class VoiceHearing implements VoicechatPlugin {
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private VoicechatApi api;

    @Override public String getPluginId() { return HorizonsUnbound.MODID; }

    @Override public void initialize(VoicechatApi api) { this.api = api; }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || event.getPacket().isWhispering()) return;
        if (!(sender.getPlayer().getPlayer() instanceof ServerPlayer player)) return;
        if (!VoiceNoise.listening(player)) {
            OpusDecoder old = decoders.remove(player.getUUID()); // out of the storm: a fresh decoder next time
            if (old != null) old.close();
            return;
        }
        OpusDecoder decoder = decoders.computeIfAbsent(player.getUUID(), id -> api.createDecoder());
        VoiceNoise.voice(player, decoder.decode(event.getPacket().getOpusEncodedData()));
    }
}
