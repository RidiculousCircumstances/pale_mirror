package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3AmbientActorLocalTargetsTest {
    @Test
    void presentationIdlingCannotReachAnotherCanonicalSupportColumn() {
        assertEquals(0.20D, FrontierV3AmbientActorLocalTargets.boundedPresentationRadius(0.20D));
        assertEquals(0.40D, FrontierV3AmbientActorLocalTargets.boundedPresentationRadius(1.50D));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3AmbientActorLocalTargets.boundedPresentationRadius(-0.01D));
    }

    @Test
    void presentationAnchorFollowsTheCanonicalActorRatherThanAStaleLeaseOrigin() {
        var state = FrontierV3FixtureCatalog.serviceDecontaminationConfiguration(new WorldId("frontier:ambient-local-anchor"), 41L).initialState();
        SubjectId medic = new SubjectId("resident:9-29");
        var moved = state.withActorBody(medic, new BodyPosition(-334, 65, 310));

        assertEquals(moved.actorLocations().get(medic).supportingSurface().support(), FrontierV3AmbientActorLocalTargets.localAnchor(moved, medic));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3AmbientActorLocalTargets.localAnchor(moved, new SubjectId("resident:missing")));
    }

    @Test
    void localTargetsUseTheCanonicalFeetHeightRatherThanTheSupportBlock() {
        var state = FrontierV3FixtureCatalog.serviceDecontaminationConfiguration(new WorldId("frontier:ambient-local-height"), 41L).initialState();
        SubjectId medic = new SubjectId("resident:9-29");

        assertEquals(state.actorLocations().get(medic).body().y(),
                FrontierV3AmbientActorLocalTargets.targetFeetY(state.actorLocations().get(medic).body()));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3AmbientActorLocalTargets.targetFeetY(null));
    }
}
