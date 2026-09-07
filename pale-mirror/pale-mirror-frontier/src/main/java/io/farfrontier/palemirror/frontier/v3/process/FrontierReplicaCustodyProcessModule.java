package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.*;

import java.util.List;

/** Sole reducer boundary for pure replica evidence and current physical custody. */
final class FrontierReplicaCustodyProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        try {
            return switch (command.payload()) {
                case ReplicaDeclared declared -> { state.replicaCustody().declare(declared.replica()); yield accepted(declared.replica().objectId(), declared); }
                case ReplicaObserved observed -> { state.replicaCustody().observe(observed.objectId(), observed.expectedReplicaRevision(),
                        observed.fingerprint(), observed.provenance(), observed.observedCanonicalRevision()); yield accepted(observed.objectId(), observed); }
                case CustodyAcquired acquired -> { state.replicaCustody().acquire(acquired.lease()); yield accepted(acquired.lease().scopeId(), acquired); }
                case CustodyCheckpointed checkpointed -> { state.replicaCustody().checkpoint(checkpointed.scopeId(), checkpointed.expectedEpoch(),
                        checkpointed.expectedCanonicalRevision(), checkpointed.expectedReplicaRevision()); yield accepted(checkpointed.scopeId(), checkpointed); }
                case CustodyUnresolved unresolved -> { state.replicaCustody().unresolved(unresolved.scopeId(), unresolved.expectedEpoch(), unresolved.reason()); yield accepted(unresolved.scopeId(), unresolved); }
                case CustodyReleased released -> { state.replicaCustody().release(released.scopeId(), released.expectedEpoch(),
                        released.expectedCanonicalRevision(), released.expectedReplicaRevision()); yield accepted(released.scopeId(), released); }
                default -> FrontierWorldCommandPlanner.rejected("replica custody process does not admit command: " + command.payload().type());
            };
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        try {
            return switch (event.payload()) {
                case ReplicaDeclared declared -> replace(state, state.replicaCustody().declare(declared.replica()));
                case ReplicaObserved observed -> replace(state, state.replicaCustody().observe(observed.objectId(), observed.expectedReplicaRevision(), observed.fingerprint(), observed.provenance(), observed.observedCanonicalRevision()));
                case CustodyAcquired acquired -> replace(state, state.replicaCustody().acquire(acquired.lease()));
                case CustodyCheckpointed checkpointed -> replace(state, state.replicaCustody().checkpoint(checkpointed.scopeId(), checkpointed.expectedEpoch(), checkpointed.expectedCanonicalRevision(), checkpointed.expectedReplicaRevision()));
                case CustodyUnresolved unresolved -> replace(state, state.replicaCustody().unresolved(unresolved.scopeId(), unresolved.expectedEpoch(), unresolved.reason()));
                case CustodyReleased released -> replace(state, state.replicaCustody().release(released.scopeId(), released.expectedEpoch(), released.expectedCanonicalRevision(), released.expectedReplicaRevision()));
                default -> throw new IllegalArgumentException("replica custody process does not own event: " + event.payload().type());
            };
        } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("replica custody event rejected: " + invalid.getMessage(), invalid); }
    }
    private static FrontierWorldState replace(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyState next) {
        return state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(next));
    }
    private static CommandPlan accepted(io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return new CommandPlan.Accepted(List.of(new ProposedEvent(subject, payload)));
    }
}
