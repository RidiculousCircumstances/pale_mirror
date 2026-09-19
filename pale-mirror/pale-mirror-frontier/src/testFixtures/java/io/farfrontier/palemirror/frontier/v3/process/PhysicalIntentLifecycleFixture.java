package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

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
        PhysicalIntentTransition requested = new PhysicalIntentTransition(intent.id(), status, observation);
        CommandPlan plan = FrontierWorldProcessCatalog.physicalLifecycles().planTransition(state,
                fixtureCommand(state, subject, requested), current, requested);
        if (!(plan instanceof CommandPlan.Accepted accepted)) {
            CommandPlan.Rejected rejected = (CommandPlan.Rejected) plan;
            throw new IllegalArgumentException("fixture transition is rejected by its exact lifecycle owner: " + rejected.rejection().detail());
        }
        PhysicalIntentTransition accounted = accepted.events().stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(PhysicalIntentTransition.class::isInstance).map(PhysicalIntentTransition.class::cast)
                .filter(candidate -> candidate.intentId().equals(intent.id()) && candidate.status() == status).reduce((left, right) -> {
                    throw new IllegalArgumentException("fixture transition has duplicate terminal owner events");
                }).orElseThrow(() -> new IllegalArgumentException("fixture transition lacks its terminal owner event"));
        return FrontierWorldProcessCatalog.physicalLifecycles().reduceTransition(state, subject, current, accounted);
    }

    private static FrontierCommand fixtureCommand(FrontierWorldState state, SubjectId subject,
                                                   PhysicalIntentTransition transition) {
        CommandId id = new CommandId("fixture:physical-transition-" + transition.intentId().value().replace(':', '-'));
        return new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, state.bootstrap().worldId(), Revision.ZERO,
                SimInstant.ZERO, subject, CauseChain.root(id), transition);
    }
}
