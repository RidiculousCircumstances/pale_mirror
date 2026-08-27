package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Source-phase-shaped operation scenery.
 *
 * <p>The output is only an immutable presentation plan. It has no timer,
 * movement, local outcome or actor ownership: the operation activity from the
 * source snapshot remains the sole authority for all of those facts.</p>
 */
final class SourceGrayboxOperationScenePlan {
    private SourceGrayboxOperationScenePlan() { }

    /** The exact {@code ReferenceOperationStatus} vocabulary, fail-closed at the physical boundary. */
    static List<SourceGrayboxPresentationPlan.Desired> from(String revision, ReferenceGrayboxSnapshot.Activity activity, int sceneY) {
        String prefix = "activity:" + activity.id();
        int x = activity.position().x();
        int z = activity.position().z();
        List<SourceGrayboxPresentationPlan.Desired> result = new ArrayList<>();
        switch (activity.phase()) {
            case "assembling" -> {
                assemblyCorner(result, revision, activity, prefix, "north-west", x - 2, z - 2, sceneY);
                assemblyCorner(result, revision, activity, prefix, "north-east", x + 2, z - 2, sceneY);
                assemblyCorner(result, revision, activity, prefix, "south-west", x - 2, z + 2, sceneY);
                assemblyCorner(result, revision, activity, prefix, "south-east", x + 2, z + 2, sceneY);
                beacon(result, revision, activity, prefix, x, z, sceneY, 2);
            }
            case "en_route" -> {
                result.add(desired(prefix + ":scene:outbound-west", activity, "ACTIVITY_OPERATION_OUTBOUND_COLUMN", revision,
                        x - 2, sceneY, z, 2, 1, 1));
                result.add(desired(prefix + ":scene:outbound-east", activity, "ACTIVITY_OPERATION_OUTBOUND_COLUMN", revision,
                        x + 1, sceneY, z, 2, 1, 1));
                beacon(result, revision, activity, prefix, x, z, sceneY, 5);
            }
            case "on_station" -> {
                result.add(desired(prefix + ":scene:station-north", activity, "ACTIVITY_OPERATION_STATION", revision,
                        x - 2, sceneY, z - 2, 5, 1, 2));
                result.add(desired(prefix + ":scene:station-south", activity, "ACTIVITY_OPERATION_STATION", revision,
                        x - 2, sceneY, z + 2, 5, 1, 2));
                result.add(desired(prefix + ":scene:station-west", activity, "ACTIVITY_OPERATION_STATION", revision,
                        x - 2, sceneY, z - 1, 1, 3, 2));
                result.add(desired(prefix + ":scene:station-east", activity, "ACTIVITY_OPERATION_STATION", revision,
                        x + 2, sceneY, z - 1, 1, 3, 2));
                beacon(result, revision, activity, prefix, x, z, sceneY, 3);
            }
            case "returning" -> {
                result.add(desired(prefix + ":scene:return-north", activity, "ACTIVITY_OPERATION_RETURN_COLUMN", revision,
                        x, sceneY, z - 2, 1, 2, 1));
                result.add(desired(prefix + ":scene:return-south", activity, "ACTIVITY_OPERATION_RETURN_COLUMN", revision,
                        x, sceneY, z + 1, 1, 2, 1));
                beacon(result, revision, activity, prefix, x, z, sceneY, 5);
            }
            case "engaged", "intercepted" -> {
                result.add(desired(prefix + ":scene:battle-west", activity, "ACTIVITY_OPERATION_BATTLE_LINE", revision,
                        x - 2, sceneY, z - 1, 1, 3, 2));
                result.add(desired(prefix + ":scene:battle-east", activity, "ACTIVITY_OPERATION_BATTLE_LINE", revision,
                        x + 2, sceneY, z - 1, 1, 3, 2));
                beacon(result, revision, activity, prefix, x, z, sceneY, 5);
            }
            case "completed", "aborted" -> throw new IllegalStateException("terminal operation was offered for materialization: " + activity.id());
            default -> throw new IllegalStateException("unknown source operation phase: " + activity.phase());
        }
        return List.copyOf(result);
    }

    private static void assemblyCorner(List<SourceGrayboxPresentationPlan.Desired> result, String revision,
                                       ReferenceGrayboxSnapshot.Activity activity, String prefix, String corner, int x, int z, int sceneY) {
        result.add(desired(prefix + ":scene:assembly:" + corner, activity, "ACTIVITY_OPERATION_ASSEMBLY", revision,
                x, sceneY, z, 1, 1, 2));
    }

    private static void beacon(List<SourceGrayboxPresentationPlan.Desired> result, String revision,
                               ReferenceGrayboxSnapshot.Activity activity, String prefix, int x, int z, int sceneY, int height) {
        result.add(desired(prefix + ":scene:beacon", activity, "ACTIVITY_BEACON", revision, x, sceneY, z, 1, 1, height));
    }

    private static SourceGrayboxPresentationPlan.Desired desired(String id, ReferenceGrayboxSnapshot.Activity activity, String kind,
                                                                  String revision, int x, int y, int z, int width, int depth, int height) {
        return new SourceGrayboxPresentationPlan.Desired(id, activity.id(), kind, revision, x, y, z, width, depth, height, activity.colour());
    }
}
