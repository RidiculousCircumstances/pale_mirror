package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Appointment-specific permission and retirement; shared equipment owns the physical transaction. */
final class GuardEquipmentLifecycle {
    static PhysicalIntentLifecycleCapability capability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleDeclaration.physical(
                PhysicalIntentLifecycleOwner.GUARD_DUTY, Set.of(PhysicalIntentKind.EQUIPMENT_ISSUE, PhysicalIntentKind.EQUIPMENT_RETURN),
                Set.of(PhysicalIntentRoleSchema.GUARD_EQUIPMENT_ISSUE, PhysicalIntentRoleSchema.GUARD_EQUIPMENT_RETURN)),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("only the guard owner prepares its equipment"),
                GuardEquipmentLifecycle::plan, GuardEquipmentLifecycle::prepare, GuardEquipmentLifecycle::reduce,
                PhysicalIntentLifecycleRetirementPolicy.of(GuardEquipmentLifecycle::plan, GuardEquipmentLifecycle::reduce),
                intent -> FencedRecoveryAsset.EFFECT, account(), EquipmentItemDeparture::mayCompact,
                PhysicalIntentRecoveryDiagnosticProducer.GUARD_DUTY);
    }
    private static void validate(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        if (transition.status() == PhysicalIntentStatus.RUNNING) EquipmentServiceBoundary.requireMayBegin(state, intent);
        else EquipmentServiceBoundary.validateContinuation(state, intent);
    }
    private static CommandPlan plan(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent, PhysicalIntentTransition transition) {
        validate(state, intent, transition);
        var events = new ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(intent.causeSubjectId(), transition));
        if (transition.status() == PhysicalIntentStatus.CONFIRMED)
            events.add(GuardContinuation.wake(intent.roles().require(PhysicalIntentSubjectRole.GUARD_DUTY), command.submittedAt().ticks()));
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    private static FrontierWorldState prepare(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("guard equipment requires its exact settlement");
        EquipmentServiceBoundary.validateIntent(state, intent);
        return state.preparePhysicalIntent(intent);
    }
    private static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, PhysicalIntent intent, PhysicalIntentTransition transition) {
        if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("guard equipment has a foreign settlement");
        validate(state, intent, transition);
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (before, current, evidence, intents) -> HumanEquipmentStateSupport.complete(before, current, evidence, new LinkedHashMap<>(intents)),
                PhysicalIntentTransitionStorage::recordUnknown);
    }
    private static PhysicalIntentRetirementAccount account() {
        return PhysicalIntentRetirementAccount.declared(PhysicalIntentLifecycleOwner.GUARD_DUTY,
                EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (state, command, intent, transition) -> facts(state, intent, transition, command == null
                    ? new PhysicalIntentRetirementAccount.CheckedNone<ScheduleId>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                    : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<ScheduleId>>map(binding -> new PhysicalIntentRetirementAccount.Exact<>(binding.action().id()))
                        .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION))),
                (state, intent, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding, facts(state, intent, transition, binding.continuation())),
                (before, after, intent, transition, binding) -> {
                    var duty = GuardEquipmentStateSupport.require(before, intent);
                    if (!Objects.equals(after.strategicPlans().guards().get(duty.id()), duty))
                        throw new IllegalArgumentException("equipment retirement cannot replace its appointment");
                    if (after == before) return; // Plan-time binding check precedes the owned custody consequence.
                    if (transition.status() == PhysicalIntentStatus.CONFIRMED) {
                        var item = after.inventory().items().get(duty.weapon());
                        InventoryCustody target = intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE
                                ? new InventoryCustody.Actor(duty.execution().actorId()) : EquipmentServiceBoundary.targetSlot(intent);
                        if (item == null || !item.custody().equals(target) || !item.economicOwnerId().equals(duty.home()))
                            throw new IllegalArgumentException("guard equipment receipt lost its exact holder or owner");
                    }
                });
    }
    private static PhysicalIntentRetirementAccount.Binding facts(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition,
            PhysicalIntentRetirementAccount.Obligation<ScheduleId> continuation) {
        var duty = GuardEquipmentStateSupport.require(state, intent);
        EquipmentServiceBoundary.validateContinuation(state, intent);
        return new PhysicalIntentRetirementAccount.Binding(intent.lifecycleOwner(), intent.id(),
                new PhysicalIntentRetirementAccount.Exact<>(GuardRelationships.edges(state, duty)),
                continuation, new PhysicalIntentRetirementAccount.Exact<>(duty.execution().actorId()), new PhysicalIntentRetirementAccount.Exact<>(duty.weapon()),
                transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY
                        : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE);
    }
    private GuardEquipmentLifecycle() { }
}
