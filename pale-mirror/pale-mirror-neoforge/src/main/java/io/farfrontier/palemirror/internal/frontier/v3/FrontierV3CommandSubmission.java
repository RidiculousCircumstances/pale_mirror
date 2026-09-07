package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding;

/** Canonical command admission shared by physical executors and restart recovery. */
final class FrontierV3CommandSubmission {
    private FrontierV3CommandSubmission() { }
    static CommandResult submit(FrontierV3ServerRuntime<?, ?> runtime, String phase, String id, FrontierPayload payload) {
        return submit(runtime, phase, id, payload, Optional.empty());
    }

    static CommandResult submitBound(FrontierV3ServerRuntime<?, ?> runtime, String phase, String id, FrontierPayload payload,
                                     ScheduledAction binding) {
        return submit(runtime, phase, id, payload, Optional.of(binding));
    }

    private static CommandResult submit(FrontierV3ServerRuntime<?, ?> runtime, String phase, String id, FrontierPayload payload,
                                        Optional<ScheduledAction> binding) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = FrontierV3CommandIds.physical(phase, checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(binding.isPresent() ? FrontierCommand.SCHEMA_VERSION : FrontierCommand.LEGACY_SCHEMA_VERSION,
                commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload,
                binding.map(action -> new EngineScheduleBinding(checkpoint.revision(), action))))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("Frontier v3 physical transition was rejected: " + result);
        return result;
    }
}
