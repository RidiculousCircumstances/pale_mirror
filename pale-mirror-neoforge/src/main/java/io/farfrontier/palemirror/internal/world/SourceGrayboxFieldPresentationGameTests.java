package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Materialization proof for source-owned posts, modules and campaign scenes. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxFieldPresentationGameTests {
    private SourceGrayboxFieldPresentationGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void fieldPostModulesHaveExactReadableClaimsAndDoNotRepairPlayerConflicts(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 20);
        ReferenceGrayboxSnapshot snapshot = fieldPostModulesFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        BlockPos depot = anchor.offset(3, 1, 3);
        SourceGrayboxPresentationLedger.Claim depotClaim = materializer.claimAt(helper.getLevel(), depot);
        helper.assertValueEqual(helper.getLevel().getBlockState(depot).getBlock(), Blocks.BROWN_WOOL,
                "a source depot module must be visible as a distinct field-post capability");
        helper.assertValueEqual(depotClaim.kind(), "FIELD_POST_MODULE", "a module must keep its semantic graybox kind");
        helper.assertValueEqual(depotClaim.subjectId(), "field-post:704", "a module must retain its field-post owner rather than inventing state");
        BlockPos hospital = anchor.offset(11, 1, 3);
        helper.assertValueEqual(helper.getLevel().getBlockState(hospital).getBlock(), Blocks.WHITE_WOOL,
                "a source field hospital must be visually distinct from the depot and base post");
        BlockPos fireSupport = anchor.offset(11, 1, 11);
        helper.assertValueEqual(helper.getLevel().getBlockState(fireSupport).getBlock(), Blocks.RED_WOOL,
                "a source fire-support module must stay readable at a distance");

        materializer.recordBlockConflict(helper.getLevel(), depot);
        helper.getLevel().setBlock(depot, Blocks.AIR.defaultBlockState(), 3);
        materializer.apply(helper.getLevel(), snapshot);
        helper.assertValueEqual(helper.getLevel().getBlockState(depot).getBlock(), Blocks.AIR,
                "a destroyed module must remain a visible player/world conflict, never be silently reconstructed");
        helper.assertTrue(materializer.claimAt(helper.getLevel(), depot).conflicted(),
                "a module conflict must stay attached to the original exact field-post claim");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void campaignFamiliesMaterializeDifferentGroundScenes(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 64);
        ReferenceGrayboxSnapshot snapshot = campaignScenesFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.Activity fieldCampaign = snapshot.activities().get(1);
        BlockPos campCorner = new BlockPos(fieldCampaign.position().x() - 2, ReferenceGrayboxLayout.GROUND_Y + 1,
                fieldCampaign.position().z() - 2);
        SourceGrayboxPresentationLedger.Claim campClaim = materializer.claimAt(helper.getLevel(), campCorner);
        helper.assertValueEqual(campClaim.kind(), "ACTIVITY_CAMPAIGN_CAMP",
                "a field campaign must project a compact camp rather than the travelling-operation column");
        helper.assertValueEqual(campClaim.subjectId(), fieldCampaign.id(), "the camp corner must retain its exact campaign identity");

        ReferenceGrayboxSnapshot.Activity frontCampaign = snapshot.activities().get(2);
        BlockPos front = new BlockPos(frontCampaign.position().x(), ReferenceGrayboxLayout.GROUND_Y + 1,
                frontCampaign.position().z() - 2);
        SourceGrayboxPresentationLedger.Claim frontClaim = materializer.claimAt(helper.getLevel(), front);
        helper.assertValueEqual(frontClaim.kind(), "ACTIVITY_FRONT_LINE",
                "a V2 front campaign must project a line silhouette rather than a camp or generic activity marker");
        helper.assertValueEqual(frontClaim.subjectId(), frontCampaign.id(), "the front line must retain its exact campaign identity");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void operationPhasesMaterializeDistinctSourceOwnedGroundScenes(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 88);
        ReferenceGrayboxSnapshot snapshot = operationPhaseScenesFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        assertSceneKind(helper, materializer, anchor.offset(6, 1, 6), "ACTIVITY_OPERATION_ASSEMBLY", "operation:assembly");
        assertSceneKind(helper, materializer, anchor.offset(22, 1, 8), "ACTIVITY_OPERATION_OUTBOUND_COLUMN", "operation:outbound");
        assertSceneKind(helper, materializer, anchor.offset(38, 1, 7), "ACTIVITY_OPERATION_BATTLE_LINE", "operation:engaged");
        assertSceneKind(helper, materializer, anchor.offset(54, 1, 6), "ACTIVITY_OPERATION_STATION", "operation:station");
        assertSceneKind(helper, materializer, anchor.offset(72, 1, 6), "ACTIVITY_OPERATION_RETURN_COLUMN", "operation:returning");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void operationPhaseChangePreservesPlayerConflictWhilePublishingTheNewCanonicalScene(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot assembling = oneOperationPhaseFixture(anchor, baseline, "assembling", "activity.operation.assembling");
        ReferenceGrayboxSnapshot outbound = oneOperationPhaseFixture(anchor, baseline, "en_route", "activity.operation.en_route");
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        BlockPos formerAssembly = anchor.offset(6, 1, 6);
        materializer.apply(helper.getLevel(), assembling);
        materializer.recordBlockConflict(helper.getLevel(), formerAssembly);
        helper.getLevel().setBlock(formerAssembly, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

        materializer.apply(helper.getLevel(), outbound);
        helper.assertValueEqual(helper.getLevel().getBlockState(formerAssembly).getBlock(), net.minecraft.world.level.block.Blocks.AIR,
                "a phase transition must not silently rebuild an assembly block that became a player/world conflict");
        helper.assertTrue(materializer.claimAt(helper.getLevel(), formerAssembly).conflicted(),
                "the retired source-owned assembly claim must retain its conflict instead of granting cleanup authority over the player change");
        assertSceneKind(helper, materializer, anchor.offset(6, 1, 8), "ACTIVITY_OPERATION_OUTBOUND_COLUMN", "operation:phase-test");
        helper.succeed();
    }

    private static void assertSceneKind(GameTestHelper helper, SourceGrayboxMaterializer materializer, BlockPos position,
                                        String kind, String subjectId) {
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(helper.getLevel(), position);
        helper.assertTrue(claim != null, "a source activity scene must retain a physical claim at " + position);
        helper.assertValueEqual(claim.kind(), kind, "the scene shape must expose its source phase instead of a generic operation marker");
        helper.assertValueEqual(claim.subjectId(), subjectId, "the phase silhouette must preserve its one source activity identity");
    }

    private static ReferenceGrayboxSnapshot fieldPostModulesFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline) {
        ReferenceGrayboxLayout.Rectangle post = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 12, 12);
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.FieldPost(704, 71, "forward_base", "active", post, 24.0d, 4, 1,
                        List.of("depot", "field_hospital", "fire_support", "decontamination", "fortification"), "post.forward_base")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static ReferenceGrayboxSnapshot campaignScenesFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline) {
        List<ReferenceGrayboxSnapshot.Activity> activities = List.of(
                new ReferenceGrayboxSnapshot.Activity("operation:705", "operation", "resupply", "en_route",
                        new ReferenceGrayboxLayout.Point(anchor.getX() + 8, anchor.getZ() + 8), 3.0d, 0.0d, false,
                        "activity.operation.en_route"),
                new ReferenceGrayboxSnapshot.Activity("field-campaign:706", "field_campaign", "containment", "build_up",
                        new ReferenceGrayboxLayout.Point(anchor.getX() + 28, anchor.getZ() + 8), 5.0d, 0.35d, false,
                        "activity.field_campaign.build_up"),
                new ReferenceGrayboxSnapshot.Activity("front-campaign:707", "front_campaign", "offensive", "engage",
                        new ReferenceGrayboxLayout.Point(anchor.getX() + 48, anchor.getZ() + 8), 6.0d, 0.62d, false,
                        "activity.front_campaign.engage"));
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), activities,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static ReferenceGrayboxSnapshot operationPhaseScenesFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline) {
        List<ReferenceGrayboxSnapshot.Activity> activities = List.of(
                operation("operation:assembly", "assembling", anchor.getX() + 8, anchor.getZ() + 8, "activity.operation.assembling"),
                operation("operation:outbound", "en_route", anchor.getX() + 24, anchor.getZ() + 8, "activity.operation.en_route"),
                operation("operation:engaged", "engaged", anchor.getX() + 40, anchor.getZ() + 8, "activity.operation.engaged"),
                operation("operation:station", "on_station", anchor.getX() + 56, anchor.getZ() + 8, "activity.operation.on_station"),
                operation("operation:returning", "returning", anchor.getX() + 72, anchor.getZ() + 8, "activity.operation.returning"));
        return withActivities(baseline, activities);
    }

    private static ReferenceGrayboxSnapshot oneOperationPhaseFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline, String phase, String colour) {
        return withActivities(baseline, List.of(operation("operation:phase-test", phase, anchor.getX() + 8, anchor.getZ() + 8, colour)));
    }

    private static ReferenceGrayboxSnapshot.Activity operation(String id, String phase, int x, int z, String colour) {
        return new ReferenceGrayboxSnapshot.Activity(id, "operation", "patrol", phase,
                new ReferenceGrayboxLayout.Point(x, z), 3.0d, 0.0d, false, colour);
    }

    private static ReferenceGrayboxSnapshot withActivities(ReferenceGrayboxSnapshot baseline, List<ReferenceGrayboxSnapshot.Activity> activities) {
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), activities,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
