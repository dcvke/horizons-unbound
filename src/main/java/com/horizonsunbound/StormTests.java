package com.horizonsunbound;

import com.horizonsunbound.api.HorizonsUnboundApi;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Server-side checks, run headless by ./gradlew runGameTestServer (a dedicated server: it also proves the mod loads
 * without any client code). Template: an empty 1x1x1 structure (data/horizons_unbound/structure/empty.nbt).
 */
@GameTestHolder(HorizonsUnbound.MODID)
@PrefixGameTestTemplate(false)
public final class StormTests {
    private StormTests() {}

    private static Storm storm(GameTestHelper h, StormType type) {
        BlockPos at = h.absolutePos(BlockPos.ZERO);
        return new Storm(UUID.randomUUID(), type, Storm.MIN_DIAMETER, at.getX() + .5, at.getY(), at.getZ() + .5);
    }

    /** Every storm whose effects make waves has a wave period (a merged storm by either kind: this once crashed the server). */
    @GameTest(template = "empty")
    public static void wavesHaveAPeriod(GameTestHelper h) {
        for (StormType a : StormType.values()) {
            for (StormType b : StormType.values()) {
                Storm s = storm(h, a);
                s.mix = b == a ? null : b;
                boolean waves = s.has(StormType.NETHER) || s.has(StormType.END);
                h.assertTrue(!waves || s.wavePeriod() > 0, a.id + " + " + b.id + ": waves without a period");
            }
        }
        Storm grand = storm(h, StormType.OVERWORLD);
        grand.grand = true;
        h.assertTrue(grand.wavePeriod() > 0 && grand.has(StormType.DEEP_DARK), "the great storm is every kind");
        h.succeed();
    }

    /** What clients are sent comes back the same: kind, place, age, life, the great storm. */
    @GameTest(template = "empty")
    public static void syncRoundTrip(GameTestHelper h) {
        Storm s = storm(h, StormType.END);
        s.mix = StormType.NETHER;
        s.age = 1234;
        s.life(567);
        s.grand = true;
        s.omen = 89;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Storm.STREAM_CODEC.encode(buf, s);
        Storm t = Storm.STREAM_CODEC.decode(buf);
        h.assertTrue(t.id.equals(s.id) && t.type == s.type && t.mix == s.mix && t.age == 1234 && t.life() == 567 && t.grand && t.omen == 89,
                "a storm changed on its way to the client");
        h.succeed();
    }

    /** A storm at the end of its life dies and is gone; its effects ran without error all along. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void stormLivesAndDies(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Storm s = HorizonsUnboundApi.spawn(level, StormType.NETHER, h.absolutePos(BlockPos.ZERO), Storm.MIN_DIAMETER, false);
        h.assertTrue(s != null, "the storm did not come");
        s.age = Storm.BIRTH + 1;  // whole: its effects run
        s.life(Storm.DEATH + 40); // and two seconds from starting to die
        h.runAtTickTime(60, () -> h.assertTrue(s.dying() > 0 && !s.broken, "it should be dying, its effects still working"));
        h.succeedWhen(() -> h.assertTrue(!HorizonsUnboundApi.storms(level).contains(s), "still there"));
    }

    /** Storms never touch storm_proof blocks and do reshape plain ground. */
    @GameTest(template = "empty")
    public static void stormProofBlocks(GameTestHelper h) {
        h.assertTrue(Blocks.BEDROCK.defaultBlockState().is(com.horizonsunbound.effect.StormEffects.STORM_PROOF), "bedrock is storm-proof");
        h.assertTrue(Blocks.STONE.defaultBlockState().is(com.horizonsunbound.effect.StormEffects.RESHAPABLE), "stone is ground storms reshape");
        h.assertTrue(!Blocks.OAK_PLANKS.defaultBlockState().is(com.horizonsunbound.effect.StormEffects.RESHAPABLE), "planks are not ground");
        h.succeed();
    }
}
