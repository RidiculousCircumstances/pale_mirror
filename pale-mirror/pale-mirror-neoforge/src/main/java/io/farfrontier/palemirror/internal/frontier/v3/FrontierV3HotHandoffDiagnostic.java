package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;
import java.util.stream.Collectors;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3DiagnosticJson.quote;

/** Opt-in explanation of the same read-only readiness proof used by admission and presentation. */
final class FrontierV3HotHandoffDiagnostic {
    private FrontierV3HotHandoffDiagnostic() { }
    static String render(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                         String id, CheckpointImage checkpoint) {
        var visibility = FrontierV3GrayboxExecutor.firstVisibility(runtime, id);
        String base = FrontierV3DiagnosticJson.firstVisibility(id, checkpoint, visibility);
        if (visibility.chunk() == null) return base;
        var review = FrontierV3HotHandoff.inspect(level, runtime, visibility.chunk());
        String checks = review.checks().stream().map(check -> "{\"owner\":\"" + quote(check.owner().value())
                + "\",\"status\":\"" + check.status().name() + "\",\"reason\":\"" + quote(check.reason()) + "\"}")
                .collect(Collectors.joining(","));
        return base.substring(0, base.length() - 1) + ",\"handoffRevision\":" + review.revision()
                + ",\"dynamicReady\":" + review.ready() + ",\"dynamicPresentable\":" + review.presentable()
                + ",\"handoffChecks\":[" + checks + "]}";
    }
}
