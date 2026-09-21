package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.SupplyOperationProcess;
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

/** Loaded-chunk proof for depot-to-cargo physical removal and its conservative restart predicate. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3CargoLoadingGameTests {
    private FrontierV3CargoLoadingGameTests() { }

    @GameTest(batch = "pm-frontier-v3-cargo-loading", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void removesOnlyTheExactTaggedDepotStackAndLeavesInspectablePostcondition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(34, 8, 0));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        SubjectId settlement = new SubjectId("settlement:1"), container = new SubjectId("container:cargo-loading-game-test");
        SupplyContract contract = new SupplyContract(new SubjectId("contract:cargo-loading-game-test"), settlement, new SubjectId("hive:frontier"),
                new SubjectId("cargo:cargo-loading-game-test"), "minecraft:bread", 64, ContractStatus.ORDERED);
        ExactItemStack item = new ExactItemStack(new SubjectId("item:cargo-loading-game-test"), settlement, "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(container, 0));
        CargoLoadingStateSupport.Target target = new CargoLoadingStateSupport.Target(contract, item, (InventoryCustody.ContainerSlot) item.custody(),
                new BlockPosition(position.getX(), position.getY(), position.getZ()));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item));
        helper.assertTrue(FrontierV3CargoLoadingExecutor.matches(chest, target), "only the exact tagged canonical stack satisfies the precondition");
        helper.assertTrue(FrontierV3CargoLoadingExecutor.remove(chest, target), "the executor removes exactly that one physical stack after durable RUNNING");
        helper.assertTrue(chest.getItem(0).isEmpty(), "an empty owned slot is the sole successful restart postcondition");
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item)); chest.getItem(0).shrink(1);
        helper.assertTrue(!FrontierV3CargoLoadingExecutor.remove(chest, target), "a player/world-altered stack is conflict evidence and is never removed");
        helper.succeed();
    }

    /**
     * Exercises the registered loaded-chunk executor, rather than submitting a prebuilt receipt.
     * The three local fixtures deliberately retain the complete translated v3 topology so the
     * physical target and canonical bounds are in the same GameTest cell.
     */
    @GameTest(batch = "pm-frontier-v3-cargo-loading", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void loadedChunkConfirmsCargoAndAnAlteredStackStaysOwnerLocal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Fixture lawful = fixture(helper, "lawful", new BlockPos(2, 8, 0), PhysicalIntentStatus.PREPARED);
        ChestBlockEntity lawfulChest = chest(level, lawful);
        lawfulChest.setItem(lawful.slot(), FrontierV3CargoHandoffExecutor.materializedStack(lawful.item())); lawfulChest.setChanged();
        FrontierV3CargoLoadingExecutor.tick(level, lawful.runtime());
        FrontierWorldState confirmed = state(lawful.runtime());
        helper.assertTrue(confirmed.physicalIntents().get(lawful.intent().id()).status() == PhysicalIntentStatus.CONFIRMED,
                "a naturally loaded exact owned depot must submit the registered confirmed cargo receipt");
        helper.assertTrue(confirmed.contracts().get(lawful.contract().id()).status() == ContractStatus.LOADED
                        && confirmed.operations().values().stream().anyMatch(operation -> operation.contractId().equals(lawful.contract().id())),
                "the same accepted receipt must atomically transfer cargo and create only its later route authority");
        helper.assertTrue(lawful.runtime().status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE,
                "ordinary loaded cargo confirmation must leave the world runtime active");

        Fixture altered = fixture(helper, "altered", new BlockPos(6, 8, 0), PhysicalIntentStatus.PREPARED);
        ChestBlockEntity alteredChest = chest(level, altered);
        alteredChest.setItem(altered.slot(), FrontierV3CargoHandoffExecutor.materializedStack(altered.item()));
        alteredChest.getItem(altered.slot()).shrink(1); alteredChest.setChanged();
        FrontierV3CargoLoadingExecutor.tick(level, altered.runtime());
        assertOwnerLocalConflict(helper, altered, "an externally altered exact stack");
        FrontierV3CommandSubmission.submit(altered.runtime(), "cargo-unrelated-after-alteration", altered.unrelatedContainer().value(),
                new ContainerSurfaceTransition(altered.unrelatedContainer(), ContainerSurfaceStatus.PREPARED));
        helper.assertTrue(state(altered.runtime()).inventory().surfaces().get(altered.unrelatedContainer()).status() == ContainerSurfaceStatus.PREPARED,
                "the altered cargo owner cannot stall an unrelated valid physical container transition");

        // A retained external command identity can reject only the confirmation command after
        // physical removal.  The real executor must take its confirmation-rejected branch and
        // record the same owner-local UNKNOWN/blocked disposition instead of throwing out of the
        // physical registry and quarantining the runtime.
        Fixture rejected = fixture(helper, "confirmation-rejected", new BlockPos(10, 8, 0), PhysicalIntentStatus.RUNNING);
        CommandId collision = new CommandId("executor:cargo-load-confirmed-" + rejected.intent().id().value().replace(':', '-'));
        FrontierCanonicalState<?> checkpoint = rejected.runtime().canonicalState().orElseThrow();
        CommandResult collisionResult = rejected.runtime().submit(new FrontierCommand(FrontierCommand.LEGACY_SCHEMA_VERSION, collision,
                checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(collision), new ContainerSurfaceTransition(rejected.unrelatedContainer(), ContainerSurfaceStatus.PREPARED)))
                .orElseThrow();
        helper.assertTrue(collisionResult instanceof CommandResult.Accepted,
                "the retained unrelated command is a real prior owner receipt, not a fabricated executor result");
        FrontierV3CargoLoadingExecutor.tick(level, rejected.runtime());
        assertOwnerLocalConflict(helper, rejected, "a rejected loaded confirmation");
        helper.assertTrue(state(rejected.runtime()).inventory().surfaces().get(rejected.unrelatedContainer()).status() == ContainerSurfaceStatus.PREPARED,
                "the prior unrelated receipt remains intact after the rejected cargo confirmation");

        lawful.runtime().shutdown(); altered.runtime().shutdown(); rejected.runtime().shutdown(); helper.succeed();
    }

    private static void assertOwnerLocalConflict(GameTestHelper helper, Fixture fixture, String condition) {
        FrontierWorldState state = state(fixture.runtime());
        helper.assertTrue(state.physicalIntents().get(fixture.intent().id()).status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                condition + " must become one inspectable UNKNOWN cargo intent");
        helper.assertTrue(state.contracts().get(fixture.contract().id()).status() == ContractStatus.ORDERED && state.operations().isEmpty(),
                condition + " must neither transfer cargo nor invent a route operation");
        helper.assertTrue(state.strategicPlans().tasks().values().stream().filter(task -> task.id().value().contains("cargo-loading"))
                        .allMatch(task -> task.status() == StrategicTaskStatus.BLOCKED),
                condition + " must block only the dependent preparation and delivery tasks");
        helper.assertTrue(fixture.runtime().status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE,
                condition + " must not quarantine the unrelated Frontier runtime");
    }

    private static ChestBlockEntity chest(ServerLevel level, Fixture fixture) {
        level.setBlock(fixture.chestPosition().below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, fixture.chestPosition(), fixture.depot());
        if (chest == null) throw new IllegalStateException("cargo loading GameTest could not create its owned active-depot chest");
        return chest;
    }

    private static Fixture fixture(GameTestHelper helper, String suffix, BlockPos localChest, PhysicalIntentStatus intentStatus) {
        WorldId world = new WorldId("frontier:cargo-loading-physical-" + suffix);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState source = base.initialState(); Settlement sourceSettlement = source.bootstrap().settlements().getFirst();
        SubjectId sourceDepot = FrontierWorldState.depotId(sourceSettlement.id()); BlockPosition sourcePosition = source.inventory().surfaces().get(sourceDepot).position();
        BlockPos chestPosition = helper.absolutePos(localChest);
        FrontierWorldState state = loadingState(FrontierWorldState.initial(translatedBootstrap(source.bootstrap(),
                chestPosition.getX() - sourcePosition.x(), chestPosition.getY() - sourcePosition.y(), chestPosition.getZ() - sourcePosition.z())));
        SupplyContract contract = state.contracts().values().iterator().next();
        PhysicalIntent intent = ((PhysicalIntentPrepared) SupplyOperationProcess.planCargoLoad(state, actionFor(contract), false).getFirst().payload()).intent();
        // The same reducer path that the physical owner uses records its recovery authority;
        // direct state construction would manufacture a PREPARED intent without that fence.
        CommandId preparedCommand = new CommandId("command:cargo-loading-physical-prepared-" + suffix);
        state = FrontierWorldRuntimeDefinition.reduce(state, new FrontierEvent(1, new EventId("event:cargo-loading-physical-prepared-" + suffix),
                new TransactionId("transaction:cargo-loading-physical-prepared-" + suffix), world, Revision.ZERO, SimInstant.ZERO,
                contract.settlementId(), CauseChain.root(preparedCommand), new PhysicalIntentPrepared(intent)));
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(state);
        if (intentStatus == PhysicalIntentStatus.RUNNING) FrontierV3CommandSubmission.submit(runtime, "cargo-running-fixture", suffix,
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty()));
        SubjectId depot = FrontierWorldState.depotId(contract.settlementId());
        SubjectId unrelated = FrontierWorldState.depotId(state(runtime).bootstrap().settlements().get(1).id());
        ExactItemStack item = state.inventory().items().get(intent.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM));
        return new Fixture(runtime, intent, contract, item, chestPosition, depot, unrelated, ((InventoryCustody.ContainerSlot) item.custody()).slot());
    }

    private static FrontierWorldState loadingState(FrontierWorldState initial) {
        Settlement settlement = initial.bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ExactItemStack bread = new ExactItemStack(new SubjectId("item:cargo-loading-bread"), settlement.id(), "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(depot, 1));
        FrontierWorldState state = initial.withInventory(initial.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE).store(bread));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:cargo-loading"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask preparation = new StrategicTask(new SubjectId("task:cargo-loading-prepare"), objective.id(), settlement.id(),
                StrategicTaskKind.PREPARE_BREAD_CARGO, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), List.of(), StrategicTaskStatus.ACTIVE);
        StrategicTask delivery = new StrategicTask(new SubjectId("task:cargo-loading-deliver"), objective.id(), settlement.id(),
                StrategicTaskKind.DELIVER_BREAD_TO_HIVE, Optional.empty(), List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE,
                StrategicTaskRequirement.AVAILABLE_HAULER, StrategicTaskRequirement.AVAILABLE_GUARD), List.of(preparation.id()), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(preparation).addTask(delivery))
                .createSupplyContract(new SupplyContract(new SubjectId("contract:supply-1-1"), settlement.id(), state.bootstrap().hive().id(),
                        new SubjectId("cargo:supply-1-1"), "minecraft:bread", 64, ContractStatus.ORDERED));
    }

    private static ScheduledAction actionFor(SupplyContract contract) {
        return new ScheduledAction(new ScheduleId("schedule:cargo-loading-physical"), new SimInstant(500L), 0,
                contract.id(), "frontier.supply.cargo.load", 1);
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(FrontierWorldState state) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 91L);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(state.bootstrap()),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElseThrow(() -> new IllegalStateException("cargo GameTest runtime is not active: " + runtime.status()));
    }

    /** GameTest coordinates are far from (0,0); move the whole immutable topology with its bounds. */
    private static FrontierBootstrap translatedBootstrap(FrontierBootstrap source, int dx, int dy, int dz) {
        java.util.function.Function<BlockPosition, BlockPosition> translate = position -> new BlockPosition(position.x() + dx, position.y() + dy, position.z() + dz);
        List<Settlement> settlements = source.settlements().stream().map(settlement -> new Settlement(settlement.id(), settlement.displayName(),
                translate.apply(settlement.anchor()), settlement.residents().stream().map(resident -> new Resident(resident.id(), resident.settlementId(),
                resident.role(), translate.apply(resident.home()))).toList(), settlement.structures().stream().map(structure -> new SettlementStructure(
                structure.id(), structure.settlementId(), structure.kind(), translate.apply(structure.anchor()), structure.facing())).toList())).toList();
        List<HiveNest> nests = source.hive().seedNests().stream().map(nest -> new HiveNest(nest.id(), nest.hiveId(), translate.apply(nest.anchor()))).toList();
        List<HiveOrgan> organs = source.hive().organs().stream().map(organ -> new HiveOrgan(organ.id(), organ.hiveId(), organ.nestId(), organ.kind(),
                translate.apply(organ.anchor()), organ.containerId())).toList();
        List<Bioform> bioforms = source.hive().bioforms().stream().map(bioform -> new Bioform(bioform.id(), bioform.hiveId(), bioform.nestId(),
                bioform.chassis(), bioform.mutations(), bioform.assignment(), translate.apply(bioform.position()))).toList();
        Map<TerrainColumn, Integer> surveyed = new LinkedHashMap<>();
        source.terrain().surveyedSupportY().forEach((column, supportY) -> surveyed.put(new TerrainColumn(column.x() + dx, column.z() + dz), supportY + dy));
        return new FrontierBootstrap(source.worldId(), source.seed(), new WorldBounds(source.bounds().minX() + dx, source.bounds().minZ() + dz,
                source.bounds().width(), source.bounds().depth()), settlements, new Hive(source.hive().id(), nests, organs, bioforms), source.ruleset(),
                new TerrainSurfacePlan(source.terrain().baselineSupportY() + dy, surveyed));
    }

    private record Fixture(FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime, PhysicalIntent intent,
                           SupplyContract contract, ExactItemStack item, BlockPos chestPosition, SubjectId depot, SubjectId unrelatedContainer, int slot) { }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, Revision coveredRevision) { throw new UnsupportedOperationException("GameTest does not compact"); }
    }
}
