package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3SceneExecutor.*;

/** Atomic physical release of an owned HOT scene back into canonical custody. */
final class FrontierV3SceneReleaseExecutor {
    private FrontierV3SceneReleaseExecutor() { }

    static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease selectedLease) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        SceneLease lease = state.sceneLeases().get(selectedLease.id());
        if (lease == null || lease.status() != SceneLeaseStatus.DRAINING) return;
        if (!FrontierV3SceneBehaviorRegistry.releaseReady(level, state, lease)) return;
        release(level, runtime, lease, FrontierV3SceneBehaviorRegistry.releaseBinding(
                runtime.executionView().orElseThrow(), state, lease));
    }
    /** Generic lifecycle release; an enforced descriptor may supply one exact engine action binding. */
    static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease selectedLease,
                        java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        SceneLease lease = state.sceneLeases().get(selectedLease.id());
        if (lease == null || lease.status() != SceneLeaseStatus.DRAINING) return;
        if (!FrontierV3SceneBehaviorRegistry.releaseReady(level, state, lease)) return;
        var unfinishedStrike = state.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                        && (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART))
                .filter(intent -> io.farfrontier.palemirror.frontier.v3.model.SceneStrikeStateSupport.boundTo(lease, intent))
                .min(Comparator.comparing(PhysicalIntent::id));
        if (unfinishedStrike.isPresent()) {
            executeStrike(level, runtime, state, lease, unfinishedStrike.orElseThrow().lifecycleOwner());
            return; // Re-read the resulting authority next turn before releasing any body.
        }
        if (FrontierV3SceneReleaseReadiness.awaitingEntityStorage(level, state, lease)) return;
        List<SceneMemberPosition> positions = new ArrayList<>();
        List<SceneMember> departedMembers = new ArrayList<>();
        var releasePolicy = FrontierV3SceneBehaviorRegistry.releaseFailurePolicy(lease.cause().kind());
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (entity == null) {
                var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
                var observed = FrontierV3SceneDepartureObserver.observedDeparture(state, lease, member, ledger);
                if (observed.isPresent() && !ledger.savedDeparture(observed.orElseThrow())
                        && !ledger.hasDepartureConflict(member.actorId())) return;
                var receipt = FrontierV3SceneDepartureObserver.validDeparture(state, lease, member, ledger);
                if (receipt.isEmpty()) { conflict(level, runtime, state, lease, "release-departure-unproven"); return; }
                if (!FrontierV3ActorBodyController.checkpointSavedDeparture(level, runtime, member.actorId())) return;
                state = runtime.decodedState().orElseThrow();
                positions.add(receipt.orElseThrow().observed());
                departedMembers.add(member);
                continue;
            }
            if (FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).hasBodyDeparture(member.actorId())) {
                conflict(level, runtime, state, lease, "release-concurrent-departure-evidence"); return;
            }
            if (!owned(entity, state, lease, member)) {
                conflict(level, runtime, state, lease, "release-body-unavailable"); return;
            }
            if (!(entity instanceof Mob body) || body.getHealth() <= 0.0F) {
                conflict(level, runtime, state, lease, "release-body-dead"); return;
            }
            if (!FrontierV3SceneBehaviorRegistry.releaseEffects(lease).matchesBody(state, lease, member, body)) {
                conflict(level, runtime, state, lease, "release-family-hand-mismatch"); return;
            }
            if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, body)) return;
            state = runtime.decodedState().orElseThrow();
            FrontierV3ActorCarryProjection.requireMatches(state, member.actorId(), body);
            var currentActor = state.actorLocations().get(member.actorId());
            positions.add(new SceneMemberPosition(member.actorId(), currentActor.body(), currentActor.condition().health()));
            // A process releases its participant, not that participant's body.
            // Natural unload and the body controller alone settle physical absence.
        }
        var effects = FrontierV3SceneBehaviorRegistry.releaseEffects(lease).prepare(level, state, lease,
                new SceneLeaseReleased(lease.id(), positions),
                departedMembers.stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        if (effects instanceof FrontierV3SceneReleaseEffects.Conflict rejected) {
            releasePolicy.conflict(level, runtime, state, lease, rejected.reason()); return;
        }
        var payload = ((FrontierV3SceneReleaseEffects.Ready) effects).payload();
        CommandResult result = releaseLoaded(runtime, lease, binding, payload);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_released", lease, result);
        if (result instanceof CommandResult.Accepted) {
            FrontierWorldState releasedState = runtime.decodedState().orElseThrow();
            for (SceneMember member : lease.members()) {
                if (level.getEntity(member.entityId()) instanceof Mob body)
                    FrontierV3ActorCarryProjection.rememberConfirmed(releasedState, member.actorId(), body);
            }
            // Scope closure never publishes physical absence. The common owner checks its
            // retained save/sync/read proof again, then commits pose and body release together.
            for (SceneMember member : departedMembers) {
                FrontierV3ActorBodyController.progressDeparture(level, runtime, runtime.decodedState().orElseThrow(), member.actorId());
            }
        }
    }
    static void releaseCustodyConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierWorldState state, SceneLease lease, String reason) {
        conflict(level, runtime, state, lease, reason);
    }
    /** A rejected release retains its same observation for diagnosis; it is never rewritten or guessed. */
    private static CommandResult releaseLoaded(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
                                               java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding,
                                               FrontierPayload payload) {
        CommandResult result = binding.map(action -> FrontierV3CommandSubmission.submitBound(runtime, "scene-release", lease.id().value(),
                        payload, action))
                .orElseGet(() -> submit(runtime, "scene-release", lease.id().value(), payload));
        if (result instanceof CommandResult.Accepted) forgetLeaseTransient(runtime, lease.id());
        return result;
    }
}
