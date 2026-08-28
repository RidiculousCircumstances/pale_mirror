package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.List;

/** Pure command/reducer boundary for post-effect physical evidence. */
final class FrontierWorldPhysicalObservationProcess {
    private FrontierWorldPhysicalObservationProcess() { }

    static CommandPlan plan(FrontierWorldState state, PhysicalDeltaObserved observed) {
        try { state.recordPhysicalDelta(observed.delta()); }
        catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, observed)));
    }

    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                     PhysicalDeltaObserved observed) {
        if (!subject.equals(FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR)) throw new IllegalArgumentException("physical delta lacks trusted executor subject");
        return state.recordPhysicalDelta(observed.delta());
    }
}
