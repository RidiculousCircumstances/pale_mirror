package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class TransportFleetTest {
    @Test void finiteBootstrapMobileLocationAndLossSurviveRecoveryWithoutReplacement() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:finite-pack-animals"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var state = FrontierWorldState.initial(bootstrap);
        assertEquals(12, state.transportFleet().assets().size());
        assertEquals(12, state.transportFleet().assets().values().stream().map(a -> a.homeSettlementId()).distinct().count());
        var asset = state.transportFleet().assets().values().iterator().next();
        assertEquals(ActorKind.PACK_ANIMAL, state.actorLocations().get(asset.actorId()).kind());
        var surface = state.inventory().surfaces().get(asset.containerId());
        assertFalse(surface.fixed()); assertEquals(15, state.inventory().containers().get(asset.containerId()).slotCount());
        assertThrows(IllegalStateException.class, surface::position);
        assertEquals(asset.homeStation().support().offset(0, 1, 0), surface.position(state));
        var actors = new LinkedHashMap<>(state.actorLocations());
        var previous = actors.get(asset.actorId());
        actors.put(asset.actorId(), new ActorLocation(previous.body(), ActorCondition.dead(), previous.kind()));
        var lost = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(lost));
        assertEquals(lost, recovered);
        assertEquals(12, recovered.transportFleet().assets().size());
        assertEquals(ActorLifeStatus.DEAD, recovered.actorLocations().get(asset.actorId()).condition().status());
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.demand(recovered, asset.actorId()));
        var orphan = new LinkedHashMap<>(state.inventory().surfaces());
        orphan.put(asset.containerId(), new ContainerSurface(asset.containerId(),
                new ContainerLocation.Mobile(new SubjectId("actor:foreign-pack")), surface.status()));
        var malformed = new ExactInventory(state.inventory().containers(), state.inventory().items(), state.inventory().cargo(),
                state.inventory().playerItems(), state.inventory().worldCarrierItems(), state.inventory().conflicts(), orphan,
                state.inventory().economics(), state.inventory().fungibleResources());
        assertThrows(IllegalArgumentException.class, () -> state.withInventory(malformed));
    }
    @Test void exactReservationCannotBeDoubleBookedOrReleasedByAnotherMission() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:pack-reservation"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1")));
        var fleet = state.transportFleet(); var asset = fleet.assets().values().iterator().next();
        var mission = new SubjectId("mission:first"); var other = new SubjectId("mission:other");
        var reserved = fleet.reserve(asset.actorId(), mission);
        assertThrows(IllegalArgumentException.class, () -> reserved.reserve(asset.actorId(), other));
        assertThrows(IllegalArgumentException.class, () -> reserved.release(asset.actorId(), other));
        assertEquals(fleet, reserved.release(asset.actorId(), mission));
        assertThrows(IllegalArgumentException.class, () -> state.withChanges(FrontierWorldStateUpdate.begin().transportFleet(reserved)));
    }
}
