package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Physical and recovery proof for source-day effects. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxEffectGameTests {
    private SourceGrayboxEffectGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void hotBreachUsesRealTntGeometryAndReconcilesClaimsWithoutProtectingForeignBlocks(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        data.activate(0L);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        ReferenceGrayboxSnapshot presentation = breachFixture(anchor, data.snapshot());
        ReferenceGrayboxSnapshot.Effect effect = presentation.effects().getFirst();
        BlockPos impact = new BlockPos(effect.position().x(), ReferenceGrayboxLayout.GROUND_Y + 3, effect.position().z());
        BlockPos foreign = impact.east();
        helper.getLevel().setBlock(foreign, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        materializer.apply(helper.getLevel(), presentation);
        double workshopBefore = data.snapshot().facilities().stream().filter(value -> value.id().equals("settlement:1:facility:workshop"))
                .findFirst().orElseThrow().level();

        SourceGrayboxEffectRuntime runtime = new SourceGrayboxEffectRuntime();
        helper.assertTrue(runtime.settleSourceDay(helper.getLevel(), data, materializer, presentation, ignored -> true),
                "a HOT source breach must be eligible for exactly one physical execution");

        helper.assertTrue(helper.getLevel().getBlockState(foreign).isAir(),
                "the real TNT interaction must be allowed to destroy a reachable foreign/player block; no PM safe zone may filter it");
        helper.assertTrue(data.snapshot().facilities().stream().filter(value -> value.id().equals("settlement:1:facility:workshop"))
                        .findFirst().orElseThrow().level() < workshopBefore,
                "a claimed interaction block actually destroyed by the blast must immediately reduce its exact canonical facility");
        helper.assertTrue(data.physicalScars().scars().stream().anyMatch(scar -> scar.x() == foreign.getX() && scar.y() == foreign.getY()
                        && scar.z() == foreign.getZ() && scar.beforeBlockId().equals("minecraft:oak_planks")),
                "an unmodelled reachable block must persist as a bounded physical scar instead of being silently forgotten or rebuilt");
        var lease = data.effectLeases().find("source-graybox:effect:" + effect.id()).orElseThrow();
        helper.assertValueEqual(lease.state().name(), "COMPLETED", "the blast must have a terminal durable receipt");
        helper.assertTrue(lease.receipt().contains("facts=") && lease.receipt().contains("scars="),
                "the terminal receipt must distinguish canonical facts from unmodelled physical aftermath");
        int scars = data.physicalScars().scars().size();
        helper.assertTrue(!runtime.settleSourceDay(helper.getLevel(), data, materializer, presentation, ignored -> true),
                "a completed source-day effect must never replay a second blast");
        helper.assertValueEqual(data.physicalScars().scars().size(), scars,
                "the idempotency receipt must keep a later tick from creating a duplicate aftermath");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void coldSourceDayEffectRecordsVisibleNonReplayableDispositionAndScarsSurviveSaveLoad(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 20);
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        data.activate(0L);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        ReferenceGrayboxSnapshot presentation = breachFixture(anchor, data.snapshot());
        ReferenceGrayboxSnapshot.Effect effect = presentation.effects().getFirst();
        BlockPos impact = new BlockPos(effect.position().x(), ReferenceGrayboxLayout.GROUND_Y + 3, effect.position().z());
        helper.getLevel().setBlock(impact.east(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        materializer.apply(helper.getLevel(), presentation);
        SourceGrayboxEffectRuntime runtime = new SourceGrayboxEffectRuntime();

        helper.assertTrue(runtime.settleSourceDay(helper.getLevel(), data, materializer, presentation, ignored -> false),
                "an unloaded/COLD source boundary must still receive one durable disposition");
        helper.assertValueEqual(helper.getLevel().getBlockState(impact.east()).getBlock(), Blocks.OAK_PLANKS,
                "a COLD event must not produce a delayed physical explosion outside its actual source-day boundary");
        var lease = data.effectLeases().find("source-graybox:effect:" + effect.id()).orElseThrow();
        helper.assertTrue(lease.receipt().contains("disposition=cold"),
                "the skipped physical action must be observable in durable effect history rather than silently dropped");
        helper.assertTrue(!runtime.settleSourceDay(helper.getLevel(), data, materializer, presentation, ignored -> true),
                "returning to the chunk later must not replay an off-screen source-day explosion");

        data.physicalScars().record("test:scar", impact, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        CompoundTag encoded = data.save(new CompoundTag(), null);
        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(encoded, null);
        helper.assertTrue(restored.physicalScars().scars().stream().anyMatch(scar -> scar.effectId().equals("test:scar")
                        && scar.x() == impact.getX() && scar.y() == impact.getY() && scar.z() == impact.getZ()),
                "a restart must retain actual unmodelled damage evidence instead of regenerating the baseline terrain");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void sourceOwnedBlastScopeCannotEnterTheExternalExplosionObservationPath(GameTestHelper helper) {
        helper.assertTrue(!SourceGrayboxExplosionObservation.isSourceEffect(),
                "ordinary world code must not inherit source-owned blast suppression");
        SourceGrayboxExplosionObservation.runSourceEffect(() -> helper.assertTrue(SourceGrayboxExplosionObservation.isSourceEffect(),
                "a source effect must suppress only its own synchronous explosion event"));
        helper.assertTrue(!SourceGrayboxExplosionObservation.isSourceEffect(),
                "source-owned blast suppression must be released after the real effect so later player TNT remains observable");
        helper.succeed();
    }

    private static ReferenceGrayboxSnapshot breachFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline) {
        ReferenceGrayboxLayout.Rectangle facility = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 4, 4);
        ReferenceGrayboxLayout.Point slot = new ReferenceGrayboxLayout.Point(anchor.getX() + 3, anchor.getZ() + 3);
        ReferenceGrayboxSnapshot.Interaction interaction = new ReferenceGrayboxSnapshot.Interaction("breach:workshop", "settlement:1:facility:workshop",
                "facility_damaged", 1.0d, 3, java.util.List.of(slot), "facility.workshop");
        ReferenceGrayboxSnapshot.Effect effect = new ReferenceGrayboxSnapshot.Effect("test:breach", "breach_bomb", "settlement:1", baseline.day(),
                slot, 8.0d, 3.0d, "test breach", "effect.breach");
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                baseline.settlements(), java.util.List.of(new ReferenceGrayboxSnapshot.Facility("breach:workshop", 1, "workshop", facility,
                1.0d, "facility.workshop")), baseline.warehouses(), baseline.resourceSites(), baseline.routes(), baseline.hiveOrgans(),
                baseline.bioforms(), baseline.residents(), baseline.fieldPosts(), baseline.fieldLinks(), baseline.activities(), java.util.List.of(effect),
                baseline.cargoes(), java.util.List.of(interaction), baseline.sectors(), baseline.chrysalises(), baseline.readouts(), baseline.events());
    }
}
