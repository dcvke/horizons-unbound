package com.horizonsunbound.fabric;

import com.horizonsunbound.StormTests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class FabricTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE) public void temperedEquipmentKeepsDefaults(GameTestHelper h) { com.horizonsunbound.effect.TemperingTest.run(h); }
    @GameTest(template = EMPTY_STRUCTURE) public void wavesHaveAPeriod(GameTestHelper h) { StormTests.wavesHaveAPeriod(h); }
    @GameTest(template = EMPTY_STRUCTURE) public void syncRoundTrip(GameTestHelper h) { StormTests.syncRoundTrip(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600) public void stormLivesAndDies(GameTestHelper h) { StormTests.stormLivesAndDies(h); }
    @GameTest(template = EMPTY_STRUCTURE) public void stormProofBlocks(GameTestHelper h) { StormTests.stormProofBlocks(h); }
}
