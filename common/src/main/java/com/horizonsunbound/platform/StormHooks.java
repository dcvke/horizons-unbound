package com.horizonsunbound.platform;

import com.horizonsunbound.Storm;
import com.horizonsunbound.api.StormListener;
import net.minecraft.server.level.ServerLevel;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StormHooks {
    private static final CopyOnWriteArrayList<StormListener> LISTENERS = new CopyOnWriteArrayList<>();
    private StormHooks() {}
    public static void listen(StormListener listener) { LISTENERS.add(Objects.requireNonNull(listener)); }
    public static boolean announce(ServerLevel level, Storm storm) {
        boolean allowed = Services.EVENTS.announce(level, storm);
        for (StormListener listener : LISTENERS) allowed &= listener.announce(level, storm);
        return allowed;
    }
    public static void born(ServerLevel level, Storm storm) {
        Services.EVENTS.born(level, storm);
        for (StormListener listener : LISTENERS) listener.born(level, storm);
    }
    public static void dying(ServerLevel level, Storm storm) {
        Services.EVENTS.dying(level, storm);
        for (StormListener listener : LISTENERS) listener.dying(level, storm);
    }
    public static void end(ServerLevel level, Storm storm) {
        Services.EVENTS.end(level, storm);
        for (StormListener listener : LISTENERS) listener.end(level, storm);
    }
    public static void merge(ServerLevel level, Storm storm, Storm first, Storm second) {
        Services.EVENTS.merge(level, storm, first, second);
        for (StormListener listener : LISTENERS) listener.merge(level, storm, first, second);
    }
}
