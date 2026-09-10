package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementProvisionProcessTest {
    @Test
    void provisionRejectsAnUnboundedExactRecipientLedger() {
        SubjectId settlement = new SubjectId("settlement:bounded-provision");
        List<SubjectId> recipients = java.util.stream.IntStream.rangeClosed(1, HumanPopulation.MAX_RESIDENTS + 1)
                .mapToObj(index -> new SubjectId("resident:bounded-" + index)).toList();

        assertThrows(IllegalArgumentException.class, () -> new SettlementProvision(settlement, 1, 0L,
                HumanPopulation.MAX_RESIDENTS + 1, 0, recipients, List.of(), 0,
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
        assertTrue(settled.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .allMatch(resident -> settled.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.NOURISHED));
        assertEquals(provision, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(settled)).humanPopulation().provision(settlement.id()));
    }

    @Test
    void missingFoodBecomesVisibleShortageWithoutCreatingAHiddenReserve() {
        WorldId world = new WorldId("frontier:provision-shortage"); var engine = FrontierEngines.create(configuration(world, base(world).initialState()));
        advance(engine, 101L);

        Settlement settlement = state(engine).bootstrap().settlements().getFirst(); SettlementProvision provision = state(engine).humanPopulation().provision(settlement.id());
        assertEquals(SettlementProvisionStatus.SHORTAGE, provision.status()); assertEquals(0, provision.fulfilledRations());
        assertTrue(state(engine).inventory().items().values().stream().noneMatch(item -> SettlementProvisionProcess.BREAD.equals(item.itemKind())));
        assertTrue(state(engine).humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .allMatch(resident -> state(engine).humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.HUNGRY));
    }

    @Test
    void partialExactStackFeedsNamedResidentsAndMarksEveryOtherRecipientHungry() {
        WorldId world = new WorldId("frontier:provision-individual-rations"); FrontierWorldState initial = withBreadCount(base(world).initialState(), false, 3);
        var engine = FrontierEngines.create(configuration(world, initial)); advance(engine, 102L);

        FrontierWorldState settled = state(engine); Settlement settlement = settled.bootstrap().settlements().getFirst();
        assertEquals(SettlementProvisionStatus.RATIONED, settled.humanPopulation().provision(settlement.id()).status());
        long fed = settled.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> settled.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.NOURISHED).count();
        long hungry = settled.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> settled.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.HUNGRY).count();
        assertEquals(3, fed); assertEquals(settlement.residents().size() - 3L, hungry);
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
    void releasedReferenceDepotUsesColdProvisionUntilItsNextExactCustodyEpoch() {
        WorldId world = new WorldId("frontier:provision-released-reference");
        FrontierWorldState held = withBread(base(world).initialState(), true);
        SubjectId depot = FrontierWorldState.depotId(held.bootstrap().settlements().getFirst().id());
        PhysicalCustodyLease lease = held.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot));
        PhysicalReplicaCustodyState releasedCustody = held.replicaCustody()
                .checkpoint(lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())
                .release(lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision());
        FrontierWorldState released = held.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(releasedCustody));

        var engine = FrontierEngines.create(configuration(world, released)); advance(engine, 101L);
        FrontierWorldState settled = state(engine); Settlement settlement = settled.bootstrap().settlements().getFirst();
        assertEquals(SettlementProvisionStatus.SECURE, settled.humanPopulation().provision(settlement.id()).status());
        assertTrue(settled.physicalIntents().isEmpty(), "released replica evidence must not manufacture a physical provision intent");
        assertEquals(64 - settlement.residents().size(), settled.inventory().items().get(new SubjectId("item:provision-bread")).count());
    }

    @Test
    void conflictedDepotFencesOnlyItsOwnProvisionStockWhileOtherReferenceScopesRemainUsable() {
        WorldId world = new WorldId("frontier:provision-conflicted-reference");
        FrontierWorldState stocked = withBread(base(world).initialState(), false);
        SubjectId depot = FrontierWorldState.depotId(stocked.bootstrap().settlements().getFirst().id());
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(stocked, depot), 0L,
                ReferenceContainerCustody.canonicalFingerprint(stocked, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState conflicted = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 0L, 1L, "sha256:changed-by-player", "foreign:player", 0L);
        FrontierWorldState localConflict = stocked.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(conflicted));

        assertEquals(0, SettlementProvisionProcess.availableFood(localConflict, stocked.bootstrap().settlements().getFirst().id()),
                "a conflicted depot cannot contribute canonical provision stock");
        SubjectId hiveStore = stocked.bootstrap().hive().organs().stream().flatMap(organ -> organ.containerId().stream()).findFirst().orElseThrow();
        assertTrue(!ReferenceContainerCustody.blocksCanonicalUse(localConflict, hiveStore),
                "the changed depot must not stall an unrelated hive store");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = SettlementProvisionProcess.planReview(localConflict,
                SettlementProvisionProcess.review(stocked.bootstrap().settlements().getFirst().id(), 1, 100L));
        SettlementProvisionStarted started = (SettlementProvisionStarted) planned.get(1).payload();
        assertEquals(SettlementProvisionStatus.SHORTAGE, started.provision().status(),
                "conflicted evidence produces a local visible shortage instead of consuming the retained bread");
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
    void activeDepotCreditsOnlyPhysicallyConfirmedRationRecipientsWhenALaterAllocationFails() {
        WorldId world = new WorldId("frontier:provision-active-partial");
        FrontierWorldState initial = hungryAtCycleOne(withBreadParts(base(world).initialState(), true, 20, 17));
        var engine = FrontierEngines.create(configuration(world, initial, 2)); advance(engine, 101L);
        Settlement settlement = state(engine).bootstrap().settlements().getFirst();
        PhysicalIntent first = state(engine).physicalIntents().get(state(engine).humanPopulation().provision(settlement.id()).activeIntentId().orElseThrow());
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, world, "partial-running-1",
                new PhysicalIntentTransition(first.id(), PhysicalIntentStatus.RUNNING, Optional.empty())));
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, world, "partial-confirmed-1", new PhysicalIntentTransition(first.id(), PhysicalIntentStatus.CONFIRMED,
                Optional.of(new ExactItemConsumedObservation(new PhysicalObservationId("observation:provision-partial-1"), first.id(),
                        new SubjectId("item:provision-bread-0"), 20, 0)))));
        var nextTick = engine.advanceTo(new SimInstant(102L), new WorkBudget(32, 128));
        assertEquals(EngineStatus.Kind.ACTIVE, nextTick.status().kind(), nextTick.status().failureDetail().orElse("frontier provision engine quarantined"));

        PhysicalIntent second = state(engine).physicalIntents().get(state(engine).humanPopulation().provision(settlement.id()).activeIntentId().orElseThrow());
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, world, "partial-running-2",
                new PhysicalIntentTransition(second.id(), PhysicalIntentStatus.RUNNING, Optional.empty())));
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, world, "partial-unknown-2",
                new PhysicalIntentTransition(second.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty())));

        FrontierWorldState resolved = state(engine);
        long nourished = resolved.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> resolved.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.NOURISHED).count();
        long hungry = resolved.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> resolved.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.HUNGRY).count();
        assertEquals(SettlementProvisionStatus.CONFLICT, resolved.humanPopulation().provision(settlement.id()).status());
        assertEquals(20, nourished, "only the first exact physical receipt may feed its named residents");
        assertEquals(settlement.residents().size() - 20L, hungry,
                "an unresolved later physical effect preserves its residents' prior hunger instead of inventing food");
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
        var configuration = FrontierV3FixtureCatalog.settlementProvisionConfiguration(new WorldId("frontier:provision-fixture"), 41L);
        FrontierWorldState state = configuration.initialState(); Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementProvision provision = state.humanPopulation().provision(settlement.id());
        PhysicalIntent intent = state.physicalIntents().get(provision.activeIntentId().orElseThrow());

        assertEquals(37, settlement.residents().size()); assertEquals(2, provision.cycleOrdinal()); assertEquals(SettlementProvisionStatus.IN_PROGRESS, provision.status());
        assertEquals(64, state.inventory().items().get(new SubjectId("item:provision-fixture-bread")).count());
        assertEquals(PhysicalIntentStatus.PREPARED, intent.status()); assertEquals(PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, intent.kind());
        assertTrue(state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .allMatch(resident -> state.humanPopulation().nutrition(resident.id()).status() == ResidentNutritionStatus.HUNGRY));
        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(FrontierWorldState.depotId(settlement.id())).status());
    }

    @Test
    void retainedCountOnlyProvisionStartUpgradesToExactCurrentRecipientIdsBeforeReduction() {
        FrontierWorldState state = base(new WorldId("frontier:legacy-provision")).initialState(); Settlement settlement = state.bootstrap().settlements().getFirst();
        int required = settlement.residents().size(); LegacySettlementProvisionStarted legacy = new LegacySettlementProvisionStarted(settlement.id(), 2, 100L, required, 0,
                List.of(new LegacySettlementProvisionStarted.LegacyAllocation(new SubjectId("item:legacy-provision-bread"), required)), 0,
                SettlementProvisionStatus.IN_PROGRESS);

        assertEquals(legacy, FrontierWorldRuntimeDefinition.payloadCodecs().decode(legacy.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(legacy)));
        FrontierWorldState upgraded = SettlementProvisionProcess.reduceLegacyStarted(state, settlement.id(), legacy);
        SettlementProvision provision = upgraded.humanPopulation().provision(settlement.id());
        assertEquals(required, provision.recipientIds().size()); assertEquals(required, provision.currentAllocation().recipientIds().size());
        assertTrue(provision.recipientIds().stream().allMatch(id -> upgraded.humanPopulation().resident(id).settlementId().equals(settlement.id())));
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base(WorldId world) {
        return FrontierWorldRuntimeDefinition.configuration(world, 91L);
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, FrontierWorldState initial) {
        return configuration(world, initial, 1);
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, FrontierWorldState initial, int cycle) {
        var base = base(world); SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        return new FrontierEngineConfiguration<>(world, initial, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), List.of(SettlementProvisionProcess.review(settlement, cycle, 100L)), base.transactionCommitter());
    }

    private static FrontierWorldState withBread(FrontierWorldState state, boolean active) {
        return withBreadCount(state, active, 64);
    }

    private static FrontierWorldState withBreadCount(FrontierWorldState state, boolean active, int count) {
        return withBreadParts(state, active, count);
    }

    private static FrontierWorldState withBreadParts(FrontierWorldState state, boolean active, int... counts) {
        SubjectId settlement = state.bootstrap().settlements().getFirst().id(); SubjectId depot = FrontierWorldState.depotId(settlement);
        ExactInventory inventory = state.inventory();
        if (active) inventory = inventory.withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE);
        for (int index = 0; index < counts.length; index++) {
            String suffix = counts.length == 1 ? "" : "-" + index;
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:provision-bread" + suffix), settlement, SettlementProvisionProcess.BREAD, counts[index],
                    new InventoryCustody.ContainerSlot(depot, index + 1)));
        }
        FrontierWorldState stocked = state.withInventory(inventory);
        return active ? ReferenceContainerCustodyFixtures.observedAndHeld(stocked, depot) : stocked;
    }

    private static FrontierWorldState hungryAtCycleOne(FrontierWorldState state) {
        HumanPopulation population = state.humanPopulation();
        for (SubjectId residentId : population.residents().keySet()) population = population.resolveNutrition(residentId, 1, false);
        return state.withHumanPopulation(population);
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
