package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;

/** Canonical command admission shared by physical executors and restart recovery. */
final class FrontierV3CommandSubmission {
    private FrontierV3CommandSubmission() { }
    static void submit(FrontierV3ServerRuntime<?, ?> runtime, String phase, String id, FrontierPayload payload) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = new CommandId("executor:" + phase + "-" + id.replace(':', '-') + "-r" + checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("Frontier v3 physical transition was rejected: " + result);
    }
}
