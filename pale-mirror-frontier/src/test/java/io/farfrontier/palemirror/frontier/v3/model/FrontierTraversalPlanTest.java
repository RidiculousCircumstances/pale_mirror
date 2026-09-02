package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierTraversalPlanTest {
    @Test void compiledPortsJoinOnlyTheirExplicitDeclaredPublicTopology() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:traversal-plan"), 7_312L));
        FrontierTraversalPlan plan = FrontierTraversalPlan.compile(state);

        assertEquals(36, plan.facilities().size());
        assertEquals(72, plan.topologies().size(), "supply, circulation, facility ingress and one resident ingress topology per settlement");
        for (FrontierTraversalPlan.FacilityBinding binding : plan.facilities().values()) {
            TraversalTopology publicTopology = plan.publicTopologyFor(binding.port().facilityId());
            assertTrue(publicTopology.nodes().containsValue(binding.port().exteriorApproach().getFirst()),
                    "a port may not rely on a nearest-route query or hidden approach");
            assertTrue(binding.port().ingressTopology(new TraversalTopologyId("topology:test-" + binding.port().facilityId().value().replace(':', '-')),
                    1L, binding.port().facilityId()).edges().stream().allMatch(edge -> edge.clearance() >= 2 && edge.grade() <= 1));
        }
    }
}
