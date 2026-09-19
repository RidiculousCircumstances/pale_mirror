package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Exact reducer owner for settlement strategic facts. */
final class FrontierStrategyProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleOwner.DECONTAMINATION,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.DECONTAMINATION),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare strategic decontamination"),
                (state, command, intent, transition) -> new CommandPlan.Accepted(
                        DecontaminationProcess.planTransition(state, intent, transition)),
                DecontaminationProcess::reducePrepared,
                (state, subject, intent, transition) -> {
                    if (!subject.equals(DecontaminationProcess.owner(state, intent.causeSubjectId()).id())) {
                        throw new IllegalArgumentException("strategic decontamination transition lacks its owning settlement");
                    }
                    DecontaminationProcess.taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
                    return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                            (currentState, current, evidence, intents) -> SettlementServiceDecontaminationStateSupport.complete(currentState, current,
                                    evidence, new java.util.LinkedHashMap<>(intents)),
                            PhysicalIntentTransitionStorage::recordUnknown);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> new CommandPlan.Accepted(
                                DecontaminationProcess.planTransition(state, intent, transition)),
                        (state, subject, intent, transition) -> {
                            if (!subject.equals(DecontaminationProcess.owner(state, intent.causeSubjectId()).id())) {
                                throw new IllegalArgumentException("strategic decontamination retirement lacks its owning settlement");
                            }
                            DecontaminationProcess.taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
                            return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                                    (currentState, current, evidence, intents) -> SettlementServiceDecontaminationStateSupport.complete(currentState, current,
                                            evidence, new java.util.LinkedHashMap<>(intents)),
                                    PhysicalIntentTransitionStorage::recordUnknown);
                        }), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.DECONTAMINATION)));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, after, intent, transition) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("strategy retirement account owner mismatch");
                });
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case SettlementInfectionObserved observed -> SettlementPerceptionProcess.reduce(state, event.subject(), observed);
            case StrategicObjectiveSelected selected -> StrategicObjectiveProcess.reduceObjective(state, event.subject(), selected);
            case StrategicTaskPlanned planned -> StrategicObjectiveProcess.reduceTask(state, event.subject(), planned);
            case StrategicTaskTransition transition -> StrategicObjectiveProcess.reduceTaskTransition(state, event.subject(), transition);
            default -> throw new IllegalArgumentException("strategy process does not own event: " + event.payload().type());
        };
    }
}
