package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ProductionTransformationObservation;
import io.farfrontier.palemirror.frontier.v3.model.ProductionTransformationStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** Durable loaded-chunk transformation of one owned wheat stack into its exact bread output. */
final class FrontierV3ProductionTransformationExecutor {
    private static final Map<FrontierV3ServerRuntime<?, ?>, FrontierV3FairTurn<PhysicalIntentId>> TURNS = new IdentityHashMap<>();
    private FrontierV3ProductionTransformationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        var candidates = state.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                        || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                .toList();
        TURNS.computeIfAbsent(runtime, ignored -> new FrontierV3FairTurn<>())
                .next(candidates, PhysicalIntent::id).ifPresent(intent -> execute(level, runtime, state, intent));
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { TURNS.remove(runtime); }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        if (intent.roles().schema() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema.PRODUCTION_RESOURCES) {
            FrontierV3FungibleProductionEffect.execute(level, runtime, state, intent); return;
        }
        ProductionTransformationStateSupport.Target target;
        try { target = ProductionTransformationStateSupport.target(state, intent); }
        catch (IllegalArgumentException conflict) { unknown(runtime, intent.id(), "canonical-precondition-conflict"); return; }
        BlockPos position = new BlockPos(target.chestPosition().x(), target.chestPosition().y(), target.chestPosition().z());
        if (!level.hasChunkAt(position)) return;
        if (ReferenceContainerCustody.isReferenceContainer(state, target.slot().containerId())
                && !ReferenceContainerCustody.hasOperationalCustody(state, target.slot().containerId())) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(position, target.slot().containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        executeEffect(intent.status(), new EffectTurn() {
            @Override public boolean inputPresent() { return matchesInput(chest, target); }
            @Override public boolean outputPresent() { return matchesOutput(chest, target); }
            @Override public boolean begin() { return transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running"); }
            @Override public boolean replaceInput() { return replace(chest, target); }
            @Override public void confirm() { FrontierV3ProductionTransformationExecutor.confirm(runtime, intent, target, chest); }
            @Override public void unknown(String reason) { FrontierV3ProductionTransformationExecutor.unknown(runtime, intent.id(), reason); }
        });
    }

    /**
     * The actual loaded-slot protocol, separated from Minecraft access, not a second executor.
     * UNKNOWN may inspect an exact output but must never begin or repeat an ambiguous write.
     */
    static void executeEffect(PhysicalIntentStatus status, EffectTurn turn) {
        switch (status) {
            case UNKNOWN_AFTER_RESTART -> {
                if (turn.outputPresent()) turn.confirm();
            }
            case RUNNING -> {
                if (turn.outputPresent()) { turn.confirm(); return; }
                if (turn.inputPresent() && turn.replaceInput()) { turn.confirm(); return; }
                turn.unknown("restart-postcondition-conflict");
            }
            case PREPARED -> {
                if (!turn.inputPresent()) { turn.unknown("input-precondition-conflict"); return; }
                if (!turn.begin()) return;
                if (!turn.replaceInput()) { turn.unknown("physical-write-conflict"); return; }
                turn.confirm();
            }
            default -> throw new IllegalArgumentException("terminal transformation cannot own an execution turn: " + status);
        }
    }

    interface EffectTurn {
        boolean inputPresent();
        boolean outputPresent();
        boolean begin();
        boolean replaceInput();
        void confirm();
        void unknown(String reason);
    }

    static boolean matchesInput(ChestBlockEntity chest, ProductionTransformationStateSupport.Target target) {
        return FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.slot().slot()), target.input());
    }
    static boolean matchesOutput(ChestBlockEntity chest, ProductionTransformationStateSupport.Target target) {
        return FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.slot().slot()), target.output());
    }
    static boolean replace(ChestBlockEntity chest, ProductionTransformationStateSupport.Target target) {
        if (!matchesInput(chest, target)) return false;
        ItemStack output = FrontierV3CargoHandoffExecutor.materializedStack(target.output());
        chest.setItem(target.slot().slot(), output); chest.setChanged(); return matchesOutput(chest, target);
    }
    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                ProductionTransformationStateSupport.Target target, ChestBlockEntity chest) {
        ProductionTransformationObservation observation = new ProductionTransformationObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), target.input().id(), target.output().id(),
                target.input().count(), target.output().count());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) {
            throw new IllegalStateException("production transformation confirmation was rejected");
        }
        if (ReferenceContainerCustody.isReferenceContainer(runtime.decodedState().orElseThrow(), target.slot().containerId())
                && !FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(runtime, target.slot().containerId(), chest)) {
            throw new IllegalStateException("confirmed reference production output did not establish its next replica boundary");
        }
    }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }
    static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:production-transform-" + phase + "-" + id.value().replace(':', '-'));
        PhysicalIntentTransition payload = new PhysicalIntentTransition(id, status, observation);
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            PhysicalIntent current = runtime.decodedState().orElseThrow().physicalIntents().get(id);
            payload = payload.withRecoveryDiagnostic(io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRecoveryDiagnosticProducer.PRODUCTION_WORK.stamp(current));
        }
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), payload))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (result instanceof CommandResult.Accepted) return true;
        CommandResult.Rejected rejected = (CommandResult.Rejected) result;
        if (status == PhysicalIntentStatus.CONFIRMED) {
            // The Minecraft write is already observed at this point.  Do not reduce the
            // consequence to an opaque boolean: quarantine must retain the canonical reason
            // which made its exact durable receipt unacceptable.
            throw new IllegalStateException("production transformation confirmation rejected: " + rejected.rejection().detail());
        }
        return false;
    }
}
