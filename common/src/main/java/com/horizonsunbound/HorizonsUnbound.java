package com.horizonsunbound;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class HorizonsUnbound {
    public static final String MODID = "horizons_unbound";

    /**
     * /storm spawn <type> [diameter] (rises right away), /storm omen <type> [diameter] (omens first; rises with its
     * cutscene once someone is close, and in front of the caller someone is), /storm end (the storms here start to
     * die now), /storm grand (the great storm, in front of the caller), /storm natural (a natural spawn right
     * now), /storm clear, /storm info
     */
    public static void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn"), omen = Commands.literal("omen");
        for (StormType type : StormType.values()) {
            for (boolean o : new boolean[] {false, true}) {
                (o ? omen : spawn).then(Commands.literal(type.id)
                        .executes(c -> spawn(c.getSource(), type, Storm.DEFAULT_DIAMETER, o))
                        .then(Commands.argument("diameter", IntegerArgumentType.integer(Storm.MIN_DIAMETER, Storm.MAX_DIAMETER))
                                .executes(c -> spawn(c.getSource(), type, IntegerArgumentType.getInteger(c, "diameter"), o))));
            }
        }
        dispatcher.register(Commands.literal("storm").requires(s -> s.hasPermission(2))
                .then(spawn)
                .then(omen)
                .then(Commands.literal("natural").executes(c -> natural(c.getSource())))
                .then(Commands.literal("twin").executes(c -> twin(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource())))
                .then(Commands.literal("end").executes(c -> end(c.getSource())))
                .then(Commands.literal("debug").executes(c -> debug(c.getSource())))
                .then(Commands.literal("grand").executes(c -> grand(c.getSource())))
                .then(Commands.literal("info").executes(c -> info(c.getSource()))));
    }

    private static int spawn(CommandSourceStack src, StormType type, int diameter, boolean omen) {
        ServerLevel level = src.getLevel();
        // In front of the caller: far enough to see it whole, close enough to stay within the default view distance.
        Vec3 dir = Vec3.directionFromRotation(0, src.getRotation().y);
        double dist = diameter / 2.0 + 80;
        int x = Mth.floor(src.getPosition().x + dir.x * dist), z = Mth.floor(src.getPosition().z + dir.z * dist);
        int y = Storm.groundUnder(level, x + .5, z + .5, diameter / 2f, true);
        Storm storm = new Storm(UUID.randomUUID(), type, diameter, x + .5, y, z + .5);
        if (omen) StormManager.get(level).announce(level, storm);
        else StormManager.get(level).add(level, storm);
        src.sendSuccess(() -> Component.literal((omen ? "Announced " : "Spawned ") + type.id + " storm (" + diameter + " blocks) at " + x + " " + y + " " + z), true);
        return 1;
    }

    private static int natural(CommandSourceStack src) {
        Storm storm = StormSpawner.trySpawn(src.getLevel());
        if (storm == null) {
            src.sendFailure(Component.literal("No free spot for a storm here (too close to players, beds, spawn or villages)"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("A " + storm.type.id + " storm (" + storm.diameter + " blocks) is coming (omens first; it rises when someone comes close) at "
                + Mth.floor(storm.x) + " " + Mth.floor(storm.y) + " " + Mth.floor(storm.z)), true);
        return 1;
    }

    /** Storms of this dimension with their age and what they have marked (seams, coming strikes). */
    private static int info(CommandSourceStack src) {
        var storms = StormManager.get(src.getLevel()).storms();
        if (storms.isEmpty()) src.sendSuccess(() -> Component.literal("No storms in this dimension"), false);
        for (Storm s : storms) {
            StringBuilder b = new StringBuilder(s.type.id + " storm at " + Mth.floor(s.x) + " " + Mth.floor(s.y) + " " + Mth.floor(s.z)
                    + ", " + s.age / 20 + " s old, structures brought: " + s.structures);
            for (Storm.Mark m : s.marks)
                b.append("\n  ").append(m.kind() == Storm.Mark.SEAM ? "seam #" + m.a() : "strike").append(" at ").append(m.x()).append(' ').append(m.y()).append(' ').append(m.z());
            src.sendSuccess(() -> Component.literal(b.toString()), false);
        }
        return storms.size();
    }

    /** /storm twin: a second storm rises near the current one and the two merge. */
    private static int twin(CommandSourceStack src) {
        var storms = StormManager.get(src.getLevel()).storms();
        Storm twin = storms.isEmpty() ? null : StormSpawner.tryTwin(src.getLevel(), storms.get(0));
        if (twin == null) {
            src.sendFailure(Component.literal("Needs one storm out (not already merging) and a free spot next to it"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("A " + twin.type.id + " storm is coming to merge with the " + storms.get(0).type.id + " storm"), true);
        return 1;
    }

    /** /storm debug: each storm here with its age, life left and what its effects cost the server a tick. */
    private static int debug(CommandSourceStack src) {
        StormManager m = StormManager.get(src.getLevel());
        float total = 0;
        for (Storm s : m.storms()) {
            total += s.cost;
            src.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "%s%s storm at %d %d %d: %d s old, %d s of life left%s, effects %.3f ms/tick%s",
                    s.grand ? "great " : "", s.type.id, (int) s.x, (int) s.y, (int) s.z, s.age / 20, s.life() / 20,
                    s.dying() > 0 ? " (dying)" : "", s.cost / 1e6, s.broken ? ", STOPPED after an error (see the log)" : "")), false);
        }
        float sum = total;
        src.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "%d storm(s), %d coming; effects %.3f ms/tick in all",
                m.storms().size(), m.quiet() ? 0 : m.omenCount(), sum / 1e6)), false);
        return m.storms().size();
    }

    /** /storm end: the storms here start to come apart now (their death, and its cutscene for whoever is close). */
    private static int end(CommandSourceStack src) {
        var storms = StormManager.get(src.getLevel()).storms();
        for (Storm s : storms) s.life(Math.min(s.life(), Storm.DEATH + 40));
        src.sendSuccess(() -> Component.literal(storms.size() + " storm(s) coming to an end"), true);
        return storms.size();
    }

    /** /storm grand: the great storm, with its omens and birth, in front of the caller. */
    private static int grand(CommandSourceStack src) {
        int n = spawn(src, StormType.OVERWORLD, Storm.MAX_DIAMETER, true);
        var omens = StormManager.get(src.getLevel());
        omens.lastAnnounced().grand = true;
        return n;
    }

    private static int clear(CommandSourceStack src) {
        int n = 0;
        for (ServerLevel level : src.getServer().getAllLevels()) n += StormManager.get(level).clear(level);
        int removed = n;
        src.sendSuccess(() -> Component.literal("Removed " + removed + " storm(s)"), true);
        return n;
    }
}
