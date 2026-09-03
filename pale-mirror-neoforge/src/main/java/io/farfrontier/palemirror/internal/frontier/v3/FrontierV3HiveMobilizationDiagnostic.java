package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;

/** Read-only operator view of one exact cocoon-release gate. */
final class FrontierV3HiveMobilizationDiagnostic {
    private FrontierV3HiveMobilizationDiagnostic() { }

    static String render(CheckpointImage checkpoint, FrontierWorldState state, ServerLevel level, String id) {
        FrontierV3HiveMobilizationExecutor.Readiness value;
        try {
            value = FrontierV3HiveMobilizationExecutor.readiness(level, state, new SubjectId(id));
        } catch (IllegalArgumentException invalid) {
            return FrontierV3DiagnosticJson.bounded("hive_mobilization", id, checkpoint,
                    base(id, checkpoint) + ",\"status\":\"not_found\"}");
        }
        return FrontierV3DiagnosticJson.bounded("hive_mobilization", id, checkpoint,
                base(id, checkpoint) + ",\"status\":\"" + value.status() + "\",\"mobilizationStatus\":\""
                        + value.mobilizationStatus() + "\",\"nextMember\":\"" + value.nextMember()
                        + "\",\"cocoon\":{\"x\":" + value.x() + ",\"y\":" + value.y() + ",\"z\":" + value.z()
                        + "},\"physicalReadiness\":{\"chunkLoaded\":" + value.chunkLoaded()
                        + ",\"ordinaryPlayerNearby\":" + value.ordinaryPlayerNearby() + ",\"runnable\":" + value.runnable()
                        + "},\"detail\":\"" + value.detail() + "\"}");
    }

    private static String base(String id, CheckpointImage checkpoint) {
        return "{\"schema\":1,\"kind\":\"hive_mobilization\",\"id\":\"" + quote(id)
                + "\",\"world\":\"" + quote(checkpoint.worldId().value()) + "\",\"revision\":"
                + checkpoint.revision().value() + ",\"instant\":" + checkpoint.instant().ticks();
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
