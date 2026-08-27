package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Source-phase-shaped V2 frontier campaign scenery.
 *
 * <p>The source campaign already owns phase, people, supply and outcome. This
 * compiler only turns that exact phase into a compact graybox silhouette so a
 * player can distinguish scouts, a forming force, a cordon, clearance, a
 * held line, recovery and withdrawal without a second local state machine.</p>
 */
final class SourceGrayboxFrontCampaignScenePlan {
    private SourceGrayboxFrontCampaignScenePlan() { }

    /** The exact {@code ReferenceFrontPhase} vocabulary; unknown and terminal phases fail closed. */
    static List<SourceGrayboxPresentationPlan.Desired> from(String revision, ReferenceGrayboxSnapshot.Activity activity, int sceneY) {
        String prefix = "activity:" + activity.id();
        int x = activity.position().x();
        int z = activity.position().z();
        List<SourceGrayboxPresentationPlan.Desired> result = new ArrayList<>();
        switch (activity.phase()) {
            case "recon" -> {
                block(result, revision, activity, prefix + ":scene:scout-north-west", "ACTIVITY_FRONT_RECON_SCOUT", x - 3, z - 3, sceneY, 1, 1, 2);
                block(result, revision, activity, prefix + ":scene:scout-south-east", "ACTIVITY_FRONT_RECON_SCOUT", x + 3, z + 3, sceneY, 1, 1, 2);
                beacon(result, revision, activity, prefix, x, z, sceneY, 2);
            }
            case "assemble" -> {
                for (int dx : List.of(-2, 2)) for (int dz : List.of(-2, 2)) {
                    block(result, revision, activity, prefix + ":scene:assembly:" + dx + ":" + dz,
                            "ACTIVITY_FRONT_ASSEMBLY", x + dx, z + dz, sceneY, 1, 1, 2);
                }
                beacon(result, revision, activity, prefix, x, z, sceneY, 3);
            }
            case "establish" -> {
                block(result, revision, activity, prefix + ":scene:establish-west", "ACTIVITY_FRONT_ESTABLISH", x - 2, z - 1, sceneY, 1, 3, 2);
                block(result, revision, activity, prefix + ":scene:establish-east", "ACTIVITY_FRONT_ESTABLISH", x + 2, z - 1, sceneY, 1, 3, 2);
                beacon(result, revision, activity, prefix, x, z, sceneY, 4);
            }
            case "cordon" -> perimeter(result, revision, activity, prefix, "ACTIVITY_FRONT_CORDON", x, z, sceneY, 2);
            case "clear" -> {
                block(result, revision, activity, prefix + ":scene:clear-west", "ACTIVITY_FRONT_CLEAR_LINE", x - 3, z - 1, sceneY, 1, 3, 2);
                block(result, revision, activity, prefix + ":scene:clear-east", "ACTIVITY_FRONT_CLEAR_LINE", x + 3, z - 1, sceneY, 1, 3, 2);
                beacon(result, revision, activity, prefix, x, z, sceneY, 5);
            }
            case "hold" -> {
                perimeter(result, revision, activity, prefix, "ACTIVITY_FRONT_HOLD", x, z, sceneY, 2);
                block(result, revision, activity, prefix + ":scene:hold-command", "ACTIVITY_FRONT_HOLD", x, z, sceneY, 1, 1, 3);
            }
            case "restore" -> {
                block(result, revision, activity, prefix + ":scene:restore-north", "ACTIVITY_FRONT_RESTORE", x, z - 2, sceneY, 1, 1, 2);
                block(result, revision, activity, prefix + ":scene:restore-south", "ACTIVITY_FRONT_RESTORE", x, z + 2, sceneY, 1, 1, 2);
                block(result, revision, activity, prefix + ":scene:restore-west", "ACTIVITY_FRONT_RESTORE", x - 2, z, sceneY, 1, 1, 2);
                block(result, revision, activity, prefix + ":scene:restore-east", "ACTIVITY_FRONT_RESTORE", x + 2, z, sceneY, 1, 1, 2);
                beacon(result, revision, activity, prefix, x, z, sceneY, 3);
            }
            case "withdraw" -> {
                block(result, revision, activity, prefix + ":scene:withdraw-north", "ACTIVITY_FRONT_WITHDRAW_COLUMN", x - 1, z - 3, sceneY, 2, 1, 2);
                block(result, revision, activity, prefix + ":scene:withdraw-south", "ACTIVITY_FRONT_WITHDRAW_COLUMN", x - 1, z + 3, sceneY, 2, 1, 2);
                beacon(result, revision, activity, prefix, x, z, sceneY, 2);
            }
            case "complete", "failed" -> throw new IllegalStateException("terminal front campaign was offered for materialization: " + activity.id());
            default -> throw new IllegalStateException("unknown source front campaign phase: " + activity.phase());
        }
        return List.copyOf(result);
    }

    private static void perimeter(List<SourceGrayboxPresentationPlan.Desired> result, String revision,
                                  ReferenceGrayboxSnapshot.Activity activity, String prefix, String kind, int x, int z, int sceneY, int height) {
        block(result, revision, activity, prefix + ":scene:perimeter-north", kind, x - 2, z - 2, sceneY, 5, 1, height);
        block(result, revision, activity, prefix + ":scene:perimeter-south", kind, x - 2, z + 2, sceneY, 5, 1, height);
        block(result, revision, activity, prefix + ":scene:perimeter-west", kind, x - 2, z - 1, sceneY, 1, 3, height);
        block(result, revision, activity, prefix + ":scene:perimeter-east", kind, x + 2, z - 1, sceneY, 1, 3, height);
    }

    private static void beacon(List<SourceGrayboxPresentationPlan.Desired> result, String revision,
                               ReferenceGrayboxSnapshot.Activity activity, String prefix, int x, int z, int sceneY, int height) {
        block(result, revision, activity, prefix + ":scene:beacon", "ACTIVITY_BEACON", x, z, sceneY, 1, 1, height);
    }

    private static void block(List<SourceGrayboxPresentationPlan.Desired> result, String revision,
                              ReferenceGrayboxSnapshot.Activity activity, String id, String kind, int x, int z,
                              int sceneY, int width, int depth, int height) {
        result.add(new SourceGrayboxPresentationPlan.Desired(id, activity.id(), kind, revision,
                x, sceneY, z, width, depth, height, activity.colour()));
    }
}
