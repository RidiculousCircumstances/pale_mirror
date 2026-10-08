package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorIdentityDeclarationsTest {
    @Test void derivedRosterIsReusedWithoutSkippingTheCurrentActorDeclarationCheck() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var expected = ActorIdentityDeclarations.validate(state.bootstrap(), state.hiveColony(), state.humanPopulation(),
                state.actorLocations(), state.transportFleet());
        var resident = state.humanPopulation().residents().keySet().iterator().next();
        var moved = state.withActorBody(resident, state.actorLocations().get(resident).body());
        assertSame(expected, ActorIdentityDeclarations.validate(moved.bootstrap(), moved.hiveColony(), moved.humanPopulation(),
                moved.actorLocations(), moved.transportFleet()));
        var forged = new java.util.LinkedHashMap<>(moved.actorLocations());
        var person = forged.get(resident);
        forged.put(resident, new ActorLocation(person.body(), person.condition(), ActorKind.BIOFORM));
        assertThrows(IllegalArgumentException.class, () -> ActorIdentityDeclarations.validate(moved.bootstrap(),
                moved.hiveColony(), moved.humanPopulation(), forged, moved.transportFleet()));
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(expected, ActorIdentityDeclarations.validate(recovered.bootstrap(), recovered.hiveColony(),
                recovered.humanPopulation(), recovered.actorLocations(), recovered.transportFleet()));
    }
    @Test void producersDeclareKindUpdatesRetainItAndForeignDeclarationFailsClosed() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var resident = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var bioform = state.bootstrap().hive().bioforms().getFirst().id();
        var person = state.actorLocations().get(resident);
        var creature = state.actorLocations().get(bioform);
        assertEquals(ActorKind.RESIDENT, person.kind());
        assertEquals(ActorKind.BIOFORM, creature.kind());
        assertEquals(ActorKind.BIOFORM, creature.withBody(person.body()).kind());
        assertEquals(ActorKind.BIOFORM, creature.deadAt(creature.body()).kind());
        assertEquals(ActorKind.RESIDENT, person.withObserved(person.body(),
                person.condition().withHealth(FixedScalar.whole(7))).kind());
        var codec = new FrontierWorldStateCodec();
        assertEquals(state.actorLocations(), codec.decode(codec.encode(state)).actorLocations());
        var forged = new java.util.LinkedHashMap<>(state.actorLocations());
        forged.put(resident, new ActorLocation(person.body(), person.condition(), ActorKind.BIOFORM));
        assertThrows(IllegalArgumentException.class, () -> state.withChanges(
                FrontierWorldStateUpdate.begin().actorLocations(forged)));
        assertThrows(NullPointerException.class, () -> new ActorLocation(person.body(), person.condition(), null));
        assertThrows(IllegalArgumentException.class, () -> FrontierWireTags.require(ActorKind.class, 127));
    }
}
