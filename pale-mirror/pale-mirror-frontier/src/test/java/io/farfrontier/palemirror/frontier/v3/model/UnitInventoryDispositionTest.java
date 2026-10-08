package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class UnitInventoryDispositionTest {
    @Test void carriedAccountIndexIsOrderedImmutableAndInvalidatedByActualCustodyChange() {
        var actor = new SubjectId("resident:inventory-index");
        var other = new SubjectId("resident:inventory-index-other");
        var owner = new SubjectId("settlement:inventory-index");
        var lot = new SubjectId("lot:inventory-index");
        var first = new SubjectId("custody:inventory-index-a");
        var second = new SubjectId("custody:inventory-index-b");
        var ledger = FungibleResourceLedger.empty().issue(new ResourceLot(lot, owner, "minecraft:bread", 2, "fixture", List.of()),
                new CustodyAccount(second, new ResourceCustody.Actor(actor), Map.of(lot, 2), Map.of()));
        var before = ActorCarriedResources.accounts(ledger, actor);
        assertSame(before, ActorCarriedResources.accounts(ledger, actor));
        assertTrue(ActorCarriedResources.accounts(ledger, other).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        var nextLot = new SubjectId("lot:inventory-index-next");
        var changed = ledger.issue(new ResourceLot(nextLot, owner, "minecraft:wheat", 1, "fixture", List.of()),
                new CustodyAccount(first, new ResourceCustody.Actor(actor), Map.of(nextLot, 1), Map.of()));
        assertEquals(List.of(first, second), ActorCarriedResources.accounts(changed, actor).stream().map(CustodyAccount::id).toList());
        assertEquals(List.of(second), ActorCarriedResources.accounts(ledger, actor).stream().map(CustodyAccount::id).toList());
        var reassigned = new FungibleResourceLedger(changed.lots(), changed.claims(), Map.of(
                first, changed.accounts().get(first),
                second, new CustodyAccount(second, new ResourceCustody.Actor(other), Map.of(lot, 2), Map.of())), changed.bindings());
        assertEquals(List.of(first), ActorCarriedResources.accounts(reassigned, actor).stream().map(CustodyAccount::id).toList());
        assertEquals(List.of(second), ActorCarriedResources.accounts(reassigned, other).stream().map(CustodyAccount::id).toList());
    }
    private record Fixture(FrontierWorldState state, ActorBodyId body, SubjectId account) { }
    private Fixture dyingInventory() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var lot = new SubjectId("lot:personal-tools"); var account = new SubjectId("custody:personal-tools");
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot,
                state.humanPopulation().resident(actor).settlementId(), "minecraft:cobblestone", 7, "fixture", List.of()),
                new CustodyAccount(account, new ResourceCustody.Actor(actor), Map.of(lot, 7), Map.of(), Optional.of(new ActorItemSlot.Pocket(3))));
        ledger = ledger.rebind(account, 1, List.of(new PhysicalStackBinding(new SubjectId("binding:personal-tools"), account,
                new PhysicalStackAddress.ActorPocket(actor, ActorBodyId.entityId(state.bootstrap().worldId(), actor), 3),
                1, "minecraft:cobblestone", Map.of(lot, 7), Map.of())));
        return new Fixture(state.withInventory(state.inventory().withFungibleResources(ledger)), body, account);
    }
    @Test void personalStockBindsOnlyToExactLiveBodyAndKeepsItsPlacementAcrossNaturalDeparture() {
        var fixture = dyingInventory(); var state = fixture.state(); var actor = fixture.body().actorId();
        var resources = state.inventory().fungibleResources().releaseBindings(fixture.account(), 1);
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var address = new PhysicalStackAddress.ActorPocket(actor, ActorBodyId.entityId(state.bootstrap().worldId(), actor), 3);
        var receipt = new UnitInventoryBoundObserved(fixture.body(), fixture.account(),
                new FungiblePhysicalObservation.Stack(address, "minecraft:cobblestone", 7));
        var unbound = state;
        assertThrows(IllegalArgumentException.class, () -> UnitInventoryBodyCustody.bind(unbound, actor,
                new UnitInventoryBoundObserved(new ActorBodyId(actor, fixture.body().physicalEpoch() + 1), fixture.account(), receipt.stack())));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
        var bound = UnitInventoryBodyCustody.bind(state, actor, receipt);
        assertThrows(IllegalArgumentException.class, () -> UnitInventoryBodyCustody.bind(bound, actor, receipt));
        assertThrows(IllegalArgumentException.class, () -> UnitInventoryBodyCustody.releaseAfterDeparture(bound, fixture.body()));
        var cold = ActorBodyAuthority.released(bound, fixture.body());
        cold = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(cold));
        assertTrue(cold.inventory().fungibleResources().bindings().values().stream()
                .noneMatch(binding -> binding.accountId().equals(fixture.account())));
        assertEquals(resources.accounts().get(fixture.account()), cold.inventory().fungibleResources().accounts().get(fixture.account()));
        assertEquals(new ActorItemSlot.Pocket(3), UnitInventoryPresentation.inventory(cold, actor).get(fixture.account()).slot());
    }
    @Test void savedDepartureReleasesPhysicalBindingsWithoutSpendingReservedPersonalStock() {
        var fixture = dyingInventory(); var state = fixture.state(); var actor = fixture.body().actorId();
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(fixture.account());
        var lot = account.lotQuantities().keySet().iterator().next();
        var claim = new ClaimAllocation(new SubjectId("claim:personal-reservation"), actor,
                state.humanPopulation().resident(actor).settlementId(), "minecraft:cobblestone", 2,
                Map.of(lot, 2), ClaimPurpose.EXTERNAL_RESERVATION);
        resources = resources.reserveBound(claim, account.id(), 1L);
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var location = state.actorLocations().get(actor);
        var receipt = new ActorBodyUnloaded(fixture.body(), location.body(), location.condition().health(),
                location.body(), location.condition().health(), state.actorExecutions().actors().get(actor).current());
        var cold = ActorBodyAuthority.unloaded(state, receipt);
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(cold, actor));
        assertEquals(resources.accounts(), cold.inventory().fungibleResources().accounts());
        assertEquals(resources.claims(), cold.inventory().fungibleResources().claims());
        assertEquals(resources.lots(), cold.inventory().fungibleResources().lots());
        assertTrue(cold.inventory().fungibleResources().bindings().isEmpty());
        assertEquals(cold, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(cold)));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.unloaded(cold, receipt));
    }
    @Test void exactDeathDropPreservesTitleAndQuantityAndRejectsReplayAndForeignEpoch() {
        var fixture = dyingInventory(); var alive = fixture.state(); var actor = fixture.body().actorId();
        var receipt = new UnitInventoryDispositionObserved(fixture.body(), fixture.account(), 1,
                UnitInventoryDispositionObserved.Outcome.WORLD_DROP, Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000123")));
        assertThrows(IllegalArgumentException.class, () -> UnitInventoryDisposition.apply(alive, actor, receipt));
        var location = alive.actorLocations().get(actor);
        var died = ActorBodyAuthority.died(alive, new ActorBodyDied(fixture.body(), location.body(), location.condition().health(),
                Optional.empty(), alive.actorExecutions().actors().get(actor).current(), "environment"), FrontierActorDeathConsequences.INSTANCE, 10);
        died = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(died));
        var restored = died;
        assertThrows(IllegalArgumentException.class, () -> UnitInventoryDisposition.apply(restored, actor,
                new UnitInventoryDispositionObserved(fixture.body(), fixture.account(), 2, receipt.outcome(), receipt.worldCarrier())));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
        var moved = UnitInventoryDisposition.apply(died, actor, receipt);
        assertEquals(died.inventory().fungibleResources().lots(), moved.inventory().fungibleResources().lots());
        assertFalse(moved.inventory().fungibleResources().accounts().containsKey(fixture.account()));
        assertTrue(moved.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                account.custody().equals(new ResourceCustody.WorldCarrier(receipt.worldCarrier().orElseThrow()))
                        && account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() == 7));
        assertThrows(IllegalArgumentException.class, () -> UnitInventoryDisposition.apply(moved, actor, receipt));
        assertEquals(moved, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(moved)));
    }
}
