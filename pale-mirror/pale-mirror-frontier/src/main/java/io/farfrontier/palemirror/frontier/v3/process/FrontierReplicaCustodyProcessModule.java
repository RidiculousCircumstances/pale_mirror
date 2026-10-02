package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceSurfaceVerified;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceSurfaceRecovery;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.*;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPayloads.*;

import java.util.List;

/** Sole reducer boundary for pure replica evidence and current physical custody. */
final class FrontierReplicaCustodyProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        try {
            return switch (command.payload()) {
                case ReferenceSurfaceVerified verified -> {
                    ReferenceSurfaceRecovery.verify(state, verified);
                    yield accepted(verified.containerId(), verified);
                }
                case CargoCleanupSaved saved -> {
                    state.fencedRecovery().acknowledgeCargoCleanupSaved(saved.retirement());
                    yield accepted(saved.retirement().cargoId(), saved);
                }
                case ReplicaDeclared declared -> { state.replicaCustody().declare(declared.replica()); yield accepted(declared.replica().objectId(), declared); }
                case ReplicaEmitted emitted -> { state.replicaCustody().emit(emitted.objectId(), emitted.expectedCanonicalRevision(), emitted.expectedReplicaRevision(),
                        emitted.emittedCanonicalRevision(), emitted.fingerprint(), emitted.provenance()); yield accepted(emitted.objectId(), emitted); }
                case ReplicaObserved observed -> { state.replicaCustody().observe(observed.objectId(), observed.expectedCanonicalRevision(), observed.expectedReplicaRevision(),
                        observed.fingerprint(), observed.provenance(), observed.observedCanonicalRevision()); yield accepted(observed.objectId(), observed); }
                case ReplicaConflictObserved conflict -> { state.replicaCustody().conflict(conflict.objectId(), conflict.expectedCanonicalRevision(), conflict.expectedReplicaRevision(),
                        conflict.fingerprint(), conflict.provenance(), conflict.diagnostic()); yield accepted(conflict.objectId(), conflict); }
                case CustodyAcquired acquired -> { state.replicaCustody().acquire(acquired.lease()); yield accepted(acquired.lease().scopeId(), acquired); }
                case ProjectionCustodyPrepared prepared -> { state.replicaCustody().prepareProjection(prepared.lease()); yield accepted(prepared.lease().scopeId(), prepared); }
                case ReferenceProjectionPrepared prepared -> {
                    io.farfrontier.palemirror.frontier.v3.model.ReferenceProjectionStateSupport.prepare(state, prepared, command.expectedRevision().next().value());
                    yield accepted(prepared.containerId(), prepared);
                }
                case ProjectionCustodyConfirmed confirmed -> {
                    state.replicaCustody().confirmProjection(confirmed.scopeId(), confirmed.expectedEpoch(), confirmed.expectedCanonicalRevision(),
                            confirmed.expectedReplicaRevision(), confirmed.fingerprint(), confirmed.provenance());
                    yield accepted(confirmed.scopeId(), confirmed);
                }
                case CustodyCheckpointed checkpointed -> { state.replicaCustody().checkpoint(checkpointed.scopeId(), checkpointed.expectedEpoch(),
                        checkpointed.expectedCanonicalRevision(), checkpointed.expectedReplicaRevision()); yield accepted(checkpointed.scopeId(), checkpointed); }
                case ProjectionConflictObserved conflict -> {
                    state.replicaCustody().conflictProjection(conflict.scopeId(), conflict.expectedEpoch(), conflict.expectedCanonicalRevision(),
                            conflict.expectedReplicaRevision(), conflict.fingerprint(), conflict.provenance(), conflict.diagnostic());
                    yield accepted(conflict.scopeId(), conflict);
                }
                case CustodyUnresolved unresolved -> {
                    state.replicaCustody().unresolved(unresolved.scopeId(), unresolved.expectedEpoch(),
                            unresolved.expectedCanonicalRevision(), unresolved.expectedReplicaRevision(), unresolved.reason(), unresolved.diagnostic());
                    yield accepted(unresolved.scopeId(), unresolved);
                }
                case CustodyReleased released -> {
                    var after = ReferenceContainerCustody.release(state, released);
                    var events = new java.util.ArrayList<ProposedEvent>();
                    events.add(new ProposedEvent(released.scopeId(), released));
                    events.addAll(ProductionProcess.resumeReleasedEffects(state, after, command.submittedAt().ticks()));
                    yield new CommandPlan.Accepted(List.copyOf(events));
                }
                case ReferenceMutationClosed closed -> {
                    var after = ReferenceContainerCustody.closeConfirmedMutation(state, closed);
                    var events = new java.util.ArrayList<ProposedEvent>();
                    events.add(new ProposedEvent(closed.objectId(), closed));
                    events.addAll(ProductionProcess.resumeReleasedEffects(state, after, command.submittedAt().ticks()));
                    yield new CommandPlan.Accepted(List.copyOf(events));
                }
                case Prepared prepared -> { state.fencedRecovery().prepare(prepared.binding()); yield accepted(prepared.binding().bindingId(), prepared); }
                case Running running -> { state.fencedRecovery().running(running.bindingId(), running.expectedEpoch()); yield accepted(running.bindingId(), running); }
                case Observed observed -> { state.fencedRecovery().observed(observed.bindingId(), observed.expectedEpoch()); yield accepted(observed.bindingId(), observed); }
                case Confirmed confirmed -> { state.fencedRecovery().confirm(confirmed.bindingId(), confirmed.expectedEpoch()); yield accepted(confirmed.bindingId(), confirmed); }
                case RevokedToCold revoked -> { state.fencedRecovery().revokeToCold(revoked.bindingId(), revoked.expectedEpoch()); yield accepted(revoked.bindingId(), revoked); }
                case Ambiguous ambiguous -> { state.fencedRecovery().ambiguous(ambiguous.bindingId(), ambiguous.expectedEpoch(), ambiguous.reason(), ambiguous.action()); yield accepted(ambiguous.bindingId(), ambiguous); }
                case Abandoned abandoned -> { state.fencedRecovery().abandon(abandoned.bindingId(), abandoned.expectedEpoch()); yield accepted(abandoned.bindingId(), abandoned); }
                default -> FrontierWorldCommandPlanner.rejected("replica custody process does not admit command: " + command.payload().type());
            };
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        try {
            return switch (event.payload()) {
                case ReferenceSurfaceVerified verified -> {
                    if (!event.subject().equals(verified.containerId())) throw new IllegalArgumentException("surface verification has a foreign subject");
                    yield ReferenceSurfaceRecovery.verify(state, verified);
                }
                case CargoCleanupSaved saved -> {
                    if (!event.subject().equals(saved.retirement().cargoId())) throw new IllegalArgumentException("cargo cleanup has a foreign subject");
                    yield replaceRecovery(state, state.fencedRecovery().acknowledgeCargoCleanupSaved(saved.retirement()));
                }
                case ReplicaDeclared declared -> replace(state, state.replicaCustody().declare(declared.replica()));
                case ReplicaEmitted emitted -> replace(state, state.replicaCustody().emit(emitted.objectId(), emitted.expectedCanonicalRevision(), emitted.expectedReplicaRevision(),
                        emitted.emittedCanonicalRevision(), emitted.fingerprint(), emitted.provenance()));
                case ReplicaObserved observed -> replace(state, state.replicaCustody().observe(observed.objectId(), observed.expectedCanonicalRevision(),
                        observed.expectedReplicaRevision(), observed.fingerprint(), observed.provenance(), observed.observedCanonicalRevision()));
                case ReplicaConflictObserved conflict -> replace(state, state.replicaCustody().conflict(conflict.objectId(), conflict.expectedCanonicalRevision(),
                        conflict.expectedReplicaRevision(), conflict.fingerprint(), conflict.provenance(), conflict.diagnostic()));
                case CustodyAcquired acquired -> replace(state, state.replicaCustody().acquire(acquired.lease()));
                case ProjectionCustodyPrepared prepared -> {
                    if (!event.subject().equals(prepared.lease().scopeId())) throw new IllegalArgumentException("projection preparation has a foreign scope");
                    yield replace(state, state.replicaCustody().prepareProjection(prepared.lease()));
                }
                case ReferenceProjectionPrepared prepared -> {
                    if (!event.subject().equals(prepared.containerId())) throw new IllegalArgumentException("reference projection has a foreign container");
                    yield io.farfrontier.palemirror.frontier.v3.model.ReferenceProjectionStateSupport.prepare(state, prepared, event.revision().value());
                }
                case ProjectionCustodyConfirmed confirmed -> {
                    if (!event.subject().equals(confirmed.scopeId())) throw new IllegalArgumentException("projection confirmation has a foreign scope");
                    yield replace(state, state.replicaCustody().confirmProjection(confirmed.scopeId(), confirmed.expectedEpoch(), confirmed.expectedCanonicalRevision(),
                            confirmed.expectedReplicaRevision(), confirmed.fingerprint(), confirmed.provenance()));
                }
                case CustodyCheckpointed checkpointed -> replace(state, state.replicaCustody().checkpoint(checkpointed.scopeId(), checkpointed.expectedEpoch(), checkpointed.expectedCanonicalRevision(), checkpointed.expectedReplicaRevision()));
                case ProjectionConflictObserved conflict -> {
                    if (!event.subject().equals(conflict.scopeId())) throw new IllegalArgumentException("projection conflict has a foreign scope");
                    yield replace(state, state.replicaCustody().conflictProjection(conflict.scopeId(), conflict.expectedEpoch(), conflict.expectedCanonicalRevision(),
                            conflict.expectedReplicaRevision(), conflict.fingerprint(), conflict.provenance(), conflict.diagnostic()));
                }
                case CustodyUnresolved unresolved -> replace(state, state.replicaCustody().unresolved(unresolved.scopeId(), unresolved.expectedEpoch(),
                        unresolved.expectedCanonicalRevision(), unresolved.expectedReplicaRevision(), unresolved.reason(), unresolved.diagnostic()));
                case CustodyReleased released -> ReferenceContainerCustody.release(state, released);
                case ReferenceMutationClosed closed -> ReferenceContainerCustody.closeConfirmedMutation(state, closed);
                case Prepared prepared -> replaceRecovery(state, state.fencedRecovery().prepare(prepared.binding()));
                case Running running -> replaceRecovery(state, state.fencedRecovery().running(running.bindingId(), running.expectedEpoch()));
                case Observed observed -> replaceRecovery(state, state.fencedRecovery().observed(observed.bindingId(), observed.expectedEpoch()));
                case Confirmed confirmed -> replaceRecovery(state, state.fencedRecovery().confirm(confirmed.bindingId(), confirmed.expectedEpoch()));
                case RevokedToCold revoked -> replaceRecovery(state, state.fencedRecovery().revokeToCold(revoked.bindingId(), revoked.expectedEpoch()));
                case Ambiguous ambiguous -> replaceRecovery(state, state.fencedRecovery().ambiguous(ambiguous.bindingId(), ambiguous.expectedEpoch(), ambiguous.reason(), ambiguous.action()));
                case Abandoned abandoned -> replaceRecovery(state, state.fencedRecovery().abandon(abandoned.bindingId(), abandoned.expectedEpoch()));
                default -> throw new IllegalArgumentException("replica custody process does not own event: " + event.payload().type());
            };
        } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("replica custody event rejected: " + invalid.getMessage(), invalid); }
    }
    private static FrontierWorldState replace(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyState next) {
        return state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(next));
    }
    private static FrontierWorldState replaceRecovery(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryState next) {
        return state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(next));
    }
    private static CommandPlan accepted(io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return new CommandPlan.Accepted(List.of(new ProposedEvent(subject, payload)));
    }
}
