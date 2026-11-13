package com.horizonsunbound.neoforge;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.StormTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HorizonsUnbound.MODID)
@PrefixGameTestTemplate(false)
public final class NeoTests {
    @GameTest(template = "empty") public static void wavesHaveAPeriod(GameTestHelper h) { StormTests.wavesHaveAPeriod(h); }
    @GameTest(template = "empty") public static void syncRoundTrip(GameTestHelper h) { StormTests.syncRoundTrip(h); }
    @GameTest(template = "empty", timeoutTicks = 600) public static void stormLivesAndDies(GameTestHelper h) { StormTests.stormLivesAndDies(h); }
    @GameTest(template = "empty") public static void stormProofBlocks(GameTestHelper h) { StormTests.stormProofBlocks(h); }
}
