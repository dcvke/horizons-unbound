package com.horizonsunbound.platform;

import java.util.ServiceLoader;

public final class Services {
    public static final Platform PLATFORM = load(Platform.class);
    public static final Network NETWORK = load(Network.class);
    public static final Storage STORAGE = load(Storage.class);
    public static final Protection PROTECTION = load(Protection.class);
    public static final StormEvents EVENTS = load(StormEvents.class);

    private Services() {}
    private static <T> T load(Class<T> type) {
        return ServiceLoader.load(type).findFirst().orElseThrow(() -> new IllegalStateException("Missing platform service: " + type.getName()));
    }
}
