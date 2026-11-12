package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dev-only scripted client test (spectator, so /tp places the camera exactly) (./gradlew runClientTest -Pscenario=name): makes a fresh world, plays a scenario from
 * tools/scenarios/name.txt, takes screenshots from the game's own frame (so the window may be hidden or behind
 * others), writes run-test/test-report.txt and quits. Does nothing unless -Dhu.test.script is set.
 * <pre>
 * # comment
 * /any command      run as the player (op)
 * wait 40           server ticks
 * shot name         run-test/screenshots/name.png, and the fps in the report
 * note text         a line in the report
 * hud on|off        show the HUD and hand (hidden by default)
 * punch             hold the attack button
 * trace on|off      log the camera every tick ("cam time x y z yaw pitch", time: the cutscene's clock while one runs)
 * Sound is muted; every sound the game starts is listed in the report instead.
 * </pre>
 */
@EventBusSubscriber(modid = HorizonsUnbound.MODID, value = Dist.CLIENT)
public final class ClientTest {
    private static final String SCRIPT = System.getProperty("hu.test.script");
    private static final boolean HIDDEN = !"false".equals(System.getProperty("hu.test.hidden"));
    private static final long TIMEOUT_MS = Long.getLong("hu.test.timeout", 300) * 1000;
    private static final String WORLD = "hutest";

    private static List<String> steps;
    private static int step, waitUntil, serverTicks;
    private static boolean worldStarted, hidden, trace, inCutscene;
    private static long startMs;
    private static Path report;

    private ClientTest() {}

    @SubscribeEvent
    static void onScreen(ScreenEvent.Init.Post e) {
        if (SCRIPT == null || worldStarted || !(e.getScreen() instanceof TitleScreen || e.getScreen() instanceof AccessibilityOnboardingScreen)) return;
        worldStarted = true;
        Minecraft mc = Minecraft.getInstance();
        try {
            steps = Files.readAllLines(Path.of(SCRIPT)).stream().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
            report = mc.gameDirectory.toPath().resolve("test-report.txt");
            Files.writeString(report, "scenario " + SCRIPT + "\n");
            deleteTree(mc.gameDirectory.toPath().resolve("screenshots"));
            deleteTree(mc.getLevelSource().getBaseDir().resolve(WORLD));
        } catch (IOException ex) {
            log("FAIL setup: " + ex);
            mc.stop();
            return;
        }
        // the test must not pause, grab the mouse or make noise when it isn't the window in front
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.hideGui = true;
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings(WORLD, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(1234L, false, false), WorldPresets::createNormalWorldDimensions, e.getScreen());
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post e) {
        if (SCRIPT == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (startMs == 0) startMs = System.currentTimeMillis();
        if (!hidden) {
            hidden = true;
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0); // silent for the user; the report lists what played
            mc.options.save(); // run-test/options.txt only: the next run starts muted too
            if (HIDDEN) GLFW.glfwHideWindow(mc.getWindow().getWindow()); // the frame is rendered into the game's own buffer, so screenshots work hidden too
        }
        if (System.currentTimeMillis() - startMs > TIMEOUT_MS) {
            log("FAIL timeout at step " + step + (steps != null && step < steps.size() ? ": " + steps.get(step) : ""));
            mc.stop();
            return;
        }
        if (Cutscene.active() != inCutscene) log((inCutscene = Cutscene.active()) ? "cutscene started" : "cutscene ended");
        if (steps == null || mc.player == null || mc.screen != null || mc.getSingleplayerServer() == null) return;
        // server ticks: the server may stall (generating chunks) while the client goes on, and the storms run on its clock
        serverTicks = mc.getSingleplayerServer().getTickCount();
        if (trace) { // the camera as last drawn, every tick
            var cam = mc.gameRenderer.getMainCamera();
            log(String.format(java.util.Locale.ROOT, "cam %.4f %.3f %.3f %.3f %.3f %.3f", Cutscene.active() ? Cutscene.lastT : serverTicks / 20f,
                    cam.getPosition().x, cam.getPosition().y, cam.getPosition().z, cam.getYRot(), cam.getXRot()));
        }
        if (serverTicks < waitUntil) return;
        while (step < steps.size()) {
            String s = steps.get(step++);
            String arg = s.contains(" ") ? s.substring(s.indexOf(' ') + 1).strip() : "";
            if (s.startsWith("/")) mc.player.connection.sendCommand(s.substring(1));
            else if (s.startsWith("wait ")) { waitUntil = serverTicks + Integer.parseInt(arg); return; }
            else if (s.startsWith("shot ")) {
                Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(), msg -> {});
                log("shot " + arg + "  fps " + mc.getFps());
                return; // one per tick: each shot is its own frame
            } else if (s.startsWith("note ")) log(arg);
            else if (s.startsWith("hud ")) mc.options.hideGui = !arg.equals("on");
            else if (s.startsWith("trace ")) trace = arg.equals("on");
            else if (s.equals("punch")) { // hold the attack button, swinging
                net.minecraft.client.KeyMapping.set(mc.options.keyAttack.getKey(), true);
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            else log("FAIL unknown step: " + s);
        }
        log("DONE in " + (System.currentTimeMillis() - startMs) / 1000 + " s");
        mc.stop();
    }

    @SubscribeEvent
    static void onChat(ClientChatReceivedEvent e) { // command feedback and errors go to the report
        if (report != null) log("chat: " + e.getMessage().getString());
    }

    @SubscribeEvent
    static void onSound(PlaySoundEvent e) { // fired before the volume check, so muted sounds are listed too
        if (report == null || e.getSound() == null || Minecraft.getInstance().player == null) return;
        var s = e.getSound();
        double dist = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(new net.minecraft.world.phys.Vec3(s.getX(), s.getY(), s.getZ()));
        log("sound " + s.getLocation() + " (" + s.getSource().getName() + ")" + (s.isRelative() ? "" : String.format(" at %.0f blocks", dist)));
    }

    private static void log(String line) {
        if (report == null) return;
        try {
            Files.writeString(report, "[" + serverTicks / 20 + " s] " + line + "\n", StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> all = Files.walk(p)) {
            for (Path f : all.sorted(Comparator.reverseOrder()).toList()) if (!f.equals(p)) Files.delete(f); // the folder itself may be open elsewhere
        }
    }
}
