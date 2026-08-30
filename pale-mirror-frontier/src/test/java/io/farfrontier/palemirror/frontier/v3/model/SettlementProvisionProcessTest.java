package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementProvisionProcessTest {
    @Test
    void provisionRejectsAnUnboundedAllocationLedger() {
        SubjectId settlement = new SubjectId("settlement:bounded-provision");
        SettlementRationAllocation allocation = new SettlementRationAllocation(new SubjectId("item:bounded-provision"), 1);

        assertThrows(IllegalArgumentException.class, () -> new SettlementProvision(settlement, 1, 0L,
                HumanPopulation.MAX_RESIDENTS + 1, 0, Collections.nCopies(HumanPopulation.MAX_RESIDENTS + 1, allocation), 0,
                SettlementProvisionStatus.IN_PROGRESS, Optional.empty()));
    }

    @Test
    void coldFoodCycleConsumesOnlyNamedBreadAndEndsSecure() {
        WorldId world = new WorldId("frontier:provision-cold"); FrontierWorldState initial = withBread(base(world).initialState(), false);
        var engine = FrontierEngines.create(configuration(world, initial)); advance(engine, 102L);

        FrontierWorldState settled = state(engine); Settlement settlement = settled.bootstrap().settlements().getFirst();
        SettlementProvision provision = settled.humanPopulation().provision(settlement.id());
        assertEquals(SettlementProvisionStatus.SECURE, provision.status()); assertEquals(settlement.residents().size(), provision.requiredRations());
        assertEquals(settlement.residents().size(), provision.fulfilledRations());
        assertEquals(64 - settlement.residents().size(), settled.inventory().items().get(new SubjectId("item:provision-bread")).count());
        assertEquals(provision, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(settled)).humanPopulation().provision(settlement.id()));
    }

    @Test
    void missingFoodBecomesVisibleShortageWithoutCreatingAHiddenReserve() {
        WorldId world = new WorldId("frontier:provision-shortage"); var engine = FrontierEngines.create(configuration(world, base(world).initialState()));
        advance(engine, 101L);

        Settlement settlement = state(engine).bootstrap().settlements().getFirst(); SettlementProvision provision = state(engine).humanPopulation().provision(settlement.id());
        assertEquals(SettlementProvisionStatus.SHORTAGE, provision.status()); assertEquals(0, provision.fulfilledRations());
        assertTrue(state(engine).inventory().items().values().stream().noneMatch(item -> SettlementProvisionProcess.BREAD.equals(item.itemKind())));
    }

    @Test
    void activeDepotRequiresConfirmedPhysicalRemainderAndUnknownEffectStaysConflict() {
        WorldId world = new WorldId("frontier:provision-active"); FrontierWorldState initial = withBread(base(world).initialState(), true);
        var engine = FrontierEngines.create(configuration(world, initial)); advance(engine, 101L);
        FrontierWorldState prepared = state(engine); Settlement settlement = prepared.bootstrap().settlements().getFirst();
        SettlementProvision provision = prepared.humanPopulation().provision(settlement.id());
        var intent = prepared.physicalIntents().get(provision.activeIntentId().orElseThrow());
        assertEquals(PhysicalIntentStatus.PREPARED, intent.status());
        assertEquals(prepared, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(prepared)));

        CommandResult running = submit(engine, world, "running", new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty()));
        assertInstanceOf(CommandResult.Accepted.class, running, running.toString());
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, world, "unknown", new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty())));
        FrontierWorldState conflicted = state(engine);
        assertEquals(SettlementProvisionStatus.CONFLICT, conflicted.humanPopulation().provision(settlement.id()).status());
        assertEquals(64, conflicted.inventory().items().get(new SubjectId("item:provision-bread")).count());
    }

    @Test
    void activeDepotConsumesTheObservedExactAmountAndKeepsTheStackIdentity() {
        WorldId world = new WorldId("frontier:provision-confirmed"); FrontierWorldState initial = withBread(base(world).initialState(), true);
        var engine = FrontierEngines.create(configuration(world, initial)); advance(engine, 101L);
        FrontierWorldState prepared = state(engine); Settlement settlement = prepared.bootstrap().settlements().getFirst();
        var intent = prepared.physicalIntents().get(prepared.humanPopulation().provision(settlement.id()).activeIntentId().orElseThrow());
        int expected = settlement.residents().size();
        CommandResult running = submit(engine, world, "running", new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty()));
        assertInstanceOf(CommandResult.Accepted.class, running, running.toString());
        ExactItemConsumedObservation receipt = new ExactItemConsumedObservation(new PhysicalObservationId("observation:provision-confirmed"), intent.id(),
                new SubjectId("item:provision-bread"), 64, 64 - expected);
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, world, "confirmed", new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt))));

        FrontierWorldState settled = state(engine); assertEquals(SettlementProvisionStatus.SECURE, settled.humanPopulation().provision(settlement.id()).status());
        ExactItemStack remaining = settled.inventory().items().get(new SubjectId("item:provision-bread"));
        assertEquals(64 - expected, remaining.count()); assertEquals(settlement.id(), remaining.economicOwnerId());
    }

    @Test
    void restartRetainsOnePreparedRationIntentAndConfirmsOnlyItsExactPhysicalRemainder() {
        WorldId world = new WorldId("frontier:provision-restart"); FrontierWorldState initial = withBread(base(world).initialState(), true);
        var uninterrupted = FrontierEngines.create(configuration(world, initial)); advance(uninterrupted, 101L);
        FrontierWorldState beforeRestart = state(uninterrupted); Settlement settlement = beforeRestart.bootstrap().settlements().getFirst();
        PhysicalIntentId intentId = beforeRestart.humanPopulation().provision(settlement.id()).activeIntentId().orElseThrow();
        var recovered = FrontierEngines.recover(configuration(world, initial), new RecoveryImage(world,
                Optional.of(new SnapshotRecord(uninterrupted.checkpoint(), uninterrupted.checkpoint().revision().value())), List.of()));
        assertEquals(beforeRestart, state(recovered)); assertEquals(uninterrupted.checkpoint().schedules(), recovered.checkpoint().schedules());

        PhysicalIntent intent = state(recovered).physicalIntents().get(intentId);
        assertInstanceOf(CommandResult.Accepted.class, submit(recovered, world, "restart-running",
                new PhysicalIntentTransition(intentId, PhysicalIntentStatus.RUNNING, Optional.empty())));
        int rationCount = settlement.residents().size(); ExactItemConsumedObservation receipt = new ExactItemConsumedObservation(
                new PhysicalObservationId("observation:provision-restart-confirmed"), intentId, new SubjectId("item:provision-bread"), 64, 64 - rationCount);
        assertInstanceOf(CommandResult.Accepted.class, submit(recovered, world, "restart-confirmed",
                new PhysicalIntentTransition(intentId, PhysicalIntentStatus.CONFIRMED, Optional.of(receipt))));

        FrontierWorldState settled = state(recovered);
        assertEquals(SettlementProvisionStatus.SECURE, settled.humanPopulation().provision(settlement.id()).status());
        assertEquals(64 - rationCount, settled.inventory().items().get(new SubjectId("item:provision-bread")).count());
        assertEquals(PhysicalIntentStatus.CONFIRMED, settled.physicalIntents().get(intent.id()).status());
    }

    @Test
    void nativeProvisionFixtureStartsWithOneNamedPendingPhysicalRation() {
        var configuration = FrontierWorldRuntimeDefinition.developmentSettlementProvisionConfiguration(new WorldId("frontier:provision-fixture"), 41L);
        FrontierWorldState state = configuration.initialState(); Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementProvision provision = state.humanPopulation().provision(settlement.id());
        PhysicalIntent intent = state.physicalIntents().get(provision.activeIntentId().orElseThrow());

        assertEquals(37, settlement.residents().size()); assertEquals(SettlementProvisionStatus.IN_PROGRESS, provision.status());
        assertEquals(64, state.inventory().items().get(new SubjectId("item:provision-fixture-bread")).count());
        assertEquals(PhysicalIntentStatus.PREPARED, intent.status()); assertEquals(PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, intent.kind());
        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(FrontierWorldState.depotId(settlement.id())).status());
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base(WorldId world) {
        return FrontierWorldRuntimeDefinition.configuration(world, 91L);
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, FrontierWorldState initial) {
        var base = base(world); SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        return new FrontierEngineConfiguration<>(world, initial, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), List.of(SettlementProvisionProcess.review(settlement, 1, 100L)), base.transactionCommitter());
    }

    private static FrontierWorldState withBread(FrontierWorldState state, boolean active) {
        SubjectId settlement = state.bootstrap().settlements().getFirst().id(); SubjectId depot = FrontierWorldState.depotId(settlement);
        ExactInventory inventory = state.inventory();
        if (active) inventory = inventory.withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE);
        return state.withInventory(inventory.store(new ExactItemStack(new SubjectId("item:provision-bread"), settlement, SettlementProvisionProcess.BREAD, 64,
                new InventoryCustody.ContainerSlot(depot, 1))));
    }

    private static CommandResult submit(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine, WorldId world,
                                        String suffix, PhysicalIntentTransition transition) {
        var checkpoint = engine.checkpoint(); CommandId id = new CommandId("command:provision-" + suffix);
        return engine.submit(new FrontierCommand(1, id, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(id), transition));
    }

    private static FrontierWorldState state(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private static void advance(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine, long through) {
        for (long tick = 1L; tick <= through; tick++) {
            var result = engine.advanceTo(new SimInstant(tick), new WorkBudget(32, 128));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("frontier provision engine quarantined"));
        }
    }
}
