package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExplosionObservationTest {
    @Test
    void durableExplosionReceiptBindsExactGeometryAndRoundTrips() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:explosion-receipt"), 91L));
        SubjectId bomber = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.BOMBER).findFirst().orElseThrow().id();
        FixedPosition origin = new FixedPosition(FixedScalar.whole(-400), FixedScalar.whole(64), FixedScalar.whole(400));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:explosion-test"), PhysicalIntentKind.EXPLOSION,
                PhysicalIntentStatus.PREPARED, bomber, List.of(bomber), origin, 4, PhysicalPostcondition.EXPLOSION_OBSERVED);
        ExplosionObservation receipt = new ExplosionObservation(new PhysicalObservationId("observation:explosion-test"), intent.id(), origin, 4, 12, 8);

        FrontierWorldState running = state.preparePhysicalIntent(intent)
                .transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> running.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED,
                Optional.of(new ExplosionObservation(receipt.id(), intent.id(), origin, 3, 12, 8))));

        FrontierWorldState confirmed = running.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(PhysicalIntentStatus.CONFIRMED, confirmed.physicalIntents().get(intent.id()).status());
        assertEquals(receipt, confirmed.physicalObservations().get(receipt.id()));
        assertEquals(confirmed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(confirmed)));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(transition, FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition)));
    }
}
