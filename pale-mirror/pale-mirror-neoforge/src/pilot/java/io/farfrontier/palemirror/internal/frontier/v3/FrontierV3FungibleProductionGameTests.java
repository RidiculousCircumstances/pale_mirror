package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Optional;

/** Actual chest effect and registered accounting; finished labor is an explicit initial precondition. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3FungibleProductionGameTests {
    private FrontierV3FungibleProductionGameTests() { }

    @GameTest(batch = "pm-frontier-v3-production", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void unavailableFirstEffectDoesNotBlockIndependentPhysicalProduction(GameTestHelper helper) {
        Fixture local = fixture(helper, "b-local");
        // Normal bootstrap provisions wheat only to settlement1. This isolated fixture
        // explicitly supplies A's initial input, never an expected output or completion.
        var remoteInput = new SubjectId("lot:bootstrap-2-wheat");
        var resources = local.state().inventory().fungibleResources()
                .destroy(new SubjectId("custody:container-2-depot"),
                        java.util.Map.of(new SubjectId("lot:bootstrap-2-bread"), 64), java.util.Map.of())
                .issue(
                new ResourceLot(remoteInput, new SubjectId("settlement:2"), "minecraft:wheat", 64, "test-input", List.of()),
                new CustodyAccount(new SubjectId("custody:container-2-depot"),
                        new ResourceCustody.Container(new SubjectId("container:2-depot")), java.util.Map.of(remoteInput, 64), java.util.Map.of()));
        Fixture remote = prepareEffect(local.state().withInventory(local.state().inventory().withFungibleResources(resources)), 2, "a-remote");
        helper.assertTrue(remote.intent().id().compareTo(local.intent().id()) < 0,
                "the unavailable effect must sort before the available effect");
        helper.assertFalse(helper.getLevel().hasChunkAt(remote.position()), "remote A must remain naturally unloaded");
        var runtime = FrontierV3ReferenceContainerCustodyGameTests.runtime(remote.state().bootstrap().worldId(), remote.state());
        try {
            var chest = FrontierV3ReferenceContainerCustodyGameTests.chest(helper, local.position(), local.depot());
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                var exact = remote.state().inventory().itemAt(local.depot(), slot).orElse(null);
                if (exact != null) chest.setItem(slot, FrontierV3ExactItemPresentation.materializedStack(exact));
            }
            chest.setItem(0, new ItemStack(Items.WHEAT, 64));
            FrontierV3ProductionTransformationExecutor.tick(helper.getLevel(), runtime);
            helper.assertTrue(chest.getItem(0).is(Items.WHEAT), "first bounded turn only attempts unavailable A");
            FrontierV3ProductionTransformationExecutor.tick(helper.getLevel(), runtime);
            var after = runtime.decodedState().orElseThrow();
            helper.assertTrue(chest.getItem(0).is(Items.BREAD) && chest.getItem(0).getCount() == 64,
                    "second real adapter turn must transform B's actual chest");
            helper.assertFalse(after.productionJobs().containsKey(local.job().id()), "B's registered confirmation closes its job");
            helper.assertTrue(after.inventory().fungibleResources().lots().get(local.job().outputItemId()).quantity() == 64,
                    "physical B output has exact canonical accounting");
            helper.assertValueEqual(after.productionJobs().get(remote.job().id()), remote.job(), "A retains its exact work");
            helper.assertValueEqual(after.physicalIntents().get(remote.intent().id()), remote.intent(), "A is not replayed or fabricated");
            FrontierV3ProductionTransformationExecutor.tick(helper.getLevel(), runtime);
            helper.assertValueEqual(chest.getItem(0).getCount(), 64, "returning to waiting A does not duplicate B");
            helper.assertFalse(helper.getLevel().hasChunkAt(remote.position()), "service fairness never force-loads A");
        } finally {
            FrontierV3ProductionTransformationExecutor.forget(runtime);
            runtime.shutdown();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-production", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void actualLotOutputClosesJobPaymentAndReplicaBoundary(GameTestHelper helper) {
        Fixture f = fixture(helper, "success");
        var runtime = FrontierV3ReferenceContainerCustodyGameTests.runtime(f.state().bootstrap().worldId(), f.state());
        var chest = FrontierV3ReferenceContainerCustodyGameTests.chest(helper, f.position(), f.depot());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            var exact = f.state().inventory().itemAt(f.depot(), slot).orElse(null);
            if (exact != null) chest.setItem(slot, FrontierV3ExactItemPresentation.materializedStack(exact));
        }
        chest.setItem(0, new ItemStack(Items.WHEAT, 64));
        var layout = FungibleProductionLayout.plan(f.state(), f.job());
        helper.assertTrue(FrontierV3FungibleProductionEffect.matches(chest, f.state(), layout, layout.before()),
                "fixture must provide the whole retained input chest, including unrelated exact items");
        FrontierV3FungibleProductionEffect.execute(helper.getLevel(), runtime, f.state(), f.intent());
        var after = runtime.canonicalState().orElseThrow().state();
        helper.assertTrue(chest.getItem(0).is(Items.BREAD) && chest.getItem(0).getCount() == 64, "actual chest must contain the recipe output");
        helper.assertTrue(!after.productionJobs().containsKey(f.job().id()), "registered confirmation retires the exact job");
        helper.assertTrue(after.inventory().fungibleResources().lots().get(f.job().outputItemId()).quantity() == 64,
                "actual observed bread must be accounted as the declared lot");
        helper.assertTrue(after.inventory().economics().require(f.job().workerId()).balance().equals(FixedScalar.ONE), "one completed job pays once");
        helper.assertTrue(after.replicaCustody().replicas().get(f.depot()).state() == PhysicalReplicaState.EXPECTED
                && !ReferenceContainerCustody.hasLiveCustody(after, f.depot()), "confirmed output must close into its next replica boundary");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-production", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void foreignPhysicalStockIsRetainedAsAnUncertainEffect(GameTestHelper helper) {
        Fixture f = fixture(helper, "foreign");
        var runtime = FrontierV3ReferenceContainerCustodyGameTests.runtime(f.state().bootstrap().worldId(), f.state());
        var chest = FrontierV3ReferenceContainerCustodyGameTests.chest(helper, f.position(), f.depot());
        chest.setItem(0, new ItemStack(Items.WHEAT, 63));
        chest.setItem(1, new ItemStack(Items.DIAMOND, 1));
        FrontierV3FungibleProductionEffect.execute(helper.getLevel(), runtime, f.state(), f.intent());
        var after = runtime.canonicalState().orElseThrow().state();
        helper.assertTrue(after.physicalIntents().get(f.intent().id()).status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                "mismatching actual contents must retain a registered uncertain effect");
        helper.assertTrue(after.inventory().fungibleResources().equals(f.state().inventory().fungibleResources())
                && after.productionJobs().get(f.job().id()).equals(f.job()), "uncertainty retains stock, claim and work");
        helper.assertTrue(chest.getItem(0).getCount() == 63 && chest.getItem(1).is(Items.DIAMOND), "foreign contents must not be repaired or overwritten");
        FrontierV3FungibleProductionEffect.execute(helper.getLevel(), runtime, after, after.physicalIntents().get(f.intent().id()));
        helper.assertTrue(chest.getItem(0).getCount() == 63 && chest.getItem(1).is(Items.DIAMOND), "uncertain retry must not replay input mutation");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, String suffix) {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:lot-effect-" + suffix), 91L);
        var initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = new SubjectId("settlement:1"), depot = FrontierWorldState.depotId(settlement);
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        var origin = initial.inventory().surfaces().get(depot).position();
        var state = FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(bootstrap,
                position.getX() - origin.x(), position.getY() - origin.y(), position.getZ() - origin.z()));
        return prepareEffect(state, 1, suffix);
    }

    private static Fixture prepareEffect(FrontierWorldState state, int settlementIndex, String suffix) {
        SubjectId settlement = new SubjectId("settlement:" + settlementIndex), depot = FrontierWorldState.depotId(settlement);
        var anchor = state.inventory().surfaces().get(depot).position();
        BlockPos position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        var objective = new StrategicObjective(new SubjectId("objective:lot-effect-" + suffix), settlement,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        var task = new StrategicTask(new SubjectId("task:lot-effect-" + suffix), objective.id(), settlement, StrategicTaskKind.PRODUCE_BREAD,
                Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT), List.of(), StrategicTaskStatus.ACTIVE);
        state = state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));
        var worker = SettlementWorkPolicy.permissions(state, settlement).workers(ResidentWorkKind.BAKING)
                .stream().sorted().findFirst().orElseThrow();
        SubjectId account = new SubjectId("custody:container-" + settlementIndex + "-depot"), claim = new SubjectId("claim:lot-effect-" + suffix);
        SubjectId input = new SubjectId("lot:bootstrap-" + settlementIndex + "-wheat");
        var job = new ProductionJob(new SubjectId("job:lot-effect-" + suffix), task.id(), settlement, new SubjectId("structure:" + settlementIndex + "-workshop"), worker,
                input, new ProductionInputHold.FungibleCold(input, account, claim),
                new SubjectId("lot:effect-bread-" + suffix), "minecraft:bread", 64).withWorkProgress(ProductionWorkProgress.outputReady());
        state = ProductionCommercialProcess.reserve(state.startFungibleProductionJob(job), job);
        var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64));
        state = ProductionResourceCustody.bind(state, account, 1L, FungiblePhysicalObservation.bind(state.inventory().fungibleResources(), account, 1L, stacks));
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state, depot);
        job = state.productionJobs().get(job.id());
        var intent = new PhysicalIntent(new PhysicalIntentId("intent:lot-effect-" + suffix), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, job.id(), PhysicalIntentRoleBinding.productionResources(job.id(), job.consumedItemId(), job.outputItemId(), claim, account, depot),
                new FixedPosition(FixedScalar.whole(position.getX()), FixedScalar.whole(position.getY()), FixedScalar.whole(position.getZ())), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
        state = PhysicalIntentLifecycleFixture.prepare(state, settlement, intent);
        return new Fixture(state, job, intent, depot, position);
    }

    private record Fixture(FrontierWorldState state, ProductionJob job, PhysicalIntent intent, SubjectId depot, BlockPos position) { }
}
