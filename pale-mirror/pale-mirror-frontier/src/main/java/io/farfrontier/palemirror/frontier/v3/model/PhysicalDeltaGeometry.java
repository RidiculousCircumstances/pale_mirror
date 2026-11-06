package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Semantic loss is not unknown collision after the physical observer supplied its actual shape. */
public final class PhysicalDeltaGeometry {
    private static final ImmutableResultView<String, RegionalBlockSet> UNKNOWN = new ImmutableResultView<>(16);
    private PhysicalDeltaGeometry() { }
    public static RegionalBlockSet unknown(FrontierWorldState state) {
        return UNKNOWN.get(List.of(state.physicalDeltas(), state.navigationGeometry()), "unknown", () -> {
            var result = new HashSet<BlockPosition>();
            for (var p : state.physicalDeltas().keySet()) {
                var observation = state.navigationGeometry().cells().get(p);
                if (observation == null || observation.cell().isEmpty()) result.add(p);
            }
            return RegionalBlockSet.copyOf(result);
        });
    }
}
