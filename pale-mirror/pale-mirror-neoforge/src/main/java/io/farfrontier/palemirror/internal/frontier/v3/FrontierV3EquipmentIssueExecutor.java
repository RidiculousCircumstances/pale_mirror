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
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentIssueObservation;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentIssueStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.Container;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentServiceBoundary;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentPhysicalAuthority;

import java.util.List;
import java.util.Optional;

/**
 * One loaded-chunk, durable-before-effect declared-storage-to-Villager equipment hand-off.
 *
 * <p>The canonical item remains in its source slot until an exact tagged stack is observed in
 * the exact deterministic resident body hand. A RUNNING request may resume only from a wholly
 * unchanged source; every mixed physical result is retained as UNKNOWN rather than repaired.</p>
 */
final class FrontierV3EquipmentIssueExecutor {
    private FrontierV3EquipmentIssueExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        // A naturally unloaded earlier depot or resident is a deferral.  It
        // must not prevent a later loaded exact hand-off from making progress.
        // The canonical ledger has an explicit 4,096-intent retention bound;
        // this family scan is therefore deterministic and bounded.
        FrontierV3PhysicalIntentScheduling.firstActionable(pendingIntents(state), intent -> readiness(level, state, intent))
                .ifPresent(intent -> execute(level, runtime, state, intent));
    }

    static List<PhysicalIntent> pendingIntents(FrontierWorldState state) {
        return state.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                        || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                .toList();
    }

    static FrontierV3PhysicalIntentScheduling.Readiness readiness(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        return readinessDetail(level, state, intent).selection();
    }

    /** Read-only explanation of the exact same physical admission predicate used by {@link #readiness}. */
    static Readiness readinessDetail(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) return new Readiness(FrontierV3PhysicalIntentScheduling.Readiness.INVALID, "CANONICAL_TARGET_INVALID");
        if (!level.hasChunkAt(target.chestPosition())) return new Readiness(FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED, "STORAGE_CHUNK_UNLOADED");
        Container chest = FrontierV3PhysicalContainer.loaded(level, state, target.sourceSlot().containerId())
                .map(FrontierV3PhysicalContainer::inventory).orElse(null);
        if (chest == null) return new Readiness(FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED, "OWNED_STORAGE_UNAVAILABLE");
        Villager resident = resident(level, state, target.residentId());
        if (resident == null) return new Readiness(FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED, "EXACT_HOT_RESIDENT_UNAVAILABLE");
        if (intent.status() == PhysicalIntentStatus.PREPARED && !FrontierV3EquipmentServicePort.ready(state, intent, resident))
            return new Readiness(FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED, "RESIDENT_NOT_AT_DECLARED_STORAGE_PORT");
        if (intent.status() != PhysicalIntentStatus.PREPARED && sourceMatches(chest, target)
                && resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                && !FrontierV3EquipmentServicePort.readyForInspection(state, intent, resident))
            return new Readiness(FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED, "INSPECTION_RETRY_PORT_UNAVAILABLE");
        if (intent.status() == PhysicalIntentStatus.PREPARED
                && FrontierV3ContainerEffectFence.pending(level, state, EquipmentPhysicalAuthority.container(intent)))
            return new Readiness(FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED, "CONTAINER_PHYSICAL_EFFECT_UNSETTLED");
        return new Readiness(FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE, "READY");
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) { unknown(runtime, intent.id(), "canonical-conflict"); return; }
        if (!level.hasChunkAt(target.chestPosition())) return;
        Container chest = FrontierV3PhysicalContainer.loaded(level, state, target.sourceSlot().containerId())
                .map(FrontierV3PhysicalContainer::inventory).orElse(null);
        Villager resident = resident(level, state, target.residentId());
        if (chest == null || resident == null
                || intent.status() == PhysicalIntentStatus.PREPARED && !FrontierV3EquipmentServicePort.ready(state, intent, resident)) return;
        if (intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) { inspectRecovered(level, runtime, intent, target, chest, resident); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target, chest, resident); return; }
        if (!sourceMatches(chest, target) || !resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            unknown(runtime, intent.id(), "precondition-conflict"); return;
        }
        if (FrontierV3ContainerEffectFence.pending(level, state, EquipmentPhysicalAuthority.container(intent))) return;
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!handOff(chest, resident, target)) { unknown(runtime, intent.id(), "effect-conflict"); return; }
        confirm(level, runtime, intent, target);
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        final io.farfrontier.palemirror.frontier.v3.model.EquipmentServiceCapabilities.Capability declaration;
        try { declaration = EquipmentServiceBoundary.capability(intent); }
        catch (IllegalArgumentException invalid) { return null; }
        SubjectId ownerId = declaration.ownerId(intent);
        SubjectId residentId = declaration.actorId(intent);
        SubjectId itemId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT);
        ExactItemStack item = state.inventory().items().get(itemId);
        try {
            if (intent.status() == PhysicalIntentStatus.PREPARED) EquipmentIssueStateSupport.validateIntent(state, intent);
            else EquipmentServiceBoundary.validateInspection(state, intent);
        }
        catch (IllegalArgumentException invalid) { return null; }
        if (item == null || !(item.custody() instanceof InventoryCustody.ContainerSlot source)) return null;
        ContainerSurface surface = state.inventory().surfaces().get(source.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) return null;
        return new Target(ownerId, residentId, item, source, new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static Villager resident(ServerLevel level, FrontierWorldState state, SubjectId residentId) {
        if (state.ambientLeases().get(residentId) == null || state.ambientLeases().get(residentId).status() != AmbientLeaseStatus.HOT) return null;
        var body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, residentId));
        return body instanceof Villager villager && FrontierV3AmbientActorExecutor.owned(villager, residentId,
                io.farfrontier.palemirror.frontier.v3.model.ActorKind.RESIDENT) ? villager : null;
    }

    private static boolean sourceMatches(Container chest, Target target) {
        return target.sourceSlot().slot() >= 0 && target.sourceSlot().slot() < chest.getContainerSize()
                && FrontierV3ExactItemPresentation.exactMatch(chest.getItem(target.sourceSlot().slot()), target.item());
    }

    static boolean handOff(Container source, Villager resident, Target target) {
        return FrontierV3ActorItemTransfer.take(source, resident, target.item(), target.sourceSlot(), EquipmentSlot.MAINHAND);
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target,
                                       Container chest, Villager resident) {
        boolean source = sourceMatches(chest, target), hand = FrontierV3ExactItemPresentation.exactMatch(resident.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
        if (!source && hand) { confirm(level, runtime, intent, target); return; }
        if (source && resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            if (!FrontierV3EquipmentServicePort.readyForInspection(runtime.decodedState().orElseThrow(), intent, resident)) return;
            if (handOff(chest, resident, target)) { confirm(level, runtime, intent, target); return; }
        }
        unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    /** Same exact whole-state recovery rule as the paired engineering return executor. */
    private static void inspectRecovered(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target,
                                         Container chest, Villager resident) {
        boolean source = sourceMatches(chest, target), hand = FrontierV3ExactItemPresentation.exactMatch(resident.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
        if (!source && hand) { confirm(level, runtime, intent, target); return; }
        if (source && resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                && FrontierV3EquipmentServicePort.readyForInspection(runtime.decodedState().orElseThrow(), intent, resident)
                && handOff(chest, resident, target)) confirm(level, runtime, intent, target);
    }

    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        EquipmentIssueObservation observation = new EquipmentIssueObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.ownerId(), target.residentId(), target.item().id(), target.sourceSlot());
        CommandResult result = transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed");
        if (result instanceof CommandResult.Rejected) {
            throw new IllegalStateException("equipment issue confirmation was rejected");
        }
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.humanEquipmentCorrelation(target.ownerId(), target.item().id()),
                "human_equipment_issued", target.ownerId(), result);
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        return transitionResult(runtime, id, status, observation, phase) instanceof CommandResult.Accepted;
    }

    private static CommandResult transitionResult(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                                   Optional<PhysicalEffectObservation> observation, String phase) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = commandId(phase, checkpoint.revision());
        return runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }

    /** Revision makes each accepted executor command unique without embedding an unbounded semantic ID. */
    static CommandId commandId(String phase, io.farfrontier.palemirror.frontier.v3.api.Revision revision) {
        return new CommandId("executor:equipment-issue-" + phase + "-r" + revision.value());
    }

    record Target(SubjectId ownerId, SubjectId residentId, ExactItemStack item, InventoryCustody.ContainerSlot sourceSlot,
                  BlockPos chestPosition) { }

    /** Bounded physical readiness for one exact equipment issue; diagnostics never invent a second predicate. */
    record Readiness(FrontierV3PhysicalIntentScheduling.Readiness selection, String detail) {
        Readiness {
            java.util.Objects.requireNonNull(selection, "equipment issue readiness selection");
            if (detail == null || detail.isBlank()) throw new IllegalArgumentException("equipment issue readiness detail is required");
        }
    }
}
