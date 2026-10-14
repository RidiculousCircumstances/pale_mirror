package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One publication/recovery barrier for cross-owner canonical references.
 *
 * <p>Aggregates remain the authority for their own relationships; this class only verifies the
 * complete composed result immediately before it becomes durable.  In particular, a current
 * exact inventory identity cannot be held by two nonterminal physical owners, even if those
 * owners belong to unrelated process families.</p>
 */
final class FrontierReferenceClosure {
    private static final Set<String> SYSTEM_SUBJECTS = Set.of(
            "system:structural-repair", "system:route-maintenance",
            "system:hive-mobilization", "system:decontamination", "system:route-construction", "system:population");

    private FrontierReferenceClosure() { }

    static void validate(FrontierWorldState state, List<ScheduledAction> schedules) {
        FrontierDomainRelationships.verifyCurrentOwnerSurfaces();
        FrontierDomainRelationships.validate(state);
        ShipmentStateSupport.validate(state);
        ResidentMealReferenceClosure.validate(state);
        validateExactPhysicalCustody(state);
        validateScheduledSubjects(state, schedules);
    }

    static void validateTransition(FrontierWorldState before, FrontierWorldState after, List<ScheduledAction> schedules) {
        validateTransition(before, after, schedules, schedules);
    }
    static void validateTransition(FrontierWorldState before, FrontierWorldState after, List<ScheduledAction> schedules,
                                   List<ScheduledAction> retainedChanges) {
        GoodsTradeStateSupport.validateTransition(before, after);
        ShipmentStateSupport.validateTransition(before, after);
        FrontierDomainRelationships.verifyCurrentOwnerSurfaces();
        FrontierDomainRelationships.validateTransition(before, after);
        // Changed schedules and retired targets are independent dimensions:
        // owner retirement must still reject unchanged incoming continuations.
        ResidentMealReferenceClosure.validateTransition(before, after);
        if (before.physicalIntents() != after.physicalIntents() || before.inventory().items() != after.inventory().items())
            validateExactPhysicalCustody(after);
        var oldSubjects = ScheduledSubjectExistence.subjects(before);
        var subjects = ScheduledSubjectExistence.subjects(after);
        if (oldSubjects != subjects) {
            var removed = new java.util.HashSet<>(oldSubjects); removed.removeAll(subjects);
            for (var action : schedules) if (removed.contains(action.subject())
                    && !SYSTEM_SUBJECTS.contains(action.subject().value()))
                throw new IllegalArgumentException("scheduled action references a retired canonical subject: " + action.id().value());
        }
        validateScheduledSubjects(after, retainedChanges);
    }

    private static void validateExactPhysicalCustody(FrontierWorldState state) {
        Map<SubjectId, PhysicalIntent> ownerByCurrentItem = new HashMap<>();
        for (PhysicalIntent intent : state.physicalIntents().values()) {
            if (intent.status() == PhysicalIntentStatus.CONFIRMED || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) continue;
            for (SubjectId subject : intent.roles().namedRoles().values()) {
                // Only an existing exact inventory identity is a consumable/transferable physical
                // subject.  Planned cargo and output identities are not falsely treated as stock.
                if (!state.inventory().items().containsKey(subject)) continue;
                PhysicalIntent previous = ownerByCurrentItem.putIfAbsent(subject, intent);
                // A composed owner may deliberately retain a short physical sub-lane (for
                // example, service input issue followed by its endpoint).  Independent owners
                // may not both claim the same current identity; that would publish an ambiguous
                // retirement/custody graph like the provision-versus-cargo r10 incident.
                if (previous != null && !previous.causeSubjectId().equals(intent.causeSubjectId())) {
                    throw new IllegalArgumentException("exact inventory subject has multiple nonterminal physical owners: " + subject.value());
                }
            }
        }
    }

    static void validateScheduledSubjects(FrontierWorldState state, List<ScheduledAction> schedules) {
        for (ScheduledAction action : schedules) {
            if (!SYSTEM_SUBJECTS.contains(action.subject().value()) && !isLiveSubject(state, action.subject())) {
                throw new IllegalArgumentException("scheduled action references a retired canonical subject: " + action.id().value());
            }
        }
    }

    /** Existence validation only, never owner discovery or dispatch. */
    private static boolean isLiveSubject(FrontierWorldState state, SubjectId id) {
        return ScheduledSubjectExistence.subjects(state).contains(id);
    }
}
