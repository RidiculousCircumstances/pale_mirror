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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteConstructionPayloadCodecTest {
    @Test
    void constructionObservationRoundTripsInsideTheDurableIntentTransition() {
        SubjectId project = new SubjectId("construction:codec"), item = new SubjectId("item:codec-concrete");
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:route-build-codec"), PhysicalIntentKind.ROUTE_CONSTRUCTION,
                PhysicalIntentStatus.PREPARED, FrontierRouteNetwork.OWNER, List.of(FrontierRouteNetwork.OWNER, project, item),
                new FixedPosition(FixedScalar.whole(12), FixedScalar.whole(64), FixedScalar.whole(18)), 0, PhysicalPostcondition.ROUTE_CONSTRUCTION_OBSERVED);
        RouteConstructionObservation observation = new RouteConstructionObservation(new PhysicalObservationId("observation:route-build-codec"), intent.id(),
                project, item, new BlockPosition(12, 64, 18));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(transition, codecs.decode(transition.type(), codecs.encode(transition)));
    }
}
