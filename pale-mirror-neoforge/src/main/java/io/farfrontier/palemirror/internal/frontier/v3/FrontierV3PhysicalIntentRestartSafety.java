package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Fail-closed restart boundary for non-replayable Frontier effects.
 *
 * <p>Each unrecognised effect kind has no supported real-world postcondition inspector. It must
 * be made visibly unknown rather than replayed or assumed successful. Supported kinds are left
 * for their loaded-chunk executor to inspect; that executor either submits immutable evidence or
 * records the same visible unknown outcome.</p>
 */
final class FrontierV3PhysicalIntentRestartSafety {
    private FrontierV3PhysicalIntentRestartSafety() { }

    static int quarantineUninspectableRunningIntents(
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime
    ) {
        List<PhysicalIntentId> running = state(runtime).physicalIntents().values().stream()
                .filter(intent -> intent.status() == PhysicalIntentStatus.RUNNING)
                .filter(intent -> intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_HANDOFF
                        && intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.STRUCTURAL_REPAIR)
                .map(PhysicalIntent::id)
                .sorted(Comparator.naturalOrder())
                .toList();
        for (PhysicalIntentId intentId : running) quarantine(runtime, intentId);
        return running.size();
    }

    private static void quarantine(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId) {
        CheckpointImage checkpoint = runtime.checkpointImage()
                .orElseThrow(() -> new IllegalStateException("cannot recover physical intent from inactive runtime"));
        CommandId commandId = new CommandId("recovery:unknown-" + intentId.value().replace(':', '-'));
        FrontierCommand command = new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new PhysicalIntentTransition(intentId, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
        CommandResult result = runtime.submit(command)
                .orElseThrow(() -> new IllegalStateException("physical intent recovery could not submit a command"));
        if (!(result instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("physical intent recovery transition was rejected: " + result);
        }
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        CheckpointImage checkpoint = runtime.checkpointImage()
                .orElseThrow(() -> new IllegalStateException("cannot inspect physical intents from inactive runtime"));
        return new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
    }
}
