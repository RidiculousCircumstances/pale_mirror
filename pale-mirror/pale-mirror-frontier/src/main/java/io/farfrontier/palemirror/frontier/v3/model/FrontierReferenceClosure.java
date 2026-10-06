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
            "system:terminal-logistics-retention", "system:structural-repair", "system:route-maintenance",
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
        GoodsTradeStateSupport.validateTransition(before, after);
        ShipmentStateSupport.validateTransition(before, after);
        FrontierDomainRelationships.verifyCurrentOwnerSurfaces();
        FrontierDomainRelationships.validateTransition(before, after);
        // Each cross-owner check uses its own exact dependency set. Scheduled subjects
        // retain the full audit: a retired owner can invalidate an unchanged future action.
        ResidentMealReferenceClosure.validateTransition(before, after);
        if (before.physicalIntents() != after.physicalIntents() || before.inventory().items() != after.inventory().items())
            validateExactPhysicalCustody(after);
        validateScheduledSubjects(after, schedules);
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

    /** Existence validation only, never owner discovery or dispatch. No whole-world ID copy. */
    private static boolean isLiveSubject(FrontierWorldState state, SubjectId id) {
        if (state.actorLocations().containsKey(id) || state.structureConditions().containsKey(id)
                || state.inventory().items().containsKey(id) || state.inventory().cargo().containsKey(id)
                || state.inventory().fungibleResources().lots().containsKey(id)
                || state.inventory().fungibleResources().claims().containsKey(id)
                || state.productionJobs().containsKey(id) || state.serviceWorks().containsKey(id)
                || state.contracts().containsKey(id) || state.operations().containsKey(id)
                || state.routeConstructions().containsKey(id) || state.routeMaintenances().containsKey(id)
                || state.resourceSites().sites().containsKey(id)
                || state.humanPopulation().residents().containsKey(id)
                || state.humanPopulation().birthJobs().containsKey(id) || state.humanPopulation().migrations().containsKey(id)
                || state.humanPopulation().provisions().containsKey(id) || state.hiveColony().growthJobs().containsKey(id)
                || state.hiveColony().nutrientTransfers().containsKey(id)
                || state.hiveColony().mobilizations().containsKey(id) || state.strategicPlans().objectives().containsKey(id)
                || state.strategicPlans().tasks().containsKey(id) || state.strategicPlans().routePatrols().containsKey(id)
                || state.strategicPlans().routeEngagements().containsKey(id)
                || state.strategicPlans().settlementAssaults().containsKey(id)
                || state.companies().market().demands().containsKey(id) || state.companies().market().quotes().containsKey(id)
                || state.companies().market().workOrders().containsKey(id)) return true;
        if (state.companies().goodsTrade().orders().containsKey(id) || state.companies().goodsTrade().contracts().containsKey(id)
                || state.shipments().shipments().containsKey(id)) return true;
        // These fixed bootstrap/site collections have no separate subject index. Retain the
        // same accepted owner surface as the full recovery barrier without building its union.
        return state.bootstrap().hive().id().equals(id)
                || state.bootstrap().settlements().stream().anyMatch(settlement -> settlement.id().equals(id)
                    || settlement.residents().stream().anyMatch(resident -> resident.id().equals(id)))
                || state.bootstrap().hive().bioforms().stream().anyMatch(bioform -> bioform.id().equals(id))
                || state.bootstrap().hive().organs().stream().anyMatch(organ -> organ.id().equals(id))
                || state.resourceSites().sites().values().stream()
                    .anyMatch(site -> site.harvestJobs().containsKey(id) || site.preparationWork().filter(work -> work.id().equals(id)).isPresent());
    }
}
