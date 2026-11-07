package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.SettlementServiceWorkProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Isolated pre-loot resource boundary, not a claim of native route/body admission or service M2. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SettlementServiceDeathGameTests {
    private FrontierV3SettlementServiceDeathGameTests() { }

    @GameTest(batch = "pm-frontier-v3-service-death", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void appliedInputSettlesAndLeavesOneExactDropNotADeadActorClaim(GameTestHelper helper) {
        exercise(helper, true, true);
    }

    @GameTest(batch = "pm-frontier-v3-service-death", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void unperformedInputStaysInSourceAndCannotBeReplayedByDeadWorker(GameTestHelper helper) {
        exercise(helper, false, true);
    }

    @GameTest(batch = "pm-frontier-v3-service-death", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void unbegunReservationRetiresWithoutManufacturingPhysicalCompletion(GameTestHelper helper) {
        exercise(helper, false, false);
    }

    private static void exercise(GameTestHelper helper, boolean applied, boolean started) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(1, 8, 0));
        helper.setBlock(new BlockPos(1, 7, 0), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(0, 7, 0), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(0, 8, 0), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(0, 9, 0), Blocks.AIR.defaultBlockState());
        var runtime = fixtureRuntime(source, applied, started);
        var state = runtime.decodedState().orElseThrow();
        var work = state.serviceWorks().values().iterator().next();
        var item = state.inventory().items().get(work.inputItemId());
        var chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, source, work.inputSource().containerId());
        if (chest == null) throw new IllegalStateException("native service death fixture could not claim its source");
        chest.setItem(work.inputSource().slot(), FrontierV3ExactItemPresentation.materializedStack(item));
        var body = EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("native service death fixture could not create its hand carrier");
        body.setUUID(SceneLease.deterministicEntityId(state.bootstrap().worldId(), work.workerId()));
        var station = work.inputStation().standingBody();
        body.setPos(station.x() + 0.5D, station.y(), station.z() + 0.5D);
        body.setNoAi(true);
        var id = ActorBodyAuthority.current(state, work.workerId());
        FrontierV3ActorCarrierComposition.stamp(body, FrontierV3ActorCarrierComposition.fromCanonical(state, work.workerId(),
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, body.getUUID(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, id.physicalEpoch()));
        level.addFreshEntity(body);
        if (applied) helper.assertTrue(FrontierV3SettlementServiceInputIssueExecutor.handOff(chest, body, item, work.inputSource()),
                "fixture must perform the real exact take before observing its uncommitted receipt");
        body.setHealth(0.0F);
        var resources = FrontierV3ActorDeathResourceComposition.prepare(level, runtime, body, id);
        var location = state.actorLocations().get(work.workerId());
        var result = FrontierV3CommandSubmission.submit(runtime, "service-death-fixture", work.id().value(),
                new ActorBodyDied(id, location.body(), location.condition().health(), Optional.empty(),
                        Optional.of(SettlementServiceExecutionAuthority.current(state, work)), "test:pre-loot-service-death"));
        helper.assertTrue(result instanceof CommandResult.Accepted, "modeled fatality must accept the exact service predecessor: " + result);
        resources.settle();
        var after = runtime.decodedState().orElseThrow();
        helper.assertTrue(after.serviceWorks().get(work.id()).phase() == SettlementServiceWorkPhase.BLOCKED
                        && after.actorExecutions().actors().get(work.workerId()).current().isEmpty(),
                "resource settlement cannot revive service execution or work");
        var intent = after.physicalIntents().get(work.inputIssueIntentId());
        helper.assertTrue(after.physicalIntents().get(work.endpointIntentId()).status() == PhysicalIntentStatus.CONFLICTED,
                "the never-started endpoint is retired, not left as a phantom effect awaiting a dead worker");
        var retained = after.inventory().items().get(item.id());
        helper.assertTrue(applied ? intent.status() == PhysicalIntentStatus.CONFIRMED
                        && retained.custody() instanceof InventoryCustody.WorldCarrier && chest.getItem(work.inputSource().slot()).isEmpty()
                : intent.status() == PhysicalIntentStatus.CONFLICTED && retained.custody().equals(work.inputSource())
                        && FrontierV3ExactItemPresentation.exactMatch(chest.getItem(work.inputSource().slot()), item),
                "pre-loot facts distinguish applied custody from an unperformed source reservation");
        helper.assertTrue(body.getMainHandItem().isEmpty(), "no accounted stack may remain in the dying hand for vanilla to drop twice");
        var codec = new FrontierWorldStateCodec(after.bootstrap());
        helper.assertValueEqual(after, codec.decode(codec.encode(after)),
                "the terminal resource account survives hydration without recreation");
        helper.runAfterDelay(5, () -> {
            var drops = level.getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(2),
                    drop -> FrontierV3ExactItemPresentation.exactMatch(drop.getItem(), item));
            helper.assertTrue(drops.size() == (applied ? 1 : 0), "a positive applied hand creates exactly one physical drop; unperformed takes create none");
            if (applied) helper.assertValueEqual(((InventoryCustody.WorldCarrier) retained.custody()).carrierId(),
                    drops.getFirst().getUUID(), "physical drop must retain its exact canonical carrier identity");
            drops.forEach(ItemEntity::discard); body.discard(); runtime.shutdown(); helper.succeed();
        });
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> fixtureRuntime(BlockPos source, boolean applied, boolean startedEffect) {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:service-death-native-" + applied + "-" + startedEffect), 211L);
        // Death accounting is isolated from authored warehouse interiors and resident
        // home routes. Declare an ordinary outdoor source beside a known worksite and
        // stage the worker there before admission; no service effect is fabricated.
        var original = base.initialState();
        var home = original.bootstrap().settlements().stream().filter(value -> value.id().value().equals("settlement:9"))
                .findFirst().orElseThrow();
        var infirmary = home.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow();
        SurfaceAnchor originalStation = null;
        for (int dx = -32; dx <= 32 && originalStation == null; dx += 4) for (int dz = -32; dz <= 32; dz += 4) {
            var candidate = InfectionCell.at(infirmary.anchor().offset(dx, 0, dz));
            var positions = InfectionTreatmentWorksite.candidates(original.bootstrap(), candidate);
            if (!positions.isEmpty()) { originalStation = positions.getFirst(); break; }
        }
        if (originalStation == null) throw new IllegalStateException("fixture has no known service worksite");
        var origin = originalStation.support();
        var initial = FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(original.bootstrap(),
                source.getX() - 1 - origin.x(), source.getY() - 1 - origin.y(), source.getZ() - origin.z()));
        var settlement = initial.bootstrap().settlements().stream().filter(value -> value.id().equals(home.id())).findFirst().orElseThrow();
        var inputStation = SurfaceAnchor.at(source.getX() - 1, source.getY() - 1, source.getZ());
        var cell = InfectionCell.at(inputStation.support());
        var depot = new SubjectId("container:service-death-source");
        var endpoint = new ShipmentEndpoint.Storage(settlement.id(), depot, inputStation, Optional.empty(), inputStation);
        initial = new ResourceStorageDeclared(ContainerRecord.storage(depot, settlement.id(), 27, StorageBlockKind.CHEST,
                new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)),
                new BlockPosition(source.getX(), source.getY(), source.getZ())).apply(initial, settlement.id());
        initial = FrontierV3ReferenceContainerCustodyGameTests.activated(initial, depot);
        var worker = FrontierWorldStateSupport.availableFieldResident(initial, settlement.id(), HumanCapability.MEDICINE).orElseThrow();
        initial = initial.withActorBody(worker.id(), inputStation.standingBody());
        var objective = new StrategicObjective(new SubjectId("objective:service-death-native"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(cell), 1, StrategicObjectiveStatus.ACTIVE);
        var task = new StrategicTask(new SubjectId("task:service-death-native"), objective.id(), settlement.id(),
                StrategicTaskKind.DECONTAMINATE_INFECTION_CELL, Optional.of(cell), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY,
                StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.ACTIVE);
        var inventory = initial.inventory().store(new ExactItemStack(new SubjectId("item:service-death-native"),
                settlement.id(), DecontaminationPolicy.REAGENT, 1,
                new InventoryCustody.ContainerSlot(depot, initial.inventory().firstFreeSlot(depot).orElseThrow())));
        var pending = task.withStatus(StrategicTaskStatus.PENDING);
        var state = initial.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .strategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(pending))).withInfection(cell, new FixedRatio(new FixedScalar(750_000L)));
        var started = SettlementServiceWorkProcess.planDecontamination(state, SettlementServiceWorkProcess.scan(1, 1_000L)).stream()
                .map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance).map(SettlementServiceWorkStarted.class::cast)
                .findFirst().orElseThrow();
        state = SettlementServiceWorkProcess.reduceStarted(state.withStrategicPlans(state.strategicPlans().transitionTask(task.id(),
                StrategicTaskStatus.ACTIVE)), settlement.id(), started);
        var work = started.work();
        while (work.inputTraversalCursor() < work.inputTraversal().linearCorridorSurfaces().size() - 1)
            work = work.withInputTraversalCursor(work.inputTraversalCursor() + 1);
        var actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(work.workerId(), actors.get(work.workerId()).withBody(
                work.inputStation().standingBody()));
        var intents = new LinkedHashMap<>(state.physicalIntents());
        var intent = intents.get(work.inputIssueIntentId());
        if (startedEffect) intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.RUNNING, Optional.empty()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(Map.of(work.id(), work)).actorLocations(actors).physicalIntents(intents));
        state = ActorBodyAuthority.demand(state, work.workerId());
        state = ActorBodyAuthority.running(state, ActorBodyAuthority.current(state, work.workerId()));
        if (startedEffect) state = state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(FencedRecoveryPhysicalIntentSupport.transition(
                state.fencedRecovery(), intent, PhysicalIntentStatus.RUNNING, FencedRecoveryAsset.EFFECT)));
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(1_000L), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(state.bootstrap()), base.projectionMapper(), base.limits(),
                List.<ScheduledAction>of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId world) { return new RecoveryImage(world, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) { return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value()); }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("fixture does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId world, Revision revision) { throw new UnsupportedOperationException("fixture does not compact"); }
    }
}
