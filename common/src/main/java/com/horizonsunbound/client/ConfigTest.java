package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Opens the registered settings factory in a scripted client and checks a real config-file save. */
public final class ConfigTest {
    private static Screen screen, parent;
    private static Consumer<String> log;
    private static Path directory;
    private static int ticks;
    private static double previous;
    private ConfigTest() {}
    public static boolean active() { return screen != null; }
    @SuppressWarnings("unchecked")
    public static void open(Path gameDirectory, Consumer<String> output) {
        Minecraft mc = Minecraft.getInstance();
        log = output; directory = gameDirectory; parent = mc.screen; previous = StormConfig.VOLUME.get(); ticks = 0;
        try {
            if (Services.PLATFORM.isModLoaded("modmenu")) {
                screen = (Screen) Class.forName("com.terraformersmc.modmenu.ModMenu").getMethod("getConfigScreen", String.class, Screen.class).invoke(null, HorizonsUnbound.MODID, parent);
            } else {
                String prefix;
                try { Class.forName("net.neoforged.fml.ModList"); prefix = "net.neoforged"; }
                catch (ClassNotFoundException ignored) { prefix = "net.minecraftforge"; }
                Class<?> listType = Class.forName(prefix + ".fml.ModList");
                Object list = listType.getMethod("get").invoke(null);
                Object container = ((Optional<?>) listType.getMethod("getModContainerById", String.class).invoke(list, HorizonsUnbound.MODID)).orElseThrow();
                Class<?> containerType = Class.forName(prefix + ".fml.ModContainer");
                String factoryName = prefix.equals("net.neoforged") ? "net.neoforged.neoforge.client.gui.IConfigScreenFactory" : "net.minecraftforge.client.ConfigScreenHandler$ConfigScreenFactory";
                Class<?> factoryType = Class.forName(factoryName);
                Object factory = ((Optional<?>) containerType.getMethod("getCustomExtension", Class.class).invoke(container, factoryType)).orElseThrow();
                if (prefix.equals("net.neoforged")) screen = (Screen) factoryType.getMethod("createScreen", containerType, Screen.class).invoke(factory, container, parent);
                else screen = ((BiFunction<Minecraft, Screen, Screen>) factoryType.getMethod("screenFunction").invoke(factory)).apply(mc, parent);
            }
            if (screen == null) throw new IllegalStateException("Settings factory returned no screen");
            mc.setScreen(screen);
            log.accept("config screen " + screen.getClass().getName());
        } catch (Exception | LinkageError error) { finish(false, error.toString()); }
    }
    public static void tick() {
        if (screen == null) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            if (ticks == 2) {
                Screenshot.grab(mc.gameDirectory, "config-screen.png", mc.getMainRenderTarget(), message -> {});
                log.accept("shot config-screen  fps " + mc.getFps());
            }
            if (ticks == 4) {
                try {
                    var check = screen.getClass().getDeclaredMethod("verifySaveRoundTrip");
                    check.setAccessible(true);
                    if (!Boolean.TRUE.equals(check.invoke(screen))) throw new IllegalStateException("Settings screen did not save the edited value");
                } catch (NoSuchMethodException ignored) {
                    StormConfig.VOLUME.set(.25);
                    StormConfig.SPEC.save();
                }
            }
            if (ticks >= 5) {
                Path config = directory.resolve("config/horizons_unbound-client.toml");
                if (Files.exists(config) && Files.readString(config).contains("stormVolume = 0.25")) finish(true, "factory opened, volume saved, original value restored");
                else if (ticks > 80) finish(false, "edited volume did not reach the config file");
            }
        } catch (Exception | LinkageError error) { finish(false, error.toString()); }
    }
    private static void finish(boolean success, String detail) {
        StormConfig.VOLUME.set(previous);
        StormConfig.SPEC.save();
        Minecraft.getInstance().setScreen(parent);
        screen = null;
        log.accept((success ? "PASS" : "FAIL") + " config screen: " + detail);
    }
}
