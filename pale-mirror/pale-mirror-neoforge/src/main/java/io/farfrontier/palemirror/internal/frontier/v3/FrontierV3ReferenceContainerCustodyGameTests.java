package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.process.HiveNutrientTransferProcess;
import io.farfrontier.palemirror.frontier.v3.process.HiveGrowthProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loaded-world negative evidence and bounded multi-scope scheduling for the F0.2B adapter. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ReferenceContainerCustodyGameTests {
    private FrontierV3ReferenceContainerCustodyGameTests() { }

    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void foreignAndMissingProvenanceRemainActualEvidence(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-observation"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        chest.getPersistentData().putString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY, "container:foreign-depot");
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, "foreign:player");
        FrontierV3ReferenceContainerCustodyExecutor.Observed foreign = FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest);
        helper.assertTrue(foreign.provenance().contains("foreign:container-owner=container:foreign-depot") && foreign.provenance().contains("foreign:player"),
                "a wrong owner and its actual provenance must remain typed evidence, never canonical adoption");

        chest.getPersistentData().putString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY, depot.value());
        chest.getPersistentData().remove(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY);
        FrontierV3ReferenceContainerCustodyExecutor.Observed missingProvenance = FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest);
        helper.assertTrue(missingProvenance.provenance().equals("missing:replica-provenance:" + depot.value()),
                "a legacy owner marker alone cannot manufacture canonical replica provenance");
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, null).provenance().equals("missing:" + depot.value()),
                "an absent chest remains missing evidence");
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.initialDeclarationReady(ContainerSurfaceStatus.PREPARED, chest)
                        && FrontierV3ReferenceContainerCustodyExecutor.initialDeclarationReady(ContainerSurfaceStatus.ACTIVE, chest),
                "a tagged chest cannot become a replica boundary until its surface transition is durably ACTIVE");
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.readyForReplicaObservation(ContainerSurfaceStatus.PREPARED)
                        && FrontierV3ReferenceContainerCustodyExecutor.readyForReplicaObservation(ContainerSurfaceStatus.ACTIVE),
                "a retained replica must not classify a generic socket's durable PREPARED recovery window as missing evidence");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-depot-never-visited", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void roundRobinAdvancesDepotAndHiveWhileAConflictIsLocal(GameTestHelper helper) {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-fairness"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        SubjectId hiveStore = baseline.bootstrap().hive().organs().stream().flatMap(organ -> organ.containerId().stream()).findFirst().orElseThrow();
        List<ContainerSurface> loaded = List.of(baseline.inventory().surfaces().get(depot), baseline.inventory().surfaces().get(hiveStore));
        List<ContainerSurface> eligible = FrontierV3ReferenceContainerCustodyExecutor.eligibleReferenceSurfaces(baseline, loaded);
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.selectRoundRobin(eligible, 0L).containerId()
                        != FrontierV3ReferenceContainerCustodyExecutor.selectRoundRobin(eligible, 1L).containerId(),
                "a stable first held scope cannot starve the next naturally loaded depot/store");

        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(baseline, depot), 1L,
                ReferenceContainerCustody.canonicalFingerprint(baseline, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState conflicted = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 1L, 1L, "sha256:foreign", "foreign:player", 1L);
        FrontierWorldState localConflict = baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(conflicted));
        List<ContainerSurface> remaining = FrontierV3ReferenceContainerCustodyExecutor.eligibleReferenceSurfaces(localConflict, loaded);
        helper.assertTrue(remaining.size() == 1 && remaining.getFirst().containerId().equals(hiveStore),
                "one conflicted depot is excluded locally while the hive store continues");
        helper.succeed();
    }

    /** A formerly visited unloaded depot remains cold until fresh matching evidence exists. */
    @GameTest(batch = "pm-frontier-v3-reference-depot-visited-unloaded", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void releasedDepotDoesNotReemitBeforeRetainedEvidenceMatches(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-released"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(state, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState retained = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 7L, 1L, expected.fingerprint(), expected.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null))
                .checkpoint(ReferenceContainerCustody.scopeId(depot), 1L, 7L, 2L)
                .release(ReferenceContainerCustody.scopeId(depot), 1L, 7L, 2L);
        helper.assertTrue(!ReferenceContainerCustody.hasLiveCustody(state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(retained)), depot),
                "a released unloaded visit has no write authority before a fresh matching observation");
        helper.succeed();
    }

    /** A naturally loaded world with no player still schedules the independent hive store. */
    @GameTest(batch = "pm-frontier-v3-reference-hive-zero-player", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void zeroPlayerHiveStoreIsAReferenceScopeWithoutDepotAuthority(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-hive"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        SubjectId hiveStore = state.bootstrap().hive().organs().stream().flatMap(organ -> organ.containerId().stream()).findFirst().orElseThrow();
        helper.assertTrue(ReferenceContainerCustody.isReferenceContainer(state, hiveStore) && !ReferenceContainerCustody.hasLiveCustody(state, depot),
                "the zero-player hive store is independent from an unheld settlement depot");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void confirmedMutationClosesItsReplicaBoundaryBeforeImmediateRestart(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-same-turn-boundary");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState before = base.initialState();
        SubjectId store = new SubjectId("container:hive-east-store");
        ExactItemStack biomass = exactHiveBiomass(before, store);
        before = before.withInventory(before.inventory().withFungibleResources(FungibleResourceLedger.empty()).store(biomass));
        PhysicalReplicaRecord replica = PhysicalReplicaRecord.expected(store, ReferenceContainerCustody.semanticKind(before, store), 0L,
                ReferenceContainerCustody.canonicalFingerprint(before, store), ReferenceContainerCustody.provenance(store));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(replica)
                .observe(store, 0L, 1L, replica.fingerprint(), replica.provenance(), 0L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(store), store, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 0L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        FrontierWorldState confirmed = before.withInventory(before.inventory().consume(biomass.id(), biomass.count()))
                .withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), confirmed,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);

        BlockPos position = helper.absolutePos(new BlockPos(10, 8, 0)); ServerLevel level = helper.getLevel();
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        chest.getPersistentData().putString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY, store.value());
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, ReferenceContainerCustody.provenance(store));
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(runtime, store, chest),
                "a confirmed exact mutation must checkpoint, release, and emit before another tick can unload its chest");
        FrontierWorldState fenced = runtime.decodedState().orElseThrow();
        PhysicalReplicaRecord emitted = fenced.replicaCustody().replicas().get(store);
        helper.assertTrue(emitted.state() == PhysicalReplicaState.EXPECTED && emitted.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(fenced, store))
                        && !ReferenceContainerCustody.hasLiveCustody(fenced, store),
                "the same turn leaves a new serialized replica boundary rather than stale live custody");
        helper.assertValueEqual(fenced, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(fenced)),
                "an immediate restart preserves the emitted custody/replica boundary without classifying the owned empty chest as foreign drift");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void nutrientEndpointExecutorClosesAndRecoversItsOwnReferenceBoundary(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-nutrient-endpoint-boundary");
        SubjectId east = new SubjectId("container:hive-east-store");
        FrontierWorldState state = activated(FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L)), east);
        BlockPos position = position(state, east);
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:reference-nutrient"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:reference-nutrient"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        state = held(state, east);
        ExactItemStack biomass = exactHiveBiomass(state, east);
        state = state.withInventory(state.inventory().withFungibleResources(FungibleResourceLedger.empty()).store(biomass));
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(state, task, biomass, new SubjectId("container:hive-west-store"), 0);
        state = HiveNutrientTransferProcess.reduceStarted(state, hive, transfer);
        var departure = HiveNutrientTransferStateSupport.departureIntent(state, transfer);
        state = state.preparePhysicalIntent(departure);

        ChestBlockEntity chest = chest(helper, position, east);
        FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, east);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(world, state);
        FrontierV3HiveNutrientEndpointExecutor.tick(helper.getLevel(), runtime);
        FrontierWorldState confirmed = runtime.decodedState().orElseThrow();
        helper.assertTrue(confirmed.physicalIntents().get(departure.id()).status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                        && confirmed.inventory().items().get(biomass.id()).custody().equals(new InventoryCustody.Cargo(transfer.cargoId()))
                        && emittedAndReleased(confirmed, east),
                "the nutrient endpoint executor itself records exact cargo and its next released replica boundary");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> recovered = recovered(world, runtime.checkpointImage().orElseThrow());
        helper.assertTrue(emittedAndReleased(recovered.decodedState().orElseThrow(), east),
                "the executor-established nutrient boundary survives persisted recovery");
        recovered.shutdown(); runtime.shutdown(); helper.succeed();
    }

    /** A HOT hive source becomes COLD cargo only after its named physical stack is removed. */
    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void fungibleNutrientEndpointTransfersOneHotSourceToColdCargo(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-fungible-nutrient-boundary");
        SubjectId east = new SubjectId("container:hive-east-store"), west = new SubjectId("container:hive-west-store");
        FrontierWorldState state = activated(FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L)), east);
        SubjectId hive = state.bootstrap().hive().id(), lotId = new SubjectId("lot:reference-hive-biomass");
        SubjectId accountId = new SubjectId("custody:reference-hive-biomass");
        ResourceLot biomass = new ResourceLot(lotId, hive, "minecraft:rotten_flesh", 64, "reference", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(east), Map.of(lotId, 64), Map.of());
        FungibleResourceLedger cold = FungibleResourceLedger.empty().issue(biomass, account);
        FungiblePhysicalObservation.Stack stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(east, 0)), biomass.itemKind(), 64);
        FungibleResourceLedger hot = cold.rebind(accountId, 1L, FungiblePhysicalObservation.bind(cold, accountId, 1L, List.of(stack)));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:reference-fungible-nutrient"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:reference-fungible-nutrient"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS),
                List.of(), StrategicTaskStatus.PENDING);
        state = held(state.withInventory(state.inventory().withFungibleResources(hot))
                .withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)), east);
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(state, task,
                new FungibleResourceCustodySupport.LotAtContainer(accountId, biomass, 64), west, 0);
        state = HiveNutrientTransferProcess.reduceStarted(state, hive, transfer);
        var departure = HiveNutrientTransferStateSupport.departureIntent(state, transfer);
        state = state.preparePhysicalIntent(departure);

        ChestBlockEntity chest = chest(helper, position(state, east), east);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 64)); chest.setChanged();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(world, state);
        FrontierV3HiveNutrientEndpointExecutor.tick(helper.getLevel(), runtime);
        FrontierWorldState confirmed = runtime.decodedState().orElseThrow();
        helper.assertTrue(chest.getItem(0).isEmpty() && confirmed.physicalIntents().get(departure.id()).status()
                        == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED,
                "the endpoint removes one verified HOT source only after its RUNNING record is durable");
        helper.assertTrue(confirmed.inventory().fungibleResources().accounts().values().stream().anyMatch(value -> value.custody()
                        instanceof ResourceCustody.Cargo cargo && cargo.cargoId().equals(transfer.cargoId())
                        && value.lotQuantities().equals(Map.of(lotId, 64)))
                        && !confirmed.inventory().fungibleResources().accounts().containsKey(accountId)
                        && confirmed.inventory().fungibleResources().bindings().isEmpty(),
                "the exact removed portion has one COLD cargo owner and no surviving source binding");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> recovered = recovered(world, runtime.checkpointImage().orElseThrow());
        helper.assertValueEqual(confirmed, recovered.decodedState().orElseThrow(),
                "the confirmed fungible source departure survives immediate replay without duplicate cargo");
        recovered.shutdown(); runtime.shutdown(); helper.succeed();
    }

    /** A player taking part of a reserved HOT hive input retires only that claimed growth work. */
    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void reservedFungibleHiveWithdrawalForfeitsItsOneOwningGrowthJob(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-fungible-claim-withdrawal");
        SubjectId east = new SubjectId("container:hive-west-store"), hive;
        FrontierWorldState state = activated(FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L)), east);
        hive = state.bootstrap().hive().id(); SubjectId lotId = new SubjectId("lot:reference-hive-claim");
        SubjectId accountId = new SubjectId("custody:reference-hive-claim");
        ResourceLot biomass = new ResourceLot(lotId, hive, "minecraft:rotten_flesh", 64, "reference", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(east), Map.of(lotId, 64), Map.of());
        FungibleResourceLedger cold = FungibleResourceLedger.empty().issue(biomass, account);
        FungiblePhysicalObservation.Stack stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(east, 0)), biomass.itemKind(), 64);
        FungibleResourceLedger hot = cold.rebind(accountId, 1L, FungiblePhysicalObservation.bind(cold, accountId, 1L, List.of(stack)));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:reference-fungible-claim"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:reference-fungible-claim"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS),
                List.of(), StrategicTaskStatus.PENDING);
        state = held(state.withInventory(state.inventory().withFungibleResources(hot))
                .withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)), east);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> start = HiveGrowthProcess.planStart(state, HiveGrowthProcess.start(task, 100L));
        HiveGrowthStarted started = start.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(HiveGrowthStarted.class::isInstance).map(HiveGrowthStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = start.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        state = state.withStrategicPlans(state.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE));
        state = HiveGrowthProcess.reduceStarted(state, hive, started).preparePhysicalIntent(prepared.intent());

        ChestBlockEntity chest = chest(helper, position(state, east), east);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 32)); chest.setChanged();
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 32));
        helper.assertTrue(helper.getLevel().players().stream().flatMap(value -> java.util.stream.IntStream.range(0,
                        value.getInventory().getContainerSize()).mapToObj(value.getInventory()::getItem))
                        .filter(value -> !value.isEmpty() && value.getItem() == net.minecraft.world.item.Items.ROTTEN_FLESH
                        && value.getCount() == 32).count() == 1,
                "the observed departure must have exactly one unambiguous matching player stack");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(world, state);
        helper.assertTrue(!FrontierV3FungibleResourceObservationExecutor.observe(helper.getLevel(), runtime,
                        runtime.decodedState().orElseThrow(), account, chest, 1L),
                "a committed handoff consumes this adapter turn rather than treating the old layout as current");
        FrontierWorldState forfeited = runtime.decodedState().orElseThrow();
        SubjectId claimId = ((HiveGrowthInputHold.FungibleCold) started.job().inputHold()).claimId();
        helper.assertTrue(!forfeited.hiveColony().growthJobs().containsKey(started.job().id())
                        && forfeited.strategicPlans().tasks().get(task.id()).status() == StrategicTaskStatus.BLOCKED
                        && !forfeited.physicalIntents().containsKey(prepared.intent().id())
                        && !forfeited.inventory().fungibleResources().claims().containsKey(claimId),
                "the exact owning growth job is retired atomically and no reserved allocation survives the physical loss");
        helper.assertTrue(forfeited.inventory().fungibleResources().accounts().values().stream().anyMatch(value -> value.custody()
                        .equals(new ResourceCustody.Player(player.getUUID())) && value.lotQuantities().equals(Map.of(lotId, 32))
                        && value.claimQuantities().isEmpty())
                        && forfeited.inventory().fungibleResources().totalQuantity(hive, "minecraft:rotten_flesh") == 64,
                "the observed player portion remains one unclaimed HOT custody account with the conserved total");
        runtime.shutdown(); helper.succeed();
    }

    private static FrontierWorldState activated(FrontierWorldState state, SubjectId containerId) {
        java.util.Map<SubjectId, ContainerSurface> surfaces = new LinkedHashMap<>(state.inventory().surfaces());
        surfaces.put(containerId, new ContainerSurface(containerId, surfaces.get(containerId).position(), ContainerSurfaceStatus.ACTIVE));
        ExactInventory inventory = new ExactInventory(state.inventory().containers(), state.inventory().items(), state.inventory().cargo(),
                state.inventory().playerItems(), state.inventory().worldCarrierItems(), state.inventory().conflicts(), surfaces, state.inventory().economics());
        return state.withInventory(inventory);
    }

    private static BlockPos position(FrontierWorldState state, SubjectId containerId) {
        BlockPosition value = state.inventory().surfaces().get(containerId).position();
        return new BlockPos(value.x(), value.y(), value.z());
    }

    private static ExactItemStack exactHiveBiomass(FrontierWorldState state, SubjectId store) {
        return new ExactItemStack(new SubjectId("item:bootstrap-hive-biomass"), state.bootstrap().hive().id(), "minecraft:rotten_flesh", 64,
                new InventoryCustody.ContainerSlot(store, 0));
    }

    private static FrontierWorldState held(FrontierWorldState state, SubjectId containerId) {
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(containerId, ReferenceContainerCustody.semanticKind(state, containerId), 0L,
                ReferenceContainerCustody.canonicalFingerprint(state, containerId), ReferenceContainerCustody.provenance(containerId));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(containerId, 0L, 1L, expected.fingerprint(), expected.provenance(), 0L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(containerId), containerId, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 0L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        return state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos position, SubjectId containerId) {
        ServerLevel level = helper.getLevel(); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        chest.getPersistentData().putString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY, containerId.value());
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, ReferenceContainerCustody.provenance(containerId));
        return chest;
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(WorldId world, FrontierWorldState state) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> recovered(WorldId world,
                                                                                                     io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        return FrontierV3ServerRuntime.start(configuration, new SnapshotStore(world, checkpoint), 10_000);
    }

    private static boolean emittedAndReleased(FrontierWorldState state, SubjectId containerId) {
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(containerId);
        return replica != null && replica.state() == PhysicalReplicaState.EXPECTED && !ReferenceContainerCustody.hasLiveCustody(state, containerId)
                && replica.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(state, containerId));
    }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }

    private record SnapshotStore(WorldId world, io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint) implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) {
            if (!world.equals(worldId)) throw new IllegalArgumentException("recovery world mismatch");
            return new RecoveryImage(world, Optional.of(new SnapshotRecord(checkpoint, checkpoint.revision().value())), List.of());
        }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) { throw new UnsupportedOperationException("recovered GameTest store is read-only"); }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("recovered GameTest store is read-only"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) { throw new UnsupportedOperationException("recovered GameTest store is read-only"); }
    }
}
