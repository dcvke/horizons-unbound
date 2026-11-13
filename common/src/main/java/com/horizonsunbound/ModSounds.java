package com.horizonsunbound;

import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** The mod's own sounds (assets/horizons_unbound/sounds.json; generated, see tools/make_sounds.py). */
public final class ModSounds {
    public static final Map<String, Supplier<SoundEvent>> ALL = new LinkedHashMap<>();

    public static final Supplier<SoundEvent>
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

    private static Supplier<SoundEvent> sound(String name) {
        Supplier<SoundEvent> sound = Suppliers.memoize(() -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HorizonsUnbound.MODID, name)));
        ALL.put(name, sound);
        return sound;
    }

}
