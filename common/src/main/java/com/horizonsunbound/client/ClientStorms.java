package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.Storm;
import net.minecraft.client.Minecraft;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The storms of the client's current dimension as last sent by the server, moved along between updates. */

public final class ClientStorms {
    private static final Map<UUID, Storm> STORMS = new HashMap<>();
    private static final Map<UUID, Storm> OMENS = new HashMap<>(); // storms about to rise: only their omens show

    static { Storm.CLIENT = STORMS.values(); } // a live view

    /** Storms that have risen. */
    public static Collection<Storm> all() { return STORMS.values(); }

    /** A storm, risen or about to rise (then omen > 0), or null. */
    public static Storm get(UUID id) {
        Storm s = STORMS.get(id);
        return s != null ? s : OMENS.get(id);
    }

    /** Storms about to rise (omen > 0). */
    public static Collection<Storm> omens() { return OMENS.values(); }

    /**
     * Takes the server's full list. Known storms keep their own object (so their render cache and smooth motion
     * survive) and only take over the new position, velocity and age; the payload's objects are never kept.
     */
    public static void accept(List<Storm> update) {
        Map<UUID, Storm> next = new HashMap<>();
        OMENS.clear();
        for (Storm u : update) {
            if (u.omen > 0) {
                OMENS.put(u.id, u);
                continue;
            }
            Storm s = STORMS.get(u.id);
            if (s == null || s.diameter != u.diameter || s.type != u.type || s.mix != u.mix) {
                s = new Storm(u.id, u.type, u.diameter, u.x, u.y, u.z);
                s.mix = u.mix;
            }
            s.x = u.x;
            s.y = u.y;
            s.z = u.z;
            s.vx = u.vx;
            s.vy = u.vy;
            s.vz = u.vz;
            s.age = u.age;
            s.life(u.life());
            s.grand = u.grand;
            s.marks.clear();
            s.marks.addAll(u.marks);
            next.put(u.id, s);
        }
        STORMS.clear();
        STORMS.putAll(next);
    }

    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (Storm s : STORMS.values()) s.clientStep();
        for (Storm o : OMENS.values()) if (o.omen > 1) o.omen--;
        com.horizonsunbound.compat.MapMarks.tick();
    }

    public static void onLogout() {
        STORMS.clear();
        OMENS.clear();
    }
}
