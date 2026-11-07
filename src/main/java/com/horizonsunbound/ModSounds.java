package com.horizonsunbound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own sounds (assets/horizons_unbound/sounds.json; generated, see tools/make_sounds.py). */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HorizonsUnbound.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            RUMBLE = sound("storm.rumble"),         // a storm heard from afar, louder the closer you get
            ENTER = sound("storm.enter"),           // crossing the wall inwards
            EXIT = sound("storm.exit"),             // and back out
            OMEN_WIND = sound("storm.omen"),        // the wind rising before a storm
            MERGE = sound("storm.merge"),           // two storms becoming one
            BIRTH = sound("storm.birth"),           // a storm being born: the in-breath, the crack at ignition, the roar
            END_HUM = sound("inside.end"),          // the hum in the silence of an End storm
            FORGE = sound("inside.nether"),         // the forge inside a Nether storm
            EYE_WIND = sound("inside.overworld"),   // the soft wind in the eye of an Overworld storm
            SCULK_HEART = sound("inside.deep_dark"),// the heartbeat inside a Deep Dark storm
            SCULK_PULSE = sound("storm.sculk_pulse");// a Deep Dark storm answering a noise

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HorizonsUnbound.MODID, name)));
    }

    static void register(IEventBus modBus) { SOUNDS.register(modBus); }
}
