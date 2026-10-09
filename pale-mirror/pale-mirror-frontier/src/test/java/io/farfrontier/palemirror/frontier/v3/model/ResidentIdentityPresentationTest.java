package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentIdentityPresentationTest {
    @Test
    void explicitNamesSurviveRecoveryAndChangingBootstrapAffinityDoesNotAssignWork() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:names"), 91L));
        var person = state.humanPopulation().residents().values().iterator().next();
        assertEquals(ResidentNames.create(91L, person.id()), person.name());
        assertFalse(person.name().isBlank());
        var renamedAffinity = person.withRole(ResidentRole.GUARD);
        state = state.withHumanPopulation(state.humanPopulation().withProfile(renamedAffinity));
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(state, recovered);
        var view = ResidentPresentation.from(recovered, person.id());
        assertEquals(person.name(), view.name());
        assertEquals("None", view.task());
        assertEquals("Unassigned", view.role());
        assertEquals("Idle", view.activity());
        var forgedName = new ResidentProfile(person.id(), person.householdId(), person.settlementId(), person.role(),
                "Another Name", person.birthTick(), person.skills(), person.capabilities());
        assertThrows(IllegalArgumentException.class, () -> recovered.humanPopulation().withProfile(forgedName));
    }

    @Test
    void profileRejectsUnboundedOrControlCharacterNames() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:names-invalid"), 91L));
        var person = state.humanPopulation().residents().values().iterator().next();
        for (String invalid : new String[]{"", " leading", "line\nname", "a".repeat(49)}) {
            assertThrows(IllegalArgumentException.class, () -> new ResidentProfile(person.id(), person.householdId(),
                    person.settlementId(), person.role(), invalid, person.birthTick(), person.skills(), person.capabilities()));
        }
    }
}
