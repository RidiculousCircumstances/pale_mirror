package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

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
        SubjectId project = new SubjectId("construction:codec"), cargo = new SubjectId("cargo:route-build-codec"),
                item = new SubjectId("item:codec-concrete"), cargoItem = new SubjectId("item:route-build-codec");
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:route-build-codec"), PhysicalIntentKind.ROUTE_CONSTRUCTION,
                PhysicalIntentStatus.PREPARED, FrontierRouteNetwork.OWNER, List.of(FrontierRouteNetwork.OWNER, project, cargo, cargoItem),
                new FixedPosition(FixedScalar.whole(12), FixedScalar.whole(64), FixedScalar.whole(18)), 0, PhysicalPostcondition.ROUTE_CONSTRUCTION_OBSERVED);
        RouteConstructionObservation observation = new RouteConstructionObservation(new PhysicalObservationId("observation:route-build-codec"), intent.id(),
                project, cargoItem, new BlockPosition(12, 64, 18));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(transition, codecs.decode(transition.type(), codecs.encode(transition)));
        PhysicalIntent loadIntent = new PhysicalIntent(new PhysicalIntentId("intent:route-load-codec"), PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING,
                PhysicalIntentStatus.PREPARED, project, List.of(FrontierRouteNetwork.OWNER, project, cargo, cargoItem, item),
                new FixedPosition(FixedScalar.whole(12), FixedScalar.whole(64), FixedScalar.whole(18)), 0,
                PhysicalPostcondition.ROUTE_CONSTRUCTION_MATERIAL_LOADED_OBSERVED);
        RouteConstructionMaterialLoadObservation load = new RouteConstructionMaterialLoadObservation(new PhysicalObservationId("observation:route-load-codec"),
                loadIntent.id(), project, cargo, item, cargoItem, 63);
        PhysicalIntentTransition loadTransition = new PhysicalIntentTransition(loadIntent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(load));
        assertEquals(loadTransition, codecs.decode(loadTransition.type(), codecs.encode(loadTransition)));
        RouteConstruction routeProject = new RouteConstruction(project, new SubjectId("settlement:1"), List.of(new BlockPosition(0, 64, 0),
                new BlockPosition(1, 64, 0), new BlockPosition(2, 64, 0)), 0, RouteConstructionStatus.BUILDING);
        RouteConstructionStarted started = new RouteConstructionStarted(routeProject); RouteTopologyCutover cutover = new RouteTopologyCutover(routeProject.id());
        RouteConstructionMaterialLoaded loaded = new RouteConstructionMaterialLoaded(project, new CargoBatch(cargo, FrontierRouteNetwork.OWNER, List.of(cargoItem)));
        assertEquals(started, codecs.decode(started.type(), codecs.encode(started))); assertEquals(cutover, codecs.decode(cutover.type(), codecs.encode(cutover)));
        assertEquals(loaded, codecs.decode(loaded.type(), codecs.encode(loaded)));
    }
}
