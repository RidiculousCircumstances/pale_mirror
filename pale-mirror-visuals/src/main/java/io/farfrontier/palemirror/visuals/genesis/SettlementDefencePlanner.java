package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.SettlementLayoutArchetype;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.List;

/** Reusable enclosure grammar with explicit traversable gates. */
final class SettlementDefencePlanner {
    private static final int FRONT = 78;

    private SettlementDefencePlanner() { }

    static List<LinearFeaturePlan> plan(SettlementLayoutArchetype archetype, VisualPoint anchor, int direction) {
        List<LinearFeaturePlan> result = new ArrayList<>();
        int civicCross = archetype == SettlementLayoutArchetype.FREIGHT_CROSSROADS ? 15 : 8;
        wall(result, "rear_west", anchor, direction, -59, -87, -5, -87);
        gate(result, "rear_gate", anchor, direction, -4, -87, 4, -87);
        wall(result, "rear_east", anchor, direction, 5, -87, 59, -87);
        side(result, "west", anchor, direction, -59, civicCross);
        side(result, "east", anchor, direction, 59, civicCross);
        // The public freight arch and the defensive opening are one gateway.
        // The former 65/78 split produced two consecutive gate structures on
        // the same road and read as accidental duplication.
        wall(result, "front_west", anchor, direction, -59, FRONT, -5, FRONT);
        gate(result, "front_freight_gate", anchor, direction, -4, FRONT, 4, FRONT);
        wall(result, "front_east", anchor, direction, 5, FRONT, 59, FRONT);
        if (archetype != SettlementLayoutArchetype.FOOTHILL_RIBBON) {
            result.add(SettlementLayoutGeometry.line("west_approach", LinearFeatureKind.DITCH,
                    anchor, direction, -64, 26, -64, 61, 2));
        }
        return List.copyOf(result);
    }

    private static void side(List<LinearFeaturePlan> target, String id, VisualPoint anchor,
                             int direction, int right, int civicCross) {
        wall(target, id + "_rear", anchor, direction, right, -87, right, civicCross - 5);
        gate(target, id + "_gate", anchor, direction, right, civicCross - 4, right, civicCross + 4);
        wall(target, id + "_front", anchor, direction, right, civicCross + 5, right, FRONT);
    }

    private static void wall(List<LinearFeaturePlan> target, String id, VisualPoint anchor,
                             int direction, int rightA, int inwardA, int rightB, int inwardB) {
        target.add(SettlementLayoutGeometry.line(id, LinearFeatureKind.PALISADE,
                anchor, direction, rightA, inwardA, rightB, inwardB, 2));
    }

    private static void gate(List<LinearFeaturePlan> target, String id, VisualPoint anchor,
                             int direction, int rightA, int inwardA, int rightB, int inwardB) {
        target.add(SettlementLayoutGeometry.line(id, LinearFeatureKind.PALISADE_GATE,
                anchor, direction, rightA, inwardA, rightB, inwardB, 3));
    }
}
