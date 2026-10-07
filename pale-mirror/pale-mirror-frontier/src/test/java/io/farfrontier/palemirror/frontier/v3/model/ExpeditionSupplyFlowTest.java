package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Registered dispatch, movement and custody must really load provisions before an outbound order. */
class ExpeditionSupplyFlowTest {
    @Test void observedSourceRemovalWithdrawsPromisesAtomicallyWithoutFoodDuplicationOrPayment() {
        var config = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:expedition-source-change"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        for (int boundary = 0; boundary < 256 && engine.canonicalState().state().shipments().missions().isEmpty(); boundary++) {
            var current = engine.canonicalState().state();
            var due = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        var state = engine.canonicalState().state();
        var mission = state.shipments().missions().values().iterator().next();
        var allocation = mission.supplies().orElseThrow().next().orElseThrow();
        var resources = state.inventory().fungibleResources();
        var quantities = new TreeMap<String, Integer>();
        resources.accounts().get(allocation.sourceAccountId()).lotQuantities().forEach((lot, q) -> quantities.merge(resources.lots().get(lot).itemKind(), q, Integer::sum));
        var layout = new ArrayList<FungiblePhysicalObservation.Stack>(); int slot = 0;
        for (var entry : quantities.entrySet()) for (int q = entry.getValue(); q > 0; q -= Math.min(64, q))
            layout.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(mission.sender().containerId(), slot++)), entry.getKey(), Math.min(64, q)));
        var bound = resources.rebind(allocation.sourceAccountId(), 7, FungiblePhysicalObservation.bind(resources, allocation.sourceAccountId(), 7, layout));
        state = state.withInventory(state.inventory().withFungibleResources(bound));
        var binding = bound.bindings().values().stream().filter(b -> b.accountId().equals(allocation.sourceAccountId())
                && b.claimQuantities().containsKey(allocation.claimId())).findFirst().orElseThrow();
        var player = UUID.fromString("00000000-0000-0000-0000-000000000232");
        var observation = FungiblePhysicalHandoff.departToNew(bound, allocation.sourceAccountId(), 7, binding, binding.quantity() - 1,
                new SubjectId("custody:expedition-player-removal"), new ResourceCustody.Player(player), 1,
                new PhysicalStackAddress.PlayerSlot(player, 0)).forfeitAffectedClaims(bound);
        var balances = state.inventory().economics().accounts(); int beforeBread = bread(state);
        assertTrue(FungibleClaimForfeitureStateSupport.supports(state, observation));
        var after = FungibleClaimForfeitureStateSupport.apply(state, observation);
        FrontierReferenceClosure.validateTransition(state, after, List.of());
        assertEquals(beforeBread, bread(after));
        assertEquals(balances, after.inventory().economics().accounts());
        var load = after.shipments().missions().get(mission.id()).supplies().orElseThrow();
        assertTrue(load.needsReplan()); assertFalse(load.complete());
        assertTrue(observation.forfeitedClaimIds().stream().noneMatch(after.inventory().fungibleResources().claims()::containsKey));
        assertFalse(after.inventory().fungibleResources().accounts().containsKey(allocation.destinationAccountId()));
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
        // The removed source lot also held merchandise: no cargo survives to justify departure.
        assertTrue(mission.shipmentIds().stream().map(after.shipments().shipments()::get).allMatch(Shipment::terminal));
        var returning = io.farfrontier.palemirror.frontier.v3.process.TransportMissionProcess.advance(after, mission.id(),
                new TransportMissionAdvanced(mission.id(), mission.revision(), TransportMission.Stage.RETURNING), engine.checkpoint().instant().ticks());
        assertEquals(beforeBread, bread(returning));
        assertTrue(returning.shipments().missions().get(mission.id()).supplies().isEmpty());
        assertTrue(returning.inventory().economics().budgets().containsKey(mission.financialBudgetId().orElseThrow()),
                "return still owns its finite budget; loading abort does not fabricate completed return");
        assertEquals(returning, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(returning)));
    }
    @Test void actualLoadingConservesStockAndResumesItsRetainedObligationAfterCheckpoint() {
        var config = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:expedition-load"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        SubjectId missionId = null; boolean recovered = false, departed = false;
        Set<SubjectId> declaredClaims = Set.of();
        int initialBread = bread(config.initialState());
        for (int boundary = 0; boundary < 2048; boundary++) {
            var state = engine.canonicalState().state();
            if (missionId == null && !state.shipments().missions().isEmpty()) {
                var mission = state.shipments().missions().values().iterator().next(); missionId = mission.id();
                var supplies = mission.supplies().orElseThrow();
                assertFalse(supplies.complete(), "initial stock is still at the depot, not in participant pockets");
                declaredClaims = supplies.allocations().stream().map(a -> a.claimId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
                assertFalse(declaredClaims.isEmpty());
                assertTrue(declaredClaims.stream().allMatch(state.inventory().fungibleResources().claims()::containsKey));
            }
            if (missionId != null) {
                var mission = state.shipments().missions().get(missionId); var supplies = mission.supplies().orElseThrow();
                if (!recovered && supplies.allocations().stream().anyMatch(a -> a.loaded()) && !supplies.complete()) {
                    var checkpoint = engine.checkpoint(); var codec = new FrontierWorldStateCodec();
                    assertEquals(state, codec.decode(codec.encode(state)));
                    engine = FrontierEngines.recoverCanonicalStateAccess(config,
                            new RecoveryImage(config.worldId(), Optional.of(new SnapshotRecord(checkpoint, 0)), List.of()));
                    assertEquals(supplies, engine.canonicalState().state().shipments().missions().get(missionId).supplies().orElseThrow());
                    recovered = true;
                }
                if (mission.stage() == TransportMission.Stage.OUTBOUND) {
                    assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(
                            state, mission, engine.checkpoint().instant().ticks()));
                    assertFalse(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(
                            state, mission, engine.checkpoint().instant().ticks() + 100_000L),
                            "a completed old load receipt is not a fresh nutrition/route forecast");
                    assertTrue(supplies.complete());
                    assertTrue(declaredClaims.stream().noneMatch(state.inventory().fungibleResources().claims()::containsKey));
                    for (var entry : supplies.foodTargets().entrySet()) assertTrue(UnitInventory.accounts(state.inventory().fungibleResources(), entry.getKey())
                            .stream().filter(a -> a.claimQuantities().isEmpty()).mapToInt(a -> state.inventory().fungibleResources()
                                    .unclaimedQuantity(a.id(), mission.sender().settlementId(), supplies.foodKind())).sum() >= entry.getValue());
                    assertEquals(initialBread, bread(state), "loading changes custody, never creates or consumes stock");
                    departed = true; break;
                }
            }
            var current = engine.canonicalState().state();
            var due = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertNotNull(missionId, "accepted contract must dispatch its feasible provisioned group");
        assertTrue(recovered, "checkpoint splits actual provisioning receipts, not a fabricated loaded fixture");
        assertTrue(departed, "members must load, clear the depot and gather before departure");
    }
    private static int bread(FrontierWorldState state) {
        return state.inventory().fungibleResources().accounts().values().stream().mapToInt(account -> account.lotQuantities().entrySet().stream()
                .filter(entry -> state.inventory().fungibleResources().lots().get(entry.getKey()).itemKind().equals(FoodCatalog.BREAD))
                .mapToInt(Map.Entry::getValue).sum()).sum();
    }
}
