package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
import java.util.function.Function;

/** Existing physical-subject existence invariants with complete declared read dependencies.
 * This checks references only; it never selects a kind, owner or behavior from membership. */
final class PhysicalIntentReferenceValidation {
    private record Inputs(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
                  Set<SubjectId> expectedActors, Set<SubjectId> expectedStructures, Set<SubjectId> expectedSettlements,
                  Map<PhysicalIntentId, PhysicalIntent> physicalIntents, ExactInventory inventory, HiveColony hiveColony,
                  HumanPopulation humanPopulation, Map<SubjectId, ProductionJob> productionJobs,
                  Map<SubjectId, SettlementServiceWork> serviceWorks, StrategicPlanState strategicPlans,
                  Map<SubjectId, RouteConstruction> routeConstructions, Map<SubjectId, RouteMaintenance> routeMaintenances,
                  ResourceSiteState resourceSites, ResourceSitePhysicalIntentStateSupport.ReferenceView fieldReferences) { }
    private static final ImmutableInputView<Object> COMPOSITION = new ImmutableInputView<>();
    private static final ImmutableInputView<Map<SubjectId, Boolean>> FIELDS = new ImmutableInputView<>();
    private static final ImmutableRegistryView<SubjectId, ProductionJob, SubjectId> OUTPUT_IDS =
            new ImmutableRegistryView<>(Integer.MAX_VALUE);
    private static final ImmutableInputView<Map<SubjectId, Boolean>> OUTPUTS = new ImmutableInputView<>();
    private static <V> ImmutableReferenceIndex.Source<Inputs, SubjectId, V> membership(Function<Inputs, Map<SubjectId, V>> values) {
        return new ImmutableReferenceIndex.Source<>(values, (before, after) -> (before == null) == (after == null));
    }
    private static final List<ImmutableReferenceIndex.Source<Inputs, SubjectId, ?>> SOURCES = List.of(
            membership(Inputs::actorLocations), membership(i -> i.inventory().containers()), membership(i -> i.inventory().items()),
            membership(i -> i.inventory().cargo()), membership(i -> i.inventory().fungibleResources().accounts()),
            membership(i -> i.inventory().fungibleResources().lots()), membership(i -> i.inventory().fungibleResources().claims()),
            membership(i -> i.hiveColony().growthJobs()), membership(i -> i.humanPopulation().birthJobs()),
            membership(i -> i.humanPopulation().medicalOperations()), membership(i -> i.humanPopulation().provisions()),
            membership(Inputs::productionJobs), membership(Inputs::serviceWorks), membership(i -> i.strategicPlans().guards()), membership(PhysicalIntentReferenceValidation::fieldSubjects),
            membership(PhysicalIntentReferenceValidation::outputSubjects),
            new ImmutableReferenceIndex.Source<>(Inputs::routeConstructions), new ImmutableReferenceIndex.Source<>(Inputs::routeMaintenances),
            new ImmutableReferenceIndex.Source<>(i -> i.fieldReferences().conflicts()));
    private static final ImmutableReferenceIndex<Inputs, PhysicalIntentId, PhysicalIntent> REFERENCES = new ImmutableReferenceIndex<>(
            FrontierWorldState.MAX_PHYSICAL_INTENTS, SOURCES.size() * (PhysicalIntentSubjectRole.values().length + 1),
            new ArrayList<>(SOURCES), (input, intent) -> {
                if (intent.status() == PhysicalIntentStatus.CONFIRMED || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                    return List.of();
                var subjects = new LinkedHashSet<SubjectId>(); subjects.add(intent.causeSubjectId());
                subjects.addAll(intent.roles().namedRoles().values());
                var reads = new ArrayList<ImmutableReferenceIndex.Read<Inputs>>();
                for (var subject : subjects) for (var source : SOURCES) reads.add(source.at(subject));
                return reads;
            }, PhysicalIntentReferenceValidation::validateIntent);
    private PhysicalIntentReferenceValidation() { }
    static void validate(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
            Set<SubjectId> expectedActors, Set<SubjectId> expectedStructures, Set<SubjectId> expectedSettlements,
            Map<PhysicalIntentId, PhysicalIntent> physicalIntents, ExactInventory inventory, HiveColony hiveColony,
            HumanPopulation humanPopulation, Map<SubjectId, ProductionJob> productionJobs,
            Map<SubjectId, SettlementServiceWork> serviceWorks, StrategicPlanState strategicPlans,
            Map<SubjectId, RouteConstruction> routeConstructions, Map<SubjectId, RouteMaintenance> routeMaintenances,
            ResourceSiteState resourceSites) {
        var input = new Inputs(bootstrap, actorLocations, expectedActors, expectedStructures, expectedSettlements, physicalIntents,
                inventory, hiveColony, humanPopulation, productionJobs, serviceWorks, strategicPlans, routeConstructions,
                routeMaintenances, resourceSites, ResourceSitePhysicalIntentStateSupport.referenceView(resourceSites));
        if (input.physicalIntents().size() > FrontierWorldState.MAX_PHYSICAL_INTENTS)
            throw new IllegalArgumentException("physical intent retention limit exceeded");
        // These remaining family predicates read global composition, not individual
        // reference rows. Keep their complete roots until their owners expose narrower facts.
        var epoch = COMPOSITION.get(List.of(input.bootstrap(), input.expectedActors(), input.expectedStructures(),
                input.expectedSettlements(), input.hiveColony(), input.strategicPlans().settlementAssaults()), Object::new);
        REFERENCES.validate(epoch, input.physicalIntents(), input);
    }
    private static Map<SubjectId, Boolean> fieldSubjects(Inputs input) {
        return FIELDS.get(List.of(input.fieldReferences().subjects()), () -> membershipMap(input.fieldReferences().subjects()));
    }
    private static Map<SubjectId, Boolean> outputSubjects(Inputs input) {
        var outputs = OUTPUT_IDS.project(input.bootstrap(), input.productionJobs(), (id, job) -> job.outputItemId());
        return OUTPUTS.get(List.of(outputs), () -> membershipMap(outputs.values()));
    }
    private static Map<SubjectId, Boolean> membershipMap(Collection<SubjectId> ids) {
        var result = new LinkedHashMap<SubjectId, Boolean>(); ids.forEach(id -> result.put(id, true));
        return ImmutableLookupMap.copyOf(result);
    }
    private static void validateIntent(Inputs input, PhysicalIntentId key, PhysicalIntent intent) {
        var bootstrap = input.bootstrap(); var inventory = input.inventory(); var hiveColony = input.hiveColony();
        var humanPopulation = input.humanPopulation(); var productionJobs = input.productionJobs(); var serviceWorks = input.serviceWorks();
        var strategicPlans = input.strategicPlans(); var routeConstructions = input.routeConstructions(); var routeMaintenances = input.routeMaintenances();
        var resourceSites = input.resourceSites(); var fieldReferences = input.fieldReferences();
        var expectedActors = input.expectedActors(); var expectedStructures = input.expectedStructures(); var expectedSettlements = input.expectedSettlements();
        var fieldSubjects = fieldReferences.subjects();
        if (!key.equals(intent.id())) throw new IllegalArgumentException("physical intent map key must match intent identity");
        boolean preparedConflictCustody = ResourceSitePhysicalIntentStateSupport.ownsPreparedConflictIntent(resourceSites, intent);
        if ((intent.status() != PhysicalIntentStatus.CONFIRMED && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && !preparedConflictCustody)
                && !expectedActors.contains(intent.causeSubjectId()) && !inventory.cargo().containsKey(intent.causeSubjectId()) && !hiveColony.growthJobs().containsKey(intent.causeSubjectId())
                && !humanPopulation.birthJobs().containsKey(intent.causeSubjectId())
                && !humanPopulation.medicalOperations().containsKey(intent.causeSubjectId())
                && !humanPopulation.provisions().containsKey(intent.causeSubjectId())
                && !expectedStructures.contains(intent.causeSubjectId()) && !expectedSettlements.contains(intent.causeSubjectId())
                && !productionJobs.containsKey(intent.causeSubjectId())
                && !serviceWorks.containsKey(intent.causeSubjectId())
                && !FrontierWorldStateSupport.isHiveOrgan(bootstrap, hiveColony, intent.causeSubjectId())
                && !strategicPlans.settlementAssaults().containsKey(intent.causeSubjectId()) && !SceneStrikeStateSupport.isSettlementAssaultCause(strategicPlans, intent)
                && !bootstrap.hive().id().equals(intent.causeSubjectId())
                && !FrontierRouteNetwork.OWNER.equals(intent.causeSubjectId()) && !routeConstructions.containsKey(intent.causeSubjectId())
                && !RouteMaintenanceStateSupport.ownsMaintenance(routeMaintenances, intent.causeSubjectId()) && !fieldSubjects.contains(intent.causeSubjectId())) {
            throw new IllegalArgumentException("physical intent cause must be a canonical subject");
        }
        for (var subjectEntry : intent.roles().namedRoles().entrySet()) {
            SubjectId subject = subjectEntry.getValue();
            if (intent.status() == PhysicalIntentStatus.CONFIRMED || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || preparedConflictCustody) continue;
            RouteConstruction routeConstruction = routeConstructions.get(intent.causeSubjectId());
            boolean hiveNutrientSubject = HiveNutrientTransferStateSupport.ownsIntentSubject(hiveColony, intent, subject);
            boolean reservedRouteConstructionCargo = intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING && routeConstruction != null && routeConstruction.cargoId().isEmpty()
                    && (subject.equals(routeConstruction.plannedCargoId())
                    || subject.equals(routeConstruction.plannedCargoItemId()));
            boolean reservedRouteMaintenanceCargo = RouteMaintenanceStateSupport.reservesSubject(routeMaintenances, intent, subject);
            boolean productionResourceSubject = intent.roles().schema() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema.PRODUCTION_RESOURCES
                    && (subject.equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.CUSTODY_ACCOUNT))
                    && inventory.fungibleResources().accounts().containsKey(subject)
                    || subject.equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.RESOURCE_CONTAINER))
                    && inventory.containers().containsKey(subject));
            boolean declaredResourceContainer = subjectEntry.getKey() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.RESOURCE_CONTAINER
                    && inventory.containers().containsKey(subject);
            boolean declaredGuardDuty = subjectEntry.getKey() == PhysicalIntentSubjectRole.GUARD_DUTY && strategicPlans.guards().containsKey(subject);
            if (!declaredGuardDuty && !declaredResourceContainer && !productionResourceSubject && !hiveNutrientSubject && !expectedActors.contains(subject) && !inventory.cargo().containsKey(subject) &&
                    !expectedStructures.contains(subject) && !expectedSettlements.contains(subject)
                    && !inventory.items().containsKey(subject)
                    && !hiveColony.growthJobs().containsKey(subject) && !humanPopulation.birthJobs().containsKey(subject) && !productionJobs.containsKey(subject)
                    && !inventory.fungibleResources().lots().containsKey(subject) && !inventory.fungibleResources().claims().containsKey(subject)
                    && !humanPopulation.provisions().containsKey(subject) && !humanPopulation.medicalOperations().containsKey(subject)
                    && !serviceWorks.containsKey(subject)
                    && !outputSubjects(input).containsKey(subject)
                    && !FrontierWorldStateSupport.isHiveOrgan(bootstrap, hiveColony, subject)
                    && !FrontierRouteNetwork.OWNER.equals(subject) && !routeConstructions.containsKey(subject) && !RouteMaintenanceStateSupport.ownsMaintenance(routeMaintenances, subject)
                    && !strategicPlans.settlementAssaults().containsKey(subject)
                    && !reservedRouteConstructionCargo && !reservedRouteMaintenanceCargo && !fieldSubjects.contains(subject)) {
                throw new IllegalArgumentException("physical intent references an unknown canonical subject");
            }
        }
    }
}
