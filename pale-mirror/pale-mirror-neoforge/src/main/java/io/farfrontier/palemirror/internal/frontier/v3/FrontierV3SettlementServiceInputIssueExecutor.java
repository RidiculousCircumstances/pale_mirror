package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.ActorContainerItemOrder;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceInputIssueObservation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceInputIssueStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWork;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;
import java.util.Optional;

/**
 * Durable-before-effect hand-off from one declared depot service slot to the exact service
 * worker's visible main hand.  This is intentionally independent of defender equipment: the
 * service-work aggregate owns the station, route and purpose of this custody boundary.
 */
final class FrontierV3SettlementServiceInputIssueExecutor {
    private FrontierV3SettlementServiceInputIssueExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierV3PhysicalIntentScheduling.firstActionable(pendingIntents(state), intent -> readiness(level, state, intent))
                .ifPresent(intent -> execute(level, runtime, state, intent));
    }

    static List<PhysicalIntent> pendingIntents(FrontierWorldState state) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                        || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).toList();
    }

    static FrontierV3PhysicalIntentScheduling.Readiness readiness(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        FrontierV3PhysicalIntentScheduling.Readiness canonical = canonicalReadiness(state, intent);
        if (canonical != FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE) return canonical;
        Target target = target(state, intent);
        // The work state owns when this hand-off becomes eligible.  A HOT scene is an
        // execution location, not an additional admission authority: while it is still
        // preparing its retained worker, the exact intent must wait rather than poison
        // itself as a canonical conflict.
        if (target == null) return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        if (!level.hasChunkAt(target.chestPosition())) return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, target.chestPosition(), target.sourceSlot().containerId());
        if (chest == null) return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        return resident(level, state, target, intent.status() == PhysicalIntentStatus.PREPARED) == null ? FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED
                : FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE;
    }

    /**
     * Read-only explanation of the exact input-issue boundary for a scene diagnostic.  It is
     * deliberately owned here, beside the executor's real preconditions, so an operator never
     * has to infer a missing chest or worker from an unrelated terminal intent timeout.
     */
    static String diagnosticReadiness(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        FrontierV3PhysicalIntentScheduling.Readiness canonical = canonicalReadiness(state, intent);
        if (canonical != FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE) return "CANONICAL_" + canonical;
        Target target = target(state, intent);
        if (target == null) return "TARGET_DEFERRED";
        if (!level.hasChunkAt(target.chestPosition())) return "CHEST_UNLOADED";
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, target.chestPosition(), target.sourceSlot().containerId());
        if (chest == null) return "CHEST_NOT_ACTIVE";
        return resident(level, state, target, intent.status() == PhysicalIntentStatus.PREPARED) == null ? "WORKER_NOT_AT_INPUT" : "RUNNABLE";
    }

    /**
     * Separates a malformed retained request from an ordinary earlier service phase.
     *
     * <p>The input intent is durably prepared together with its work so that no later
     * actor can claim the reagent.  It therefore normally exists while the worker is
     * still approaching the source station.  Treating that durable reservation as an
     * invalid executable effect made physical executor ordering part of canonical
     * correctness and could repeatedly transition {@code UNKNOWN_AFTER_RESTART}.</p>
     */
    static FrontierV3PhysicalIntentScheduling.Readiness canonicalReadiness(FrontierWorldState state, PhysicalIntent intent) {
        return switch (SettlementServiceInputIssueStateSupport.executionEligibility(state, intent)) {
            case INVALID -> FrontierV3PhysicalIntentScheduling.Readiness.INVALID;
            case DEFERRED -> FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
            case READY -> FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE;
        };
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) {
            // A prior physical conflict is intentionally retained for loaded-world
            // inspection.  Re-submitting UNKNOWN is not a legal state transition and
            // must never quarantine the whole simulation.
            if (intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) unknown(runtime, intent.id(), "canonical-conflict");
            return;
        }
        if (!level.hasChunkAt(target.chestPosition())) return;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, target.chestPosition(), target.sourceSlot().containerId());
        Villager worker = resident(level, state, target, intent.status() == PhysicalIntentStatus.PREPARED);
        if (chest == null || worker == null) return;
        if (intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) { inspectRecovered(level, runtime, intent, target, chest, worker); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target, chest, worker); return; }
        if (!sourceMatches(chest, target) || !worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            unknown(runtime, intent.id(), "precondition-conflict"); return;
        }
        var actuation = takeAuthority(level, runtime, intent, target, worker);
        if (actuation == null || !transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!actuation.current(worker)) return;
        if (!handOff(chest, worker, target)) { unknown(runtime, intent.id(), "effect-conflict"); return; }
        confirm(runtime, intent, target);
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        try {
            if (intent.status() == PhysicalIntentStatus.PREPARED) SettlementServiceInputIssueStateSupport.validateIntent(state, intent);
            else SettlementServiceInputIssueStateSupport.validateRetainedInput(state, intent);
        }
        catch (IllegalArgumentException invalid) { return null; }
        SubjectId workId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.SETTLEMENT_SERVICE_WORK);
        SettlementServiceWork work = state.serviceWorks().get(workId);
        ExactItemStack item = state.inventory().items().get(work.inputItemId());
        ContainerSurface surface = state.inventory().surfaces().get(work.inputSource().containerId());
        if (intent.status() == PhysicalIntentStatus.PREPARED && !hasHotScope(state, work)) return null;
        if (item == null || surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) return null;
        ActorContainerItemOrder order = new ActorContainerItemOrder(work.id(), work.workerId(), ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Exact(item), new ActorContainerItemOrder.ContainerEndpoint.ExactSlot(work.inputSource()), work.inputStation(),
                ActorContainerItemOrder.Hand.MAIN, 0L, Math.addExact(work.inputTraversal().revision(), 1L));
        return new Target(work, item, work.inputSource(), new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()), order);
    }

    private static boolean hasHotScope(FrontierWorldState state, SettlementServiceWork work) {
        return state.sceneLeases().values().stream().filter(value -> value.status() == SceneLeaseStatus.HOT)
                .filter(FrontierSceneBehaviors::isServiceWork)
                .anyMatch(value -> FrontierSceneBehaviors.serviceWork(value).workId().equals(work.id()));
    }

    private static Villager resident(ServerLevel level, FrontierWorldState state, Target target, boolean requireStation) {
        var entity = level.getEntity(SceneLease.deterministicEntityId(state.bootstrap().worldId(), target.work().workerId()));
        if (!(entity instanceof Villager worker)) return null;
        if (requireStation) return worker.isAlive()
                && FrontierV3ActorBodyController.recognizesRecordedBody(level, state, worker)
                && FrontierV3SemanticMovement.arrived(level, worker, target.work().inputStation()) ? worker : null;
        return FrontierV3ActorBodyController.recognizesRecordedBody(level, state, worker)
                || FrontierV3ActorBodyController.recognizesRetiredDeadBody(level, state, worker) ? worker : null;
    }

    /** New mutation uses current UAE authority; observing a possibly applied effect does not. */
    private static FrontierV3ActorActuation takeAuthority(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target, Villager worker) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !target.work().equals(state.serviceWorks().get(target.work().id()))
                || !hasHotScope(state, target.work())
                || !FrontierV3SemanticMovement.arrived(level, worker, target.work().inputStation())) return null;
        try {
            if (intent.status() == PhysicalIntentStatus.PREPARED) SettlementServiceInputIssueStateSupport.validateIntent(state, intent);
            else SettlementServiceInputIssueStateSupport.validateUnappliedRetry(state, intent);
        } catch (IllegalArgumentException noActiveTakeAuthority) { return null; }
        var actuation = FrontierV3ActorActuation.capture(state, worker,
                io.farfrontier.palemirror.frontier.v3.model.SettlementServiceExecutionAuthority.current(state, target.work()),
                () -> runtime.decodedState().filter(now -> target.work().equals(now.serviceWorks().get(target.work().id()))));
        return FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker) && actuation.current(worker)
                ? actuation : null;
    }

    private static boolean sourceMatches(ChestBlockEntity chest, Target target) {
        return target.sourceSlot().slot() < chest.getContainerSize()
                && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.sourceSlot().slot()), target.item());
    }

    private static boolean handOff(ChestBlockEntity chest, Villager worker, Target target) {
        return FrontierV3ActorItemTransfer.take(chest, worker, target.item(), target.order().exactSlot(), EquipmentSlot.MAINHAND);
    }

    static boolean handOff(ChestBlockEntity chest, Villager worker, ExactItemStack item, InventoryCustody.ContainerSlot sourceSlot) {
        return FrontierV3ActorItemTransfer.take(chest, worker, item, sourceSlot, EquipmentSlot.MAINHAND);
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target,
                                       ChestBlockEntity chest, Villager worker) {
        boolean source = sourceMatches(chest, target), hand = FrontierV3CargoHandoffExecutor.exactMatch(worker.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
        if (!source && hand) { confirm(runtime, intent, target); return; }
        if (source && worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            var permission = takeAuthority(level, runtime, intent, target, worker);
            if (permission == null) return; // A lawful off-station body is not a resource conflict.
            if (permission.current(worker) && handOff(chest, worker, target)) { confirm(runtime, intent, target); return; }
        }
        unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void inspectRecovered(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target,
                                         ChestBlockEntity chest, Villager worker) {
        boolean source = sourceMatches(chest, target), hand = FrontierV3CargoHandoffExecutor.exactMatch(worker.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
        if (!source && hand) confirm(runtime, intent, target);
        else if (source && worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            var permission = takeAuthority(level, runtime, intent, target, worker);
            if (permission != null && permission.current(worker) && handOff(chest, worker, target)) confirm(runtime, intent, target);
        }
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        SettlementServiceInputIssueObservation receipt = new SettlementServiceInputIssueObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), target.work().id(),
                target.work().workerId(), target.item().id(), target.sourceSlot());
        if (!(transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt), "confirmed") instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("service input issue confirmation was rejected");
        }
    }

    /** Capture before loot; settlement never retries a take or requires a surviving worker/scene. */
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepareDeathObservation(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
            SettlementServiceWork work, net.minecraft.world.entity.Mob body) {
        var intent = state.physicalIntents().get(work.inputIssueIntentId());
        if (intent.status() == PhysicalIntentStatus.PREPARED) return () -> {
            // Durable PREPARED never authorized mutation. No corpse/chest absence is needed
            // to retire an unbegun reservation after its exact activity has died.
            if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFLICTED, Optional.empty(), "death-unbegun"))
                throw new IllegalStateException("unbegun service input reservation could not be retired");
        };
        if (intent.status() != PhysicalIntentStatus.RUNNING && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
            return () -> { };
        var target = target(state, intent);
        if (target == null || !level.hasChunkAt(target.chestPosition())) return () -> { };
        var chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, target.chestPosition(), target.sourceSlot().containerId());
        if (chest == null) return () -> { };
        boolean source = sourceMatches(chest, target);
        boolean hand = FrontierV3CargoHandoffExecutor.exactMatch(body.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
        if (!source && hand) return () -> confirm(runtime, intent, target);
        if (source && body.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return () -> {
            if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFLICTED, Optional.empty(), "death-unapplied"))
                throw new IllegalStateException("positively unperformed service take could not be abandoned");
        };
        return () -> { }; // Contradictory source/hand evidence retains its explicit UNKNOWN obligation.
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        return transitionResult(runtime, id, status, observation, phase) instanceof CommandResult.Accepted;
    }

    private static CommandResult transitionResult(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id,
                                                   PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:service-input-issue-" + phase + "-r" + checkpoint.revision().value());
        return runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }

    record Target(SettlementServiceWork work, ExactItemStack item, InventoryCustody.ContainerSlot sourceSlot, BlockPos chestPosition,
                  ActorContainerItemOrder order) { }
}
