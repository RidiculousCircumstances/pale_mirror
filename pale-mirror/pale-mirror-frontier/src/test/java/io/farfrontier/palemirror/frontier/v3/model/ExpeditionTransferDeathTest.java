package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Death preimage cases, not a substitute for actual Vanilla pre-loot evidence. */
class ExpeditionTransferDeathTest {
    @Test void preparedRecipientDeathSettlesOnlyTheWitnessedSideAndCannotReplayOrMintStock() {
        var config = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:expedition-pending-death"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        FrontierWorldState ready = null; TransportMission mission = null;
        for (int boundary = 0; boundary < 2048; boundary++) {
            var state = engine.canonicalState().state();
            for (var m : state.shipments().missions().values()) {
                var a = m.supplies().flatMap(ExpeditionSupplyLoad::next).orElse(null);
                if (a != null && ExpeditionSupplyAuthority.execution(state, m, a).isPresent()
                        && !state.actorMovements().containsKey(a.actorId())
                        && state.actorLocations().get(a.actorId()).supportingSurface().equals(m.sender().station())) {
                    ready = state; mission = m; break;
                }
            }
            if (ready != null) break;
            var due = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, a)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertNotNull(ready, "registered dispatch must reach a real loading participant");
        var allocation = mission.supplies().orElseThrow().next().orElseThrow();
        var state = ActorBodyAuthority.demand(ready, allocation.actorId());
        var body = ActorBodyAuthority.current(state, allocation.actorId()); state = ActorBodyAuthority.running(state, body);
        var resources = state.inventory().fungibleResources();
        var quantities = new TreeMap<String, Integer>();
        for (var entry : resources.accounts().get(allocation.sourceAccountId()).lotQuantities().entrySet())
            quantities.merge(resources.lots().get(entry.getKey()).itemKind(), entry.getValue(), Integer::sum);
        var source = new ArrayList<FungiblePhysicalObservation.Stack>(); int slot = 0;
        for (var entry : quantities.entrySet()) for (int q = entry.getValue(); q > 0; q -= Math.min(q, 64))
            source.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(
                    mission.sender().containerId(), slot++)), entry.getKey(), Math.min(q, 64)));
        resources = resources.rebind(allocation.sourceAccountId(), 7, FungiblePhysicalObservation.bind(resources, allocation.sourceAccountId(), 7, source));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var execution = ExpeditionSupplyAuthority.execution(state, mission, allocation).orElseThrow();
        var order = mission.supplies().orElseThrow().order(mission.id(), mission.sender(), allocation);
        var step = new ActorItemTransferStep(new ActorHotObservation(new ActorActuationId(body, execution), 1),
                MaterialSourceSelection.select(resources, order), body.physicalEpoch());
        state = state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replaceSupplies(mission,
                mission.supplies().orElseThrow().replace(allocation, allocation.prepare(step)))));
        var prepared = state;
        long tick = engine.checkpoint().instant().ticks();
        var died = ActorBodyAuthority.died(prepared, new ActorBodyDied(body, prepared.actorLocations().get(body.actorId()).body(),
                prepared.actorLocations().get(body.actorId()).condition().health(), Optional.empty(), Optional.of(execution), "fixture:pre-loot"),
                FrontierActorDeathConsequences.INSTANCE, tick);
        for (boolean applied : List.of(false, true)) {
            var remainder = new ArrayList<FungiblePhysicalObservation.Stack>();
            for (var stack : source) {
                int moved = step.source().stream().filter(s -> s.address().equals(stack.address())).mapToInt(MaterialSourceSelection.Slice::moved).sum();
                int q = stack.quantity() - (applied ? moved : 0);
                if (q > 0) remainder.add(new FungiblePhysicalObservation.Stack(stack.address(), stack.itemKind(), q));
            }
            var destination = applied ? List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(body.actorId(),
                    ActorBodyId.entityId(state.bootstrap().worldId(), body.actorId()), ((ActorItemSlot.Pocket) allocation.slot()).index()),
                    order.portion().itemKind(), allocation.quantity())) : List.<FungiblePhysicalObservation.Stack>of();
            var receipt = new ExpeditionTransferDeathObserved(mission.id(), allocation.claimId(), body, step, applied, remainder, destination);
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
            assertThrows(IllegalArgumentException.class, () -> ExpeditionTransferDeathAuthority.observed(prepared, receipt));
            var after = ExpeditionTransferDeathAuthority.observed(died, receipt);
            FrontierReferenceClosure.validateTransition(died, after, List.of());
            assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
            assertFalse(after.inventory().fungibleResources().claims().containsKey(allocation.claimId()));
            assertEquals(died.inventory().economics(), after.inventory().economics());
            assertEquals(total(died), total(after), "death settlement transfers or releases, never destroys/invents resources");
            var settled = after.shipments().missions().get(mission.id()).supplies().orElseThrow().allocations().stream()
                    .filter(a -> a.claimId().equals(allocation.claimId())).findFirst().orElseThrow();
            assertEquals(applied, settled.loaded()); assertEquals(!applied, settled.withdrawn());
            assertThrows(IllegalArgumentException.class, () -> ExpeditionTransferDeathAuthority.observed(after, receipt));
        }
    }
    private static long total(FrontierWorldState state) {
        return state.inventory().fungibleResources().accounts().values().stream().flatMap(a -> a.lotQuantities().values().stream()).mapToLong(Integer::longValue).sum();
    }
}
