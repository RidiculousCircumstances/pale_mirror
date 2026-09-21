package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.HashMap;
import java.util.HashSet;
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
            "system:terminal-logistics-retention", "system:structural-repair", "system:route-maintenance",
            "system:hive-mobilization", "system:decontamination", "system:route-construction", "system:population");

    private FrontierReferenceClosure() { }

    static void validate(FrontierWorldState state, List<ScheduledAction> schedules) {
        FrontierDomainRelationships.verifyCurrentOwnerSurfaces();
        FrontierDomainRelationships.validate(state);
        validateExactPhysicalCustody(state);
        validateScheduledSubjects(state, schedules);
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

    private static void validateScheduledSubjects(FrontierWorldState state, List<ScheduledAction> schedules) {
        Set<SubjectId> live = liveSubjects(state);
        for (ScheduledAction action : schedules) {
            if (!live.contains(action.subject()) && !SYSTEM_SUBJECTS.contains(action.subject().value())) {
                throw new IllegalArgumentException("scheduled action references a retired canonical subject: " + action.id().value());
            }
        }
    }

    private static Set<SubjectId> liveSubjects(FrontierWorldState state) {
        Set<SubjectId> live = new HashSet<>();
        state.bootstrap().settlements().forEach(settlement -> {
            live.add(settlement.id()); settlement.residents().forEach(resident -> live.add(resident.id()));
        });
        live.add(state.bootstrap().hive().id());
        state.bootstrap().hive().bioforms().forEach(bioform -> live.add(bioform.id()));
        state.bootstrap().hive().organs().forEach(organ -> live.add(organ.id()));
        live.addAll(state.actorLocations().keySet()); live.addAll(state.structureConditions().keySet());
        live.addAll(state.inventory().items().keySet()); live.addAll(state.inventory().cargo().keySet());
        live.addAll(state.inventory().fungibleResources().lots().keySet()); live.addAll(state.inventory().fungibleResources().claims().keySet());
        live.addAll(state.productionJobs().keySet()); live.addAll(state.serviceWorks().keySet()); live.addAll(state.contracts().keySet());
        live.addAll(state.operations().keySet()); live.addAll(state.routeConstructions().keySet()); live.addAll(state.routeMaintenances().keySet());
        live.addAll(state.resourceSites().sites().keySet());
        state.resourceSites().sites().values().forEach(site -> site.activeWork().ifPresent(work -> live.add(work.id())));
        live.addAll(state.humanPopulation().residents().keySet());
        live.addAll(state.humanPopulation().birthJobs().keySet()); live.addAll(state.humanPopulation().migrations().keySet());
        live.addAll(state.humanPopulation().provisions().keySet()); live.addAll(state.hiveColony().growthJobs().keySet());
        live.addAll(state.hiveColony().mobilizations().keySet()); live.addAll(state.strategicPlans().objectives().keySet());
        live.addAll(state.strategicPlans().tasks().keySet()); live.addAll(state.strategicPlans().routePatrols().keySet());
        live.addAll(state.strategicPlans().routeEngagements().keySet()); live.addAll(state.strategicPlans().settlementAssaults().keySet());
        live.addAll(state.companies().market().demands().keySet()); live.addAll(state.companies().market().quotes().keySet());
        live.addAll(state.companies().market().workOrders().keySet());
        return Set.copyOf(live);
    }
}
