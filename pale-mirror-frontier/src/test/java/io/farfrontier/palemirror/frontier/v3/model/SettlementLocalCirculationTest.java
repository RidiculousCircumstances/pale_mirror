package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementLocalCirculationTest {
    @Test void everySettlementCompilesAndMaterializesTheSameFlatProviderPublicInfirmaryConnector() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:local-circulation"), 391L);
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(FrontierWorldState.initial(bootstrap));

        for (Settlement settlement : bootstrap.settlements()) {
            SettlementStructure hall = settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst().orElseThrow();
            SettlementStructure infirmary = settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow();
            List<SurfaceAnchor> sidewalk = SettlementLocalCirculation.infirmarySidewalk(settlement);
            TraversalTopology topology = SettlementLocalCirculation.topology(settlement);

            assertEquals(SettlementAccessPort.forHall(hall).routeSurface(), sidewalk.getFirst());
            assertEquals(SettlementInfirmaryTreatmentPort.forInfirmary(infirmary).exteriorApproachSurface(), sidewalk.getLast());
            assertTrue(topology.edges().stream().allMatch(edge -> edge.grade() <= 1 && edge.clearance() >= 2
                    && edge.traversableBy(TraversalCapability.PEDESTRIAN)));
            assertTrue(topology.edges().stream().allMatch(edge -> edge.grade() == 0),
                    "the flat graybox provider may not invent an excavated terrain datum");
            assertEquals(2 * (sidewalk.size() - 1), topology.edges().size(), "each declared public edge is available in both directions");
            for (SurfaceAnchor surface : sidewalk) {
                GrayboxCell cell = plan.cells().get(surface.support());
                assertTrue(cell != null, "every compiled sidewalk surface must be materialized: " + surface);
            }
        }
    }
}
