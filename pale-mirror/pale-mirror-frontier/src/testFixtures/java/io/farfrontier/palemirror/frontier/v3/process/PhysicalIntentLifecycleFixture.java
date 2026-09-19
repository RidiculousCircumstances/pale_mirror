package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;

import java.util.Optional;

/** Test-only ingress for legacy unit fixtures: owner identity is read from the durable intent and subject is explicit. */
public final class PhysicalIntentLifecycleFixture {
    private PhysicalIntentLifecycleFixture() { }

    public static FrontierWorldState prepare(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        return FrontierWorldProcessCatalog.physicalLifecycles().reducePrepared(state, subject, intent);
    }

    public static FrontierWorldState transition(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                                                PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation) {
        PhysicalIntent current = state.physicalIntents().get(intent.id());
        if (current == null || !current.lifecycleOwner().equals(intent.lifecycleOwner())) {
            throw new IllegalArgumentException("fixture transition has no exact owner-stamped intent");
        }
        return FrontierWorldProcessCatalog.physicalLifecycles().reduceTransition(state, subject, current,
                new PhysicalIntentTransition(intent.id(), status, observation));
    }
}
