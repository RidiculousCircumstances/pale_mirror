package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Exact reducer owner for settlement strategic facts. */
final class FrontierStrategyProcessModule implements FrontierWorldProcessModule {
    @Override public List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> retiredSchedules(
            FrontierWorldState previous, FrontierWorldState next,
            java.util.function.Supplier<List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction>> pending) {
        return StrategicScheduleRetirement.retiredBy(previous, next, pending);
    }

    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(new FunctionalPhysicalIntentLifecycleCapability(
                PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.DECONTAMINATION,
                        Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.DECONTAMINATION),
                        Set.of(PhysicalIntentRoleSchema.DECONTAMINATION)),
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
                retirementAccount(PhysicalIntentLifecycleOwner.DECONTAMINATION), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.DECONTAMINATION));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, command, intent, transition) -> retirementFacts(before, command, intent, transition, owner),
                (before, intent, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        retirementFacts(before, binding.continuation(), intent, transition, owner)),
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("strategy retirement account owner mismatch");
                    if (after == before) return;
                    SubjectId reagent = reagent(before, intent);
                    ExactItemStack beforeItem = before.inventory().items().get(reagent);
                    ExactItemStack afterItem = after.inventory().items().get(reagent);
                    if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                            && afterItem != null && afterItem.count() >= beforeItem.count()) {
                        throw new IllegalArgumentException("decontamination retirement did not consume its exact reagent");
                    }
                    if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                            && !java.util.Objects.equals(beforeItem, afterItem)) {
                        throw new IllegalArgumentException("ambiguous decontamination retirement lost its exact reagent");
                    }
                });
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state, FrontierCommand command,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition,
                                                                            PhysicalIntentLifecycleOwner owner) {
        return retirementFacts(state, command == null
                ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(value -> new PhysicalIntentRetirementAccount.Exact<>(value.action().id()))
                        .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)), intent, transition, owner);
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state,
                                                                            PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId> continuation,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition,
                                                                            PhysicalIntentLifecycleOwner owner) {
        SubjectId reagent = reagent(state, intent);
        // Decontamination's explicitly typed task has no REL projection: its facility and reagent
        // are retained on the intent and task, and the owner verifies both below.
        DecontaminationProcess.taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
        return new PhysicalIntentRetirementAccount.Binding(owner, intent.id(),
                new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION), continuation,
                new PhysicalIntentRetirementAccount.Exact<>(intent.causeSubjectId()),
                new PhysicalIntentRetirementAccount.Exact<>(reagent), lateDisposition(transition));
    }

    private static SubjectId reagent(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        DecontaminationStateSupport.validateIntent(state, intent);
        return intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.MATERIAL);
    }

    private static PhysicalIntentRetirementAccount.LateDisposition lateDisposition(PhysicalIntentTransition transition) {
        return transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY
                : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE;
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
