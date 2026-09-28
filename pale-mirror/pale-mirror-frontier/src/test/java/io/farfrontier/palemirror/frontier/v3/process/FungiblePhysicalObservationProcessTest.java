package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.ClaimAllocation;
import io.farfrontier.palemirror.frontier.v3.model.ClaimPurpose;
import io.farfrontier.palemirror.frontier.v3.model.FungibleClaimForfeitureStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalHandoff;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStockDepartureObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStockContributionObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackLayoutObserved;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackBindingsReleased;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveGrowthStarted;
import io.farfrontier.palemirror.frontier.v3.model.HiveGrowthInputHold;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustodyFixtures;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaRecord;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjective;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveStatus;
import io.farfrontier.palemirror.frontier.v3.model.StrategicPlanState;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTask;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskRequirement;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskStatus;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskTransition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FungiblePhysicalObservationProcessTest {
    @Test
    void playerGiftCreatesFreshSettlementLotAtTheSameObservedDepotEpoch() {
        WorldId world = new WorldId("frontier:stock-contribution");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState state = base.initialState();
        SubjectId owner = state.bootstrap().settlements().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(owner);
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        var wheat = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64);
        FungibleResourceLedger cold = state.inventory().fungibleResources();
        FungibleResourceLedger hot = cold.rebind(account, 1L,
                FungiblePhysicalObservation.bind(cold, account, 1L, List.of(wheat)));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(
                state.withInventory(state.inventory().withFungibleResources(hot)
                        .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                        .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)), depot);
        ResourceLot gift = new ResourceLot(new SubjectId("lot:player-gift-test"), owner,
                "minecraft:bread", 16, "player-gift", List.of());
        var bread = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 1)), "minecraft:bread", 16);
        FungibleStockContributionObserved observed = new FungibleStockContributionObserved(account, depot, 1L,
                UUID.fromString("00000000-0000-0000-0000-000000000193"),
                UUID.fromString("00000000-0000-0000-0000-000000000194"), gift, List.of(wheat, bread));
        var giftPlan = assertInstanceOf(CommandPlan.Accepted.class,
                FrontierWorldPhysicalObservationProcess.planFungibleStockContribution(state, observed, 0L));
        assertEquals(2, giftPlan.events().size());
        assertEquals(StrategicObjectiveProcess.playerStockReconsideration(owner, observed.interactionId(), 1L),
                ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created) giftPlan.events().get(1).payload()).action());
        FrontierWorldState reduced = FrontierWorldPhysicalObservationProcess.reduceFungibleStockContribution(state, owner, observed);
        assertEquals(16, reduced.inventory().fungibleResources().totalQuantity(owner, "minecraft:bread"));
        var configuration = new FrontierEngineConfiguration<>(world, state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics());
        var engine = FrontierEngines.create(configuration);
        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command(engine, world, "stock-gift", observed)));
        FrontierWorldState recovered = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState());
        assertEquals(reduced.inventory().fungibleResources(), recovered.inventory().fungibleResources());
    }

    @Test
    void stockDepartureUsesExactDepotRemainderWithoutInventingPlayerInventoryCustody() {
        WorldId world = new WorldId("frontier:stock-departure");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState state = base.initialState();
        SubjectId owner = state.bootstrap().settlements().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(owner);
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        SubjectId wheat = new SubjectId("lot:bootstrap-1-wheat");
        FungibleResourceLedger cold = state.inventory().fungibleResources();
        var before = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64);
        FungibleResourceLedger hot = cold.rebind(account, 1L,
                FungiblePhysicalObservation.bind(cold, account, 1L, List.of(before)));
        state = state.withInventory(state.inventory().withFungibleResources(hot)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state, depot);
        var after = new FungiblePhysicalObservation.Stack(before.address(), "minecraft:wheat", 32);
        FungibleStockDepartureObserved observed = new FungibleStockDepartureObserved(account, depot, owner, 1L,
                UUID.fromString("00000000-0000-0000-0000-000000000191"),
                UUID.fromString("00000000-0000-0000-0000-000000000192"), Map.of(wheat, 32), List.of(after));

        var departurePlan = assertInstanceOf(CommandPlan.Accepted.class,
                FrontierWorldPhysicalObservationProcess.planFungibleStockDeparture(state, observed, 0L));
        assertEquals(2, departurePlan.events().size());
        assertEquals(StrategicObjectiveProcess.playerStockReconsideration(owner, observed.interactionId(), 1L),
                ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created) departurePlan.events().get(1).payload()).action());
        FrontierWorldState reduced = FrontierWorldPhysicalObservationProcess.reduceFungibleStockDeparture(state, owner, observed);
        assertEquals(32, reduced.inventory().fungibleResources().totalQuantity(owner, "minecraft:wheat"));
        assertFalse(reduced.inventory().fungibleResources().accounts().values().stream()
                .anyMatch(value -> value.custody() instanceof ResourceCustody.Player));
        assertInstanceOf(CommandPlan.Rejected.class,
                FrontierWorldPhysicalObservationProcess.planFungibleStockDeparture(reduced, observed, 0L));
        var configuration = new FrontierEngineConfiguration<>(world, state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics());
        var engine = FrontierEngines.create(configuration);
        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command(engine, world, "stock-exit", observed)));
        FrontierWorldState recovered = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState());
        assertEquals(reduced.inventory().fungibleResources(), recovered.inventory().fungibleResources());
    }

    @Test
    void oneObservedBreadStackRetiresMultipleRationClaimsThroughTheRegisteredHandoffOwner() {
        WorldId world = new WorldId("frontier:multi-ration-physical-handoff");
        FrontierWorldState state = FrontierWorldRuntimeDefinition.configuration(world, 91L).initialState();
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        SubjectId account = new SubjectId("custody:container-1-depot"), bread = new SubjectId("lot:multi-ration-bread");
        FungibleResourceLedger resources = state.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(bread, settlement, "minecraft:bread", 64, "multi-ration", List.of()));
        resources = resources.split(account, bread, resources.lots().get(bread).splitChild(new SubjectId("lot:bread-a"), 8), 8);
        resources = resources.split(account, bread, resources.lots().get(bread).splitChild(new SubjectId("lot:bread-b"), 8), 8);
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var started = SettlementProvisionProcess.planReview(state, SettlementProvisionProcess.review(settlement, 1, 100L))
                .stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(io.farfrontier.palemirror.frontier.v3.model.SettlementProvisionStarted.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.SettlementProvisionStarted.class::cast).findFirst().orElseThrow();
        state = SettlementProvisionProcess.reduceStarted(state, settlement, started);
        resources = state.inventory().fungibleResources();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        resources = resources.rebind(account, 7L, FungiblePhysicalObservation.bind(resources, account, 7L, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:bread", 64))));
        FrontierWorldState bound = state.withInventory(state.inventory().withFungibleResources(resources));
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000189");
        SubjectId playerAccount = new SubjectId("custody:multi-ration-player");
        FungibleResourceHandoffObserved unstamped = FungiblePhysicalHandoff.departToNew(resources, account, 7L,
                resources.bindings().values().iterator().next(), 0, playerAccount, new ResourceCustody.Player(player), 1L,
                new PhysicalStackAddress.PlayerSlot(player, 0)).forfeitAffectedClaims(resources);
        assertInstanceOf(CommandPlan.Rejected.class, FrontierWorldPhysicalObservationProcess.planFungibleHandoff(bound, unstamped));
        FungibleResourceHandoffObserved observed = FungibleClaimForfeitureStateSupport.stampOwnerDiagnostic(bound, unstamped)
                .withPlayerSaveFence(UUID.fromString("00000000-0000-0000-0000-000000000190"));
        org.junit.jupiter.api.Assertions.assertTrue(observed.forfeitedClaimIds().size() >= 2);
        assertEquals(observed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(observed.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(observed)));
        io.farfrontier.palemirror.frontier.v3.model.DiagnosticProducerContract.requireAdmitted(observed);
        assertInstanceOf(CommandPlan.Accepted.class, FrontierWorldPhysicalObservationProcess.planFungibleHandoff(bound, observed));
        FrontierWorldState after = FrontierWorldPhysicalObservationProcess.reduceFungibleHandoff(bound, settlement, observed);
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.SettlementProvisionStatus.CONFLICT,
                after.humanPopulation().provision(settlement).status());
        assertEquals(Map.of(), after.inventory().fungibleResources().claims());
        assertEquals(64, after.inventory().fungibleResources().accounts().get(playerAccount).lotQuantities()
                .values().stream().mapToInt(Integer::intValue).sum());
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var configuration = new FrontierEngineConfiguration<>(world, bound, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = FrontierEngines.create(configuration);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine, world, "multi-ration-theft", observed)));
        FrontierWorldState committed = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState());
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.DiagnosticReason.SETTLEMENT_PROVISION_CONFLICT,
                committed.diagnosticIncidents().why(new io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject(
                        io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubjectKind.SETTLEMENT_PROVISION, settlement))
                        .orElseThrow().diagnostic().reason());
    }

    @Test
    void registeredPhysicalObservationAcceptsVanillaSplitButRejectsStaleAndMixedEvidence() {
        WorldId world = new WorldId("frontier:fungible-physical-observation");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 23L);
        SubjectId owner = new SubjectId("settlement:1"), container = new SubjectId("container:1-depot");
        SubjectId lotId = new SubjectId("lot:physical-observation"), accountId = new SubjectId("custody:physical-observation");
        FungibleResourceLedger resources = FungibleResourceLedger.empty().issue(new ResourceLot(lotId, owner, "minecraft:bread", 10, "test", List.of()),
                new CustodyAccount(accountId, new ResourceCustody.Container(container), Map.of(lotId, 10), Map.of()));
        FrontierWorldState state = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(resources));
        FungibleStackLayoutObserved split = observation(accountId, 4L, "minecraft:bread", 6, 4);
        assertInstanceOf(CommandPlan.Rejected.class, FrontierWorldPhysicalObservationProcess.planFungibleLayout(state, split));
        FrontierWorldState withoutCustody = state;
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldPhysicalObservationProcess.reduceFungibleLayout(withoutCustody, owner, split));
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(container, ReferenceContainerCustody.semanticKind(state, container), 0L,
                ReferenceContainerCustody.canonicalFingerprint(state, container), ReferenceContainerCustody.provenance(container));
        var preparing = state.replicaCustody().declare(expected).prepareProjection(new PhysicalCustodyLease(
                ReferenceContainerCustody.scopeId(container), container, ReferenceContainerCustody.PROVIDER_ID, 4L, 0L, 1L,
                PhysicalCustodyLeaseStatus.PREPARING, null));
        FrontierWorldState pending = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(preparing));
        assertInstanceOf(CommandPlan.Rejected.class, FrontierWorldPhysicalObservationProcess.planFungibleLayout(pending, split));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldPhysicalObservationProcess.reduceFungibleLayout(pending, owner, split));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(preparing.confirmProjection(
                ReferenceContainerCustody.scopeId(container), 4L, 0L, 1L, expected.fingerprint(), expected.provenance())));
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = FrontierEngines.create(configuration);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine, world, "split", split)));
        FrontierWorldState afterSplit = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(2, afterSplit.inventory().fungibleResources().bindings().size());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, world, "stale", observation(accountId, 3L, "minecraft:bread", 6, 4))));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, world, "mixed", observation(accountId, 4L, "minecraft:carrot", 6, 4))));
        SubjectId playerAccount = new SubjectId("custody:physical-player"); UUID player = UUID.fromString("00000000-0000-0000-0000-000000000099");
        CustodyAccount destination = new CustodyAccount(playerAccount, new ResourceCustody.Player(player), Map.of(lotId, 4), Map.of());
        PhysicalStackBinding remaining = new PhysicalStackBinding(new SubjectId("binding:physical-remainder"), accountId,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 1)), 4L, "minecraft:bread", Map.of(lotId, 6), Map.of());
        PhysicalStackBinding playerBinding = new PhysicalStackBinding(new SubjectId("binding:physical-player"), playerAccount,
                new PhysicalStackAddress.PlayerSlot(player, 0), 5L, "minecraft:bread", Map.of(lotId, 4), Map.of());
        FungibleResourceHandoffObserved handoff = new FungibleResourceHandoffObserved(accountId, destination, 4L, 5L, Map.of(lotId, 4), Map.of(),
                List.of(remaining), List.of(playerBinding)).withPlayerSaveFence(UUID.fromString("00000000-0000-0000-0000-000000000777"));
        assertEquals(handoff, FrontierWorldRuntimeDefinition.payloadCodecs().decode(handoff.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(handoff)));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine, world, "handoff", handoff)));
        FrontierWorldState transferred = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(Map.of(lotId, 4), transferred.inventory().fungibleResources().accounts().get(playerAccount).lotQuantities());
        assertEquals("00000000-0000-0000-0000-000000000777", transferred.inventory().fungibleResources().bindings()
                .get(playerBinding.id()).playerSaveFence(), "the exact canonical handoff must retain its durable player-save fence");
        assertEquals(10, transferred.inventory().fungibleResources().totalQuantity(owner, "minecraft:bread"));

        CustodyAccount forgedDestination = new CustodyAccount(accountId, new ResourceCustody.Container(container), Map.of(lotId, 6), Map.of());
        PhysicalStackBinding restored = new PhysicalStackBinding(new SubjectId("binding:physical-restored"), accountId,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 3)), 4L, "minecraft:bread", Map.of(lotId, 10), Map.of());
        FungibleResourceHandoffObserved forged = new FungibleResourceHandoffObserved(playerAccount, forgedDestination, 5L, 4L,
                Map.of(lotId, 4), Map.of(), List.of(), List.of(restored));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, world, "forged-existing", forged)));

        CustodyAccount finalDestination = new CustodyAccount(accountId, new ResourceCustody.Container(container), Map.of(lotId, 10), Map.of());
        FungibleResourceHandoffObserved returned = new FungibleResourceHandoffObserved(playerAccount, finalDestination, 5L, 4L,
                Map.of(lotId, 4), Map.of(), List.of(), List.of(restored));
        CommandResult returnedResult = engine.submit(command(engine, world, "returned", returned));
        assertInstanceOf(CommandResult.Accepted.class, returnedResult, returnedResult::toString);
        FrontierWorldState restoredState = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(Map.of(lotId, 10), restoredState.inventory().fungibleResources().accounts().get(accountId).lotQuantities());
        assertEquals(10, restoredState.inventory().fungibleResources().totalQuantity(owner, "minecraft:bread"));
        FungibleStackBindingsReleased released = new FungibleStackBindingsReleased(accountId, 4L);
        assertEquals(released, FrontierWorldRuntimeDefinition.payloadCodecs().decode(released.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(released)));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine, world, "released", released)));
        FrontierWorldState releasedState = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(Map.of(), releasedState.inventory().fungibleResources().bindings());
    }

    @Test
    void observedPartialDepartureUsesTheCurrentBindingAndCannotReopenColdCustody() {
        SubjectId owner = new SubjectId("settlement:1"), container = new SubjectId("container:1-depot");
        SubjectId lot = new SubjectId("lot:partial-departure"), account = new SubjectId("custody:partial-departure");
        FungibleResourceLedger ledger = FungibleResourceLedger.empty().issue(new ResourceLot(lot, owner, "minecraft:wheat", 10, "test", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot, 10), Map.of()));
        ledger = ledger.rebind(account, 7L, FungiblePhysicalObservation.bind(ledger, account, 7L, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 3)), "minecraft:wheat", 10))));
        PhysicalStackBinding source = ledger.bindings().values().iterator().next(); UUID player = UUID.fromString("00000000-0000-0000-0000-000000000101");
        CustodyAccount destination = new CustodyAccount(new SubjectId("custody:player-partial"), new ResourceCustody.Player(player), Map.of(lot, 4), Map.of());

        FungibleResourceHandoffObserved observed = FungiblePhysicalHandoff.depart(ledger, account, 7L, source, 6, destination, 1L,
                new PhysicalStackAddress.PlayerSlot(player, 2));
        FungibleResourceLedger transferred = ledger.transferObservedToNewAccount(observed.sourceAccountId(), observed.destinationAccount(),
                observed.sourceEpoch(), observed.destinationEpoch(), observed.lotQuantities(), observed.claimQuantities(),
                observed.remainingSource(), observed.destinationBindings());

        assertEquals(10, transferred.totalQuantity(owner, "minecraft:wheat"));
        assertEquals(Map.of(lot, 6), transferred.accounts().get(account).lotQuantities());
        assertEquals(Map.of(lot, 4), transferred.accounts().get(destination.id()).lotQuantities());
        assertEquals(2, transferred.bindings().size(), "both visible portions remain HOT; neither may be spent as COLD");
        assertThrows(IllegalArgumentException.class, () -> transferred.transferObservedToNewAccount(observed.sourceAccountId(), observed.destinationAccount(),
                observed.sourceEpoch(), observed.destinationEpoch(), observed.lotQuantities(), observed.claimQuantities(),
                observed.remainingSource(), observed.destinationBindings()));
    }

    @Test
    void hotReservedDepartureReleasesOnlyItsOwningHiveWorkAndNeverLeavesTheClaimSpendable() {
        FrontierWorldState baseline = hiveGrowthTaskState();
        SubjectId hive = baseline.bootstrap().hive().id(), east = new SubjectId("container:hive-west-store");
        SubjectId accountId = new SubjectId("custody:hive-west-biomass"), lotId = new SubjectId("lot:hive-west-biomass");
        FungibleResourceLedger resources = FungibleResourceLedger.empty().issue(new ResourceLot(lotId, hive, "minecraft:rotten_flesh", 64,
                "test", List.of()), new CustodyAccount(accountId, new ResourceCustody.Container(east), Map.of(lotId, 64), Map.of()));
        List<FungiblePhysicalObservation.Stack> layout = List.of(new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(east, 0)), "minecraft:rotten_flesh", 64));
        resources = resources.rebind(accountId, 7L, FungiblePhysicalObservation.bind(resources, accountId, 7L, layout));
        FrontierWorldState held = ReferenceContainerCustodyFixtures.observedAndHeld(baseline.withInventory(baseline.inventory()
                .withSurfaceStatus(east, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(east, ContainerSurfaceStatus.ACTIVE)
                .withFungibleResources(resources)), east);
        StrategicTask task = held.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> start = HiveGrowthProcess.planStart(held, HiveGrowthProcess.start(task, 100L));
        HiveGrowthStarted started = start.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(HiveGrowthStarted.class::isInstance).map(HiveGrowthStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = start.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        FrontierWorldState active = held.withStrategicPlans(held.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE));
        active = HiveGrowthProcess.reduceStarted(active, hive, started).preparePhysicalIntent(prepared.intent());
        PhysicalStackBinding source = active.inventory().fungibleResources().bindings().values().iterator().next();
        SubjectId claimId = ((HiveGrowthInputHold.FungibleCold) started.job().inputHold()).claimId();
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000144");
        FungibleResourceHandoffObserved theft = FungiblePhysicalHandoff.departToNew(active.inventory().fungibleResources(), accountId, 7L,
                source, 32, new SubjectId("custody:player-claim-theft"), new ResourceCustody.Player(player), 1L,
                new PhysicalStackAddress.PlayerSlot(player, 0)).forfeitMovedClaims()
                .withPlayerSaveFence(UUID.fromString("00000000-0000-0000-0000-000000000778"));

        var wrongClaims = new java.util.HashMap<>(active.inventory().fungibleResources().claims());
        ClaimAllocation actual = wrongClaims.get(claimId);
        wrongClaims.put(claimId, new ClaimAllocation(actual.id(), actual.claimantId(), actual.economicOwnerId(),
                actual.itemKind(), actual.quantity(), actual.lotQuantities(), ClaimPurpose.SUPPLY_CONTRACT));
        FungibleResourceLedger actualResources = active.inventory().fungibleResources();
        FrontierWorldState misdeclared = active.withInventory(active.inventory().withFungibleResources(new FungibleResourceLedger(
                actualResources.lots(), wrongClaims, actualResources.accounts(), actualResources.bindings())));
        assertFalse(FungibleClaimForfeitureStateSupport.supports(misdeclared, theft),
                "a claimant ID found in the hive registry cannot override its declared supply-contract owner kind");

        assertEquals(theft, FrontierWorldRuntimeDefinition.payloadCodecs().decode(theft.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(theft)));
        assertInstanceOf(CommandPlan.Accepted.class, FrontierWorldPhysicalObservationProcess.planFungibleHandoff(active, theft));
        FrontierWorldState afterTheft = FrontierWorldPhysicalObservationProcess.reduceFungibleHandoff(active, hive, theft);

        assertFalse(afterTheft.hiveColony().growthJobs().containsKey(started.job().id()));
        assertEquals(StrategicTaskStatus.BLOCKED, afterTheft.strategicPlans().tasks().get(task.id()).status());
        assertFalse(afterTheft.physicalIntents().containsKey(prepared.intent().id()));
        assertFalse(afterTheft.inventory().fungibleResources().claims().containsKey(claimId));
        assertEquals(Map.of(), afterTheft.inventory().fungibleResources().accounts().get(accountId).claimQuantities());
        assertEquals(Map.of(), afterTheft.inventory().fungibleResources().accounts().get(theft.destinationAccount().id()).claimQuantities());
        assertEquals("00000000-0000-0000-0000-000000000778", afterTheft.inventory().fungibleResources().bindings()
                .get(theft.destinationBindings().getFirst().id()).playerSaveFence(), "claimed player theft retains the same exact save fence");
        assertEquals(64, afterTheft.inventory().fungibleResources().totalQuantity(hive, "minecraft:rotten_flesh"));
        assertEquals(afterTheft, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(afterTheft)));
        FungibleResourceHandoffObserved bypass = new FungibleResourceHandoffObserved(theft.sourceAccountId(), theft.destinationAccount(),
                theft.sourceEpoch(), theft.destinationEpoch(), theft.lotQuantities(), theft.claimQuantities(), theft.remainingSource(), theft.destinationBindings());
        assertInstanceOf(CommandPlan.Rejected.class, FrontierWorldPhysicalObservationProcess.planFungibleHandoff(active, bypass));
        assertEquals(Map.of(lotId, 32), afterTheft.inventory().fungibleResources().accounts().get(theft.destinationAccount().id()).lotQuantities());
    }

    private static FrontierWorldState hiveGrowthTaskState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:fungible-claim-theft"), 93L));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-nutrient"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, java.util.Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-nutrient"), objective.id(), hive, StrategicTaskKind.GROW_HIVE_ORGANISM,
                java.util.Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), List.of(), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static FungibleStackLayoutObserved observation(SubjectId account, long epoch, String kind, int first, int second) {
        return new FungibleStackLayoutObserved(account, epoch, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 1)), kind, first),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 2)), kind, second)));
    }

    private static FrontierCommand command(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<?> engine, WorldId world, String id,
                                           io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CommandId command = new CommandId("command:fungible-" + id); var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), payload);
    }
}
