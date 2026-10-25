package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated owner/custody flow with real graybox geometry and real opportunity selection.
 * Fixture tool/arrival setup is not native evidence; resource outcomes use production reducers. */
class ForestryLifecycleTest {
    private record Fixture(FrontierWorldState state, ForestryWorkStarted start, long tick) { }
    private static Fixture fixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:forestry-lifecycle"), 126,
                FrontierRulesets.installed("frontier-v3-forestry-graybox-r28")));
        var home = new SubjectId("settlement:7"); var inventory = state.inventory();
        var container = inventory.containers().values().stream().filter(value -> value.ownerId().equals(home)
                && value.storage().isPresent() && value.storage().orElseThrow().filter().accepts("minecraft:iron_axe")
                && inventory.firstFreeSlot(value.id()).isPresent()).sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        var tool = new ExactItemStack(new SubjectId("item:forestry-fixture-axe"), home, "minecraft:iron_axe", 1,
                new InventoryCustody.ContainerSlot(container.id(), inventory.firstFreeSlot(container.id()).orElseThrow()), new ItemCondition(250, 0));
        var items = new HashMap<>(inventory.items()); items.put(tool.id(), tool);
        state = state.withInventory(new ExactInventory(inventory.containers(), items, inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(),
                inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources()));
        state = AcceptedEconomyFixture.publish(state, home, 100);
        var staffing = SettlementStaffingComposition.change(state, home, 100);
        if (staffing.isPresent()) state = SettlementStaffingComposition.reduce(state, home, staffing.orElseThrow());
        var selecting = state;
        var resident = state.humanPopulation().residents().values().stream().filter(person -> person.settlementId().equals(home)
                && ResidentWorkComposition.SELECTION.admissible(selecting, person, ResidentWorkKind.FORESTRY, HumanCapability.EXTRACTION, 100))
                .sorted(Comparator.comparing(ResidentProfile::id)).findFirst().orElseThrow();
        var offer = new ForestryWorkAdmission().discover(state, resident, 100).orElseThrow();
        return new Fixture(state, offer.execution(), 100);
    }
    private static FrontierWorldState advance(FrontierWorldState state, ForestryWork job, ForestryWorkAdvanced.Operation operation, long tick) {
        var event = new ForestryWorkAdvanced(job.id(), job.revision(), operation);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        return ForestryColdWork.apply(state, job.id(), event, tick);
    }
    private static FrontierWorldState arrived(FrontierWorldState state, ForestryWork job) {
        return state.withActorBody(job.execution().actorId(), job.movementOrder().legalStations().getFirst().standingBody());
    }
    @Test void wholeTreeAndCargoSurviveRecoveryAndSettleOnceWithoutAPlannerInReplay() throws Exception {
        var f = fixture(); var codec = new FrontierWorldStateCodec(); var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var start = (ForestryWorkStarted)codecs.decode(f.start().type(), codecs.encode(f.start()));
        var state = ForestryWorkAdmission.start(codec.decode(codec.encode(f.state())), start, f.tick()); var id = start.work().id();
        var job = state.forest().work().get(id); var reference = job.target().orElseThrow().tree(); int logs = job.target().orElseThrow().expectedLogs();
        state = advance(arrived(state, job), job, ForestryWorkAdvanced.Operation.TAKE_TOOL, f.tick()); job = state.forest().work().get(id);
        state = advance(arrived(state, job), job, ForestryWorkAdvanced.Operation.LABOUR, f.tick()); job = state.forest().work().get(id);
        long done = job.labour().orElseThrow().activeUntilTick();
        assertEquals(job.labour().orElseThrow().requiredMilliWork(), job.labour().orElseThrow().completedAt(done));
        state = advance(state, job, ForestryWorkAdvanced.Operation.BEGIN_FELLING, done); job = state.forest().work().get(id);
        state = advance(state, job, ForestryWorkAdvanced.Operation.APPLY_CHILD, done); state = codec.decode(codec.encode(state));
        assertEquals(1, ForestryWorkAuthority.carried(state, state.forest().work().get(id)));
        assertEquals(1, ForestryStockDemand.held(state, job.home(), job.outputKind()));
        assertEquals(logs - 1, ForestryStockDemand.committed(state, job.home(), job.outputKind(), Optional.empty()),
                "credited cargo and unsettled source suffix must not be counted twice");
        try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> { fail("settled suffix replay cannot calculate routes"); return null; })) {
            while (state.forest().work().get(id).phase() == ForestryWork.Phase.FELL)
                state = advance(state, state.forest().work().get(id), ForestryWorkAdvanced.Operation.APPLY_CHILD, done);
        }
        job = state.forest().work().get(id); assertEquals(0, state.forest().require(reference).remaining());
        assertEquals(logs, ForestryWorkAuthority.carried(state, job));
        assertEquals(250-logs, state.inventory().items().get(job.toolId()).condition().remaining());
        var settled = state; var completedJob = job;
        assertThrows(IllegalArgumentException.class, () -> advance(settled, completedJob, ForestryWorkAdvanced.Operation.APPLY_CHILD, done));
        state = advance(state, job, ForestryWorkAdvanced.Operation.DELIVER_PART, done); job = state.forest().work().get(id);
        var previousAccount = job.carriedAccount();
        state = advance(arrived(state, job), job, ForestryWorkAdvanced.Operation.STORE, done); job = state.forest().work().get(id);
        assertEquals(0, ForestryWorkAuthority.carried(state, job)); assertNotEquals(previousAccount, job.carriedAccount());
        assertEquals(logs, ProductiveResourceStock.held(state.inventory(), job.home(), job.outputKind()));
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void externalLossDuringFellingInvalidatesOnlyTheUnsettledSuffixAndPreservesEarnedCargo() {
        var f = fixture(); var state = ForestryWorkAdmission.start(f.state(), f.start(), f.tick()); var id = f.start().work().id();
        var job = state.forest().work().get(id);
        state = advance(arrived(state, job), job, ForestryWorkAdvanced.Operation.TAKE_TOOL, f.tick()); job = state.forest().work().get(id);
        state = advance(arrived(state, job), job, ForestryWorkAdvanced.Operation.LABOUR, f.tick()); job = state.forest().work().get(id);
        long done = job.labour().orElseThrow().activeUntilTick();
        state = advance(state, job, ForestryWorkAdvanced.Operation.BEGIN_FELLING, done); job = state.forest().work().get(id);
        state = advance(state, job, ForestryWorkAdvanced.Operation.APPLY_CHILD, done); job = state.forest().work().get(id);
        var tree = state.forest().require(job.target().orElseThrow().tree());
        var target = job.felling().orElseThrow().batch().effects().get(job.appliedEffects()).target();
        int log = tree.declaration().logs().indexOf(tree.declaration().origin(), target);
        // Isolated external-source fact; native ingress epoch fences are covered separately.
        state = state.withChanges(FrontierWorldStateUpdate.begin().forest(state.forest().replace(tree, tree.external(log))));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertFalse(ForestryWorkSelection.targetCurrent(state, job));
        var changed = state; var retained = job;
        assertThrows(IllegalArgumentException.class, () -> advance(changed, retained, ForestryWorkAdvanced.Operation.APPLY_CHILD, done));
        state = advance(state, job, ForestryWorkAdvanced.Operation.ABANDON_TARGET, done); job = state.forest().work().get(id);
        assertEquals(1, ForestryWorkAuthority.carried(state, job));
        assertEquals(249, state.inventory().items().get(job.toolId()).condition().remaining());
        assertTrue(job.target().isEmpty()); assertTrue(job.felling().isEmpty());
        assertEquals(ForestryWork.Phase.SELECT_TREE, job.phase());
    }
    @Test void equipmentReturnCannotBePreparedAtThePreviousCargoWarehouseWhenItsFilterRefusesTheTool() {
        var f = fixture(); var state = ForestryWorkAdmission.start(f.state(), f.start(), f.tick());
        var job = state.forest().work().get(f.start().work().id());
        state = advance(arrived(state, job), job, ForestryWorkAdvanced.Operation.TAKE_TOOL, f.tick());
        job = state.forest().work().get(job.id()); var tool = state.inventory().items().get(job.toolId());
        var current = job; var before = state;
        var oreStorage = state.inventory().containers().values().stream().filter(record -> record.ownerId().equals(current.home())
                && record.storage().filter(policy -> policy.filter().accepts("minecraft:coal")
                    && !policy.filter().accepts(tool.itemKind())).isPresent()).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> WorkEquipmentOrders.create(before, current.id(), current.execution().actorId(),
                tool, oreStorage.storage().orElseThrow().endpoint(), ActorContainerItemOrder.Direction.PLACE,
                Optional.empty(), 1, current.revision()), "unsafe tool return must fail before an intent or physical effect exists");
        var accepted = WorkEquipmentOrders.create(state, job.id(), job.execution().actorId(), tool, job.toolSource(),
                ActorContainerItemOrder.Direction.PLACE, Optional.empty(), 1, job.revision());
        assertEquals(job.toolSource().containerId(), accepted.containerEndpoint().containerId());
        assertEquals(tool, state.inventory().items().get(tool.id()), "admission never moves or creates the tool");
    }
    @Test void runtimeRegistersForestrySchedulesAndValidatesTheirExactCurrentOwner() {
        var f = fixture(); var configuration = FrontierWorldRuntimeDefinition.configuration(f.state().bootstrap());
        assertEquals(3, configuration.initialSchedules().stream().filter(action -> action.kind().equals(ForestryContinuation.REVIEW)).count());
        var state = ForestryWorkAdmission.start(f.state(), f.start(), f.tick());
        var action = ForestryContinuation.progress(f.start().work().id(), f.tick()+1);
        configuration.stateValidator().validateRecoveryInitial(state, List.of(action));
        var unrelated = ForestryContinuation.progress(new SubjectId("work:forestry-foreign"), f.tick()+1);
        assertThrows(IllegalArgumentException.class, () -> configuration.stateValidator().validateRecoveryInitial(state, List.of(unrelated)));
        assertFalse(FrontierWorldRuntimeDefinition.planScheduled(state, action, action.dueAt()).isEmpty());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void unacquiredToolWithdrawalOrDestructionAtomicallyClosesItsWorkAndMovement(boolean destroyed) {
        var f = fixture(); var state = ForestryWorkAdmission.start(f.state(), f.start(), f.tick()); var job = f.start().work();
        var movement = new ActorMovement(job.movementOrder(), f.tick(),
                new ActorMovementContext.ForestryLeg(job.id(), job.revision()), job.execution());
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(Map.of(job.execution().actorId(), movement)));
        var before = state; var tool = state.inventory().items().get(job.toolId());
        FrontierPayload fact = destroyed ? new ExactItemDestroyed(tool.id(), tool.custody(), "isolated-source-destruction")
                : new ExactItemCustodyChanged(tool.id(), tool.custody(), new InventoryCustody.Player(
                    UUID.fromString("a4a3ef6c-d913-4eea-942b-e4821cbb25e0")));
        var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:forestry-tool-departure"),
                new TransactionId("transaction:forestry-tool-departure"), state.bootstrap().worldId(), new Revision(1),
                new SimInstant(f.tick()+1), job.home(), CauseChain.root(new CommandId("command:forestry-tool-departure")), fact);
        state = FrontierWorldRuntimeDefinition.reduce(state, event);
        assertTrue(state.forest().work().isEmpty()); assertTrue(state.actorMovements().isEmpty());
        assertTrue(state.actorExecutions().actors().get(job.execution().actorId()).current().isEmpty());
        assertEquals(destroyed, !state.inventory().items().containsKey(tool.id()));
        var pending = List.of(ForestryContinuation.progress(job.id(), f.tick()+1), ActorMovementProcess.progress(movement, f.tick()+1));
        var retired = new FrontierPhysicalProcessModule().retiredSchedules(before, state, event, () -> pending);
        assertEquals(Set.copyOf(pending), Set.copyOf(retired));
        FrontierWorldRuntimeDefinition.configuration(state.bootstrap()).stateValidator().validateRecoveryInitial(state, List.of());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void movingTheSameToolWithinItsContainerKeepsTheWorkAndItsExactIdentity() {
        var f = fixture(); var state = ForestryWorkAdmission.start(f.state(), f.start(), f.tick()); var job = f.start().work();
        var tool = state.inventory().items().get(job.toolId());
        var slot = new InventoryCustody.ContainerSlot(job.toolSource().containerId(), state.inventory().firstFreeSlot(job.toolSource().containerId()).orElseThrow());
        var moved = FrontierPhysicalProcessModule.reduceCustodyChanged(state, job.home(),
                new ExactItemCustodyChanged(tool.id(), tool.custody(), slot), f.tick()+1);
        assertSame(state.forest(), moved.forest()); assertSame(state.actorExecutions(), moved.actorExecutions());
        assertEquals(slot, moved.inventory().items().get(tool.id()).custody());
    }
}
