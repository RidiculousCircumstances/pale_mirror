package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;
import io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyBlockage;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;

/** Read-only operator view of one exact cocoon-release gate. */
final class FrontierV3HiveMobilizationDiagnostic {
    private FrontierV3HiveMobilizationDiagnostic() { }

    static String render(CheckpointImage checkpoint, FrontierWorldState state, ServerLevel level, String id) {
        try {
            HiveMobilization mobilization = state.hiveColony().mobilizations().get(new SubjectId(id));
            if (mobilization != null && (mobilization.status() == HiveMobilizationStatus.ASSEMBLING
                    || mobilization.status() == HiveMobilizationStatus.CONFLICT && mobilization.assembly().isPresent())) {
                return assembly(checkpoint, state, level, mobilization);
            }
        } catch (IllegalArgumentException invalid) {
            return FrontierV3DiagnosticJson.bounded("hive_mobilization", id, checkpoint,
                    base(id, checkpoint) + ",\"status\":\"not_found\"}");
        }
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

    /**
     * Read-only assembly progress for one retained topology.  The diagnostic identifies the
     * same next cursor as the executor and reports only already-loaded target readiness; it
     * cannot seek a body, load a chunk, alter a lease or advance an actor.
     */
    private static String assembly(CheckpointImage checkpoint, FrontierWorldState state, ServerLevel level,
                                   HiveMobilization mobilization) {
        HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
        HiveAssemblyBlockage blockage = mobilization.assemblyBlockage().orElse(null);
        SubjectId nextActor = blockage == null ? assembly.safeAdvances().stream().min(Comparator.naturalOrder()).orElse(null) : blockage.actorId();
        HiveTaskAssembly.Member member = nextActor == null ? null : assembly.members().get(nextActor);
        boolean complete = assembly.complete();
        boolean advanced = assembly.members().values().stream().anyMatch(value -> value.cursor() > 0);
        long completed = assembly.members().values().stream().filter(HiveTaskAssembly.Member::arrived).count();
        long hot = assembly.members().keySet().stream().map(state.ambientLeases()::get)
                .filter(lease -> lease != null && lease.status() == AmbientLeaseStatus.HOT
                        && lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY).count();
        if (member == null) {
            return FrontierV3DiagnosticJson.bounded("hive_mobilization", mobilization.id().value(), checkpoint,
                    base(mobilization.id().value(), checkpoint) + ",\"status\":\"ok\",\"mobilizationStatus\":\"" + mobilization.status() + "\""
                            + ",\"conflictReason\":\"" + mobilization.conflictReason().map(Enum::name).orElse("") + "\""
                            + ",\"assemblyComplete\":" + complete + ",\"assemblyAdvanced\":" + advanced
                            + ",\"completedMembers\":" + completed + ",\"hotMembers\":" + hot
                            + ",\"nextMember\":\"\",\"physicalReadiness\":{\"chunkLoaded\":false,\"ordinaryPlayerNearby\":false,\"headroom\":false,\"runnable\":false},\"detail\":\""
                            + (mobilization.status() == HiveMobilizationStatus.CONFLICT ? "durable_assembly_conflict" : "assembly_complete") + "\"}");
        }
        SurfaceAnchor current = member.currentSurface();
        SurfaceAnchor target = blockage == null ? member.nextSurface() : blockage.target();
        BlockPos targetBlock = new BlockPos(target.support().x(), target.support().y(), target.support().z());
        FrontierV3PhysicalDemand.Readiness demand = FrontierV3PhysicalDemand.readiness(level, targetBlock);
        boolean standingColumn = demand.chunkLoaded() && FrontierV3StandingPosition.hasExactStandingColumn(level, target.support());
        boolean runnable = demand.runnable() && standingColumn;
        String detail = mobilization.status() == HiveMobilizationStatus.CONFLICT ? "durable_assembly_conflict"
                : !demand.chunkLoaded() ? "awaiting_natural_chunk" : !standingColumn ? "loaded_target_blocked"
                : !demand.ordinaryPlayerNearby() ? "awaiting_player_demand" : "awaiting_observed_arrival";
        return FrontierV3DiagnosticJson.bounded("hive_mobilization", mobilization.id().value(), checkpoint,
                base(mobilization.id().value(), checkpoint) + ",\"status\":\"ok\",\"mobilizationStatus\":\"" + mobilization.status() + "\""
                        + ",\"conflictReason\":\"" + mobilization.conflictReason().map(Enum::name).orElse("") + "\""
                        + ",\"assemblyComplete\":" + complete + ",\"assemblyAdvanced\":" + advanced
                        + ",\"completedMembers\":" + completed + ",\"hotMembers\":" + hot
                        + ",\"nextMember\":\"" + quote(nextActor.value()) + "\",\"cursor\":" + member.cursor()
                        + ",\"blockedCursor\":" + (blockage == null ? -1 : blockage.expectedCursor())
                        + ",\"current\":{\"x\":" + current.support().x() + ",\"y\":" + current.support().y() + ",\"z\":" + current.support().z()
                        + "},\"target\":{\"x\":" + target.support().x() + ",\"y\":" + target.support().y() + ",\"z\":" + target.support().z()
                        + "},\"physicalReadiness\":{\"chunkLoaded\":" + demand.chunkLoaded() + ",\"ordinaryPlayerNearby\":" + demand.ordinaryPlayerNearby()
                        + ",\"standingColumn\":" + standingColumn + ",\"runnable\":" + runnable + "},\"detail\":\"" + detail + "\"}");
    }

    private static String base(String id, CheckpointImage checkpoint) {
        return "{\"schema\":1,\"kind\":\"hive_mobilization\",\"id\":\"" + quote(id)
                + "\",\"world\":\"" + quote(checkpoint.worldId().value()) + "\",\"revision\":"
                + checkpoint.revision().value() + ",\"instant\":" + checkpoint.instant().ticks();
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
