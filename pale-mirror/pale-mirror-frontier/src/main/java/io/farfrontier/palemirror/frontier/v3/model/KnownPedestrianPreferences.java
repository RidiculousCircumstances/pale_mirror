package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianTraversalPolicy;
import java.util.HashSet;
import java.util.List;

/** Building annotations contribute spatial meaning; the navigator owns how it affects a route. */
public final class KnownPedestrianPreferences {
    private static final ImmutableInputView<PedestrianTraversalPolicy> VIEWS = new ImmutableInputView<>();
    private KnownPedestrianPreferences() { }
    public static PedestrianTraversalPolicy of(FrontierBootstrap bootstrap) {
        return VIEWS.get(List.of(bootstrap), () -> {
            var obstacles = new HashSet<BlockPosition>();
            for (var settlement : bootstrap.settlements()) for (var structure : settlement.structures()) {
                if (structure.definition().representation() != BuildingDefinition.Representation.AUTHORED) continue;
                var plan = BuildingSpatialPlan.of(structure);
                var floors = plan.authoredFloors();
                for (var position : plan.blocks().keySet()) {
                    var support = new SurfaceAnchor(position);
                    if (!floors.contains(support) && !plan.circulation().contains(support)) obstacles.add(position);
                }
            }
            return new PedestrianTraversalPolicy(RegionalBlockSet.copyOf(obstacles));
        });
    }
}
