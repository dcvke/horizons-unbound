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
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

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

public final class ClientTest {
    private static final String SCRIPT = System.getProperty("hu.test.script");
    private static final boolean HIDDEN = !"false".equals(System.getProperty("hu.test.hidden"));
    private static final long TIMEOUT_MS = Long.getLong("hu.test.timeout", 300) * 1000;
    private static final String WORLD = "hutest";

    private static List<String> steps;
    private static int step, waitUntil, serverTicks;
    private static boolean worldStarted, hidden, trace, inCutscene, renderReported;
    private static long startMs;
    private static Path report;

    private ClientTest() {}

    public static void onScreen(net.minecraft.client.gui.screens.Screen screen) {
        if (SCRIPT == null || worldStarted || !(screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) return;
        worldStarted = true;
        Minecraft mc = Minecraft.getInstance();
        try {
            steps = Files.readAllLines(Path.of(SCRIPT)).stream().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
            report = mc.gameDirectory.toPath().resolve("test-report.txt");
            Files.writeString(report, "scenario " + SCRIPT + "\n");
            archive(mc.gameDirectory.toPath().resolve("screenshots"));
            if (!Boolean.getBoolean("hu.test.reuseWorld")) archive(mc.getLevelSource().getBaseDir().resolve(WORLD));
        } catch (IOException ex) {
            log("FAIL setup: " + ex);
            mc.stop();
            return;
        }
        // the test must not pause, grab the mouse or make noise when it isn't the window in front
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.hideGui = true;
        if (Boolean.getBoolean("hu.test.reuseWorld")) { mc.createWorldOpenFlows().openWorld(WORLD, () -> { log("FAIL opening saved world"); mc.stop(); }); return; }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings(WORLD, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(1234L, false, false), WorldPresets::createNormalWorldDimensions, screen);
    }

    public static void onTick() {
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
        ConfigTest.tick();
        if (ConfigTest.active()) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen && mc.player != null) {
            mc.player.respawn();
            mc.setScreen(null);
        }
        if (Cutscene.active() != inCutscene) log((inCutscene = Cutscene.active()) ? "cutscene started" : "cutscene ended");
        if (steps == null || mc.player == null || mc.screen != null || mc.getSingleplayerServer() == null) return;
        if (!renderReported) {
            renderReported = true;
            log("shader active=" + StormRenderer.shaderPack() + " forward=" + StormRenderer.forwardPack());
        }
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
            else if (s.equals("config")) { ConfigTest.open(mc.gameDirectory.toPath(), ClientTest::log); return; }
            else if (s.startsWith("persist ")) {
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(() -> PersistenceTest.run(arg, server.getPlayerList().getPlayer(id), mc.gameDirectory.toPath(), ClientTest::log));
            }
            else if (s.startsWith("wait ")) { waitUntil = serverTicks + Integer.parseInt(arg); return; }
            else if (s.startsWith("shot ")) {
                Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(), msg -> {});
                log("shot " + arg + "  fps " + mc.getFps());
                return; // one per tick: each shot is its own frame
            } else if (s.startsWith("note ")) log(arg);
            else if (s.startsWith("hud ")) mc.options.hideGui = !arg.equals("on");
            else if (s.startsWith("trace ")) trace = arg.equals("on");
            else if (s.startsWith("voice ")) { // a 20 ms voice frame, as Simple Voice Chat would decode it: loud (speech) or quiet (breath)
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                short[] pcm = new short[960];
                for (int i = 0; i < pcm.length; i++) pcm[i] = (short) ((arg.equals("loud") ? 16000 : 300) * Math.sin(i * .13));
                server.execute(() -> log("voice " + arg + " heard=" + com.horizonsunbound.compat.VoiceNoise.voice(server.getPlayerList().getPlayer(id), pcm)));
            }
            else if (s.equals("pulses")) { // the Deep Dark storms' answers the client can see now
                long n = ClientStorms.all().stream().flatMap(st -> st.marks.stream()).filter(k -> k.kind() == com.horizonsunbound.Storm.Mark.PULSE).count();
                log("pulses " + n);
            }
            else if (s.startsWith("figura ")) { // wear a local Figura avatar (the folder's path); Figura must be in run-test/mods
                try {
                    Class.forName("org.figuramc.figura.avatar.AvatarManager").getMethod("loadLocalAvatar", Path.class).invoke(null, Path.of(arg));
                    log("figura avatar " + arg);
                } catch (ReflectiveOperationException | LinkageError e) {
                    log("FAIL figura: " + e);
                }
            }
            else if (s.equals("punch")) { // hold the attack button, swinging
                mc.options.keyAttack.setDown(true);
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            else log("FAIL unknown step: " + s);
        }
        log("DONE in " + (System.currentTimeMillis() - startMs) / 1000 + " s");
        mc.stop();
    }

    public static void onChat(net.minecraft.network.chat.Component message) { // command feedback and errors go to the report
        if (report == null) return;
        log("chat: " + message.getString());
        boolean[] error = {false};
        message.visit((style, text) -> {
            if (style.getColor() != null && style.getColor().getValue() == 0xFF5555) error[0] = true;
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        if (error[0]) log("FAIL command feedback: " + message.getString());
    }

    public static void onSound(net.minecraft.client.resources.sounds.SoundInstance sound) { // fired before the volume check, so muted sounds are listed too
        if (report == null || sound == null || Minecraft.getInstance().player == null) return;
        var s = sound;
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

    private static void archive(Path path) throws IOException {
        if (Files.exists(path)) Files.move(path, path.resolveSibling(path.getFileName() + "-previous-" + System.currentTimeMillis()));
    }
}
