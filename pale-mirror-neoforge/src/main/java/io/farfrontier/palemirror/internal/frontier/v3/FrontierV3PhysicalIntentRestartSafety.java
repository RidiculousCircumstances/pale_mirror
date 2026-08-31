package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import net.minecraft.server.level.ServerLevel;

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

    static int quarantineUninspectableRunningIntents(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return quarantineWithManagedPostcondition(runtime, intent -> false);
    }

    static int quarantineUninspectableRunningIntents(
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level
    ) {
        java.util.function.Predicate<PhysicalIntentId> hasManagedPostcondition = intent -> FrontierV3ManagedExplosionLedger.get(level).has(intent);
        List<PhysicalIntentId> running = state(runtime).physicalIntents().values().stream()
                .filter(intent -> intent.status() == PhysicalIntentStatus.RUNNING)
                .filter(intent -> !hasRestartInspector(level, intent, hasManagedPostcondition))
                .map(PhysicalIntent::id)
                .sorted(Comparator.naturalOrder())
                .toList();
        for (PhysicalIntentId intentId : running) quarantine(runtime, intentId);
        return running.size();
    }

    static int quarantineWithManagedPostcondition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                   java.util.function.Predicate<PhysicalIntentId> hasManagedPostcondition) {
        List<PhysicalIntentId> running = state(runtime).physicalIntents().values().stream()
                .filter(intent -> intent.status() == PhysicalIntentStatus.RUNNING)
                .filter(intent -> !hasLoadedPostconditionInspector(intent, hasManagedPostcondition))
                .map(PhysicalIntent::id)
                .sorted(Comparator.naturalOrder())
                .toList();
        for (PhysicalIntentId intentId : running) quarantine(runtime, intentId);
        return running.size();
    }

    /**
     * A retained RUNNING intent is safe only when a loaded-world executor has an exact,
     * non-replaying postcondition inspector for it.  In particular, a harvest checks all owned
     * crop cells and its one named depot stack before acknowledging the existing physical result.
     */
    static boolean hasLoadedPostconditionInspector(PhysicalIntent intent,
                                                    java.util.function.Predicate<PhysicalIntentId> hasManagedPostcondition) {
        return switch (intent.kind()) {
            case CARGO_HANDOFF, STRUCTURAL_REPAIR, ROUTE_CONSTRUCTION, DECONTAMINATION,
                    EXACT_ITEM_CONSUMPTION, RESOURCE_SITE_PREPARATION, RESOURCE_SITE_HARVEST,
                    PRODUCTION_TRANSFORMATION, CARGO_LOADING, ROUTE_CONSTRUCTION_MATERIAL_LOADING,
                    HIVE_NUTRIENT_DEPARTURE, HIVE_NUTRIENT_ARRIVAL -> true;
            case EXPLOSION -> hasManagedPostcondition.test(intent.id());
            default -> false;
        };
    }

    private static boolean hasRestartInspector(ServerLevel level, PhysicalIntent intent,
                                               java.util.function.Predicate<PhysicalIntentId> hasManagedPostcondition) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXPLOSION) {
            return hasLoadedPostconditionInspector(intent, hasManagedPostcondition);
        }
        if (hasManagedPostcondition.test(intent.id())) return true;
        // Never load a chunk merely to recover a physical effect.  An exact bomb in a naturally
        // loaded chunk is recoverable; an absent chunk must wait; a missing/altered loaded bomb
        // is unsafe and is therefore made visibly UNKNOWN.
        return FrontierV3BomberBomb.inspect(level, intent) != FrontierV3BomberBomb.Inspection.MISSING_OR_ALTERED;
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
        return runtime.decodedState()
                .orElseThrow(() -> new IllegalStateException("cannot inspect physical intents from inactive runtime"));
    }
}
