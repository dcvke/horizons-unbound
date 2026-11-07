package com.horizonsunbound;

import com.horizonsunbound.client.ClientStorms;
import com.horizonsunbound.client.StormConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

@Mod(HorizonsUnbound.MODID)
public class HorizonsUnbound {
    public static final String MODID = "horizons_unbound";

    public HorizonsUnbound(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, StormConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, StormsConfig.SPEC);
        ModContent.register(modBus);
        modBus.addListener(HorizonsUnbound::registerPayloads);
        NeoForge.EVENT_BUS.addListener(HorizonsUnbound::registerCommands);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // ClientStorms is only touched when a packet arrives, which only happens on a client
        event.registrar("1").playToClient(StormSyncPayload.TYPE, StormSyncPayload.CODEC, (payload, ctx) -> ClientStorms.accept(payload.storms()));
    }

    /** /storm spawn <type> [diameter], /storm natural (a natural spawn right now), /storm clear, /storm info */
    private static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
        for (StormType type : StormType.values()) {
            spawn.then(Commands.literal(type.id)
                    .executes(c -> spawn(c.getSource(), type, Storm.DEFAULT_DIAMETER))
                    .then(Commands.argument("diameter", IntegerArgumentType.integer(Storm.MIN_DIAMETER, Storm.MAX_DIAMETER))
                            .executes(c -> spawn(c.getSource(), type, IntegerArgumentType.getInteger(c, "diameter")))));
        }
        event.getDispatcher().register(Commands.literal("storm").requires(s -> s.hasPermission(2))
                .then(spawn)
                .then(Commands.literal("natural").executes(c -> natural(c.getSource())))
                .then(Commands.literal("twin").executes(c -> twin(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource())))
                .then(Commands.literal("info").executes(c -> info(c.getSource()))));
    }

    private static int spawn(CommandSourceStack src, StormType type, int diameter) {
        ServerLevel level = src.getLevel();
        // In front of the caller: far enough to see it whole, close enough to stay within the default view distance.
        Vec3 dir = Vec3.directionFromRotation(0, src.getRotation().y);
        double dist = diameter / 2.0 + 80;
        int x = Mth.floor(src.getPosition().x + dir.x * dist), z = Mth.floor(src.getPosition().z + dir.z * dist);
        int y = Storm.groundUnder(level, x + .5, z + .5, diameter / 2f, true);
        StormManager.get(level).add(level, new Storm(UUID.randomUUID(), type, diameter, x + .5, y, z + .5));
        src.sendSuccess(() -> Component.literal("Spawned " + type.id + " storm (" + diameter + " blocks) at " + x + " " + y + " " + z), true);
        return 1;
    }

    private static int natural(CommandSourceStack src) {
        Storm storm = StormSpawner.trySpawn(src.getLevel());
        if (storm == null) {
            src.sendFailure(Component.literal("No free spot for a storm here (too close to players, beds, spawn or villages)"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("A " + storm.type.id + " storm (" + storm.diameter + " blocks) is coming (a minute of omens) at "
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

    private static int clear(CommandSourceStack src) {
        int n = 0;
        for (ServerLevel level : src.getServer().getAllLevels()) n += StormManager.get(level).clear(level);
        int removed = n;
        src.sendSuccess(() -> Component.literal("Removed " + removed + " storm(s)"), true);
        return n;
    }
}
