package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Multi-region identity and placement tests kept separate from the legacy-region regression suite. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingFrontierGameTests {
    private LivingFrontierGameTests() { }

    @GameTest(batch = "pm-living-frontier", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void threeWellSpacedSettlementsBecomeIndependentIronFrontiers(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        LivingRegionGameTests.reset(data);
        BlockPos base = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos[] anchors = {base, LivingRegionGameTests.surface(level, base.east(3_000)),
                LivingRegionGameTests.surface(level, base.south(3_000))};
        for (int index = 0; index < anchors.length; index++) bind(level, data, anchors[index], index);
        helper.assertValueEqual(data.worldState().livingRegions().size(), 3,
                "three well-spaced observations must become independent regions");
        helper.assertValueEqual(data.campaignRegions().size(), 3,
                "each canonical region must retain an independent physical placement plan");
        helper.assertValueEqual(data.worldState().livingRegions().stream().map(value -> value.communityId()).distinct().count(), 3L,
                "region communities must never share canonical identities");
        helper.assertValueEqual(data.campaignRegions().values().stream().map(value -> value.displayName()).distinct().count(), 3L,
                "the first three generated regions must remain distinguishable to players");
        helper.succeed();
    }

    private static void bind(ServerLevel level, PaleMirrorSavedData data, BlockPos anchor, int index) {
        WorldObjectId observedId = new WorldObjectId("pale_mirror:independent_village_" + index);
        long observedAt = level.getGameTime();
        data.observeSettlement(LivingRegionGameTests.observation(level, observedId, anchor, 5 + index, 1, observedAt));
        data.observeSettlement(LivingRegionGameTests.observation(level, observedId, anchor, 5 + index, 1, observedAt + 200));
        data.observeSettlement(LivingRegionGameTests.observation(level, observedId, anchor, 5 + index, 1, observedAt + 400));
        CampaignRegionBootstrapper.bindCandidate(level.getServer(), data, new DomainServices().commands(), observedId);
    }
}
