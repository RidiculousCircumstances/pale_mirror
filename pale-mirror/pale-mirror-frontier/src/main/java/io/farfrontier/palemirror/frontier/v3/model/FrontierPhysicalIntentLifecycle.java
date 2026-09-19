package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Closed physical-intent lifecycle composition.  The world aggregate owns immutable state only;
 * this registry selects exactly one declared family owner.  It deliberately contains no
 * intent-kind routing table: a family owner owns its own predicates and transition semantics.
 */
public final class FrontierPhysicalIntentLifecycle {
    private FrontierPhysicalIntentLifecycle() { }

    @FunctionalInterface private interface Preparation { void validate(FrontierWorldState state, PhysicalIntent intent); }
    @FunctionalInterface private interface Transition {
        FrontierWorldState apply(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                 Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next);
    }

    private record Owner(FrontierDurationProcessDriverRegistry.Family family,
                         java.util.function.BiPredicate<FrontierWorldState, PhysicalIntent> owns,
                         Preparation preparation, Transition transition) {
        private Owner {
            family = Objects.requireNonNull(family, "physical lifecycle owner family");
            owns = Objects.requireNonNull(owns, "physical lifecycle owner predicate");
            preparation = Objects.requireNonNull(preparation, "physical lifecycle owner preparation");
            transition = Objects.requireNonNull(transition, "physical lifecycle owner transition");
        }
    }

    private static final List<Owner> OWNERS = owners();

    /** Runtime composition fence: a descriptor cannot claim lifecycle ownership without one executable owner registration. */
    public static void requireComposition(java.util.Set<FrontierDurationProcessDriverRegistry.Family> families) {
        java.util.Set<FrontierDurationProcessDriverRegistry.Family> registered = OWNERS.stream().map(Owner::family)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!registered.equals(java.util.Set.copyOf(families))) {
            throw new IllegalStateException("physical lifecycle owner inventory differs from execution-boundary inventory");
        }
    }

    static FrontierWorldState prepare(FrontierWorldState state, PhysicalIntent intent) {
        Owner owner = owner(state, intent);
        owner.preparation().validate(state, intent);
        Map<PhysicalIntentId, PhysicalIntent> next = new LinkedHashMap<>(state.physicalIntents());
        next.put(intent.id(), intent);
        FrontierWorldState prepared = basic(state, next, state.physicalObservations());
        return prepared.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                FencedRecoveryPhysicalIntentSupport.prepared(state.fencedRecovery(), intent)));
    }

    static FrontierWorldState transition(FrontierWorldState state, PhysicalIntentId intentId, PhysicalIntentStatus nextStatus,
                                         Optional<PhysicalEffectObservation> observation) {
        PhysicalIntent current = state.physicalIntents().get(Objects.requireNonNull(intentId, "physical intent id"));
        if (current == null) throw new IllegalArgumentException("unknown physical intent: " + intentId.value());
        Owner owner = owner(state, current);
        if (!allowed(current.status(), nextStatus)) throw new IllegalArgumentException("physical intent transition is not allowed: "
                + current.id().value() + " kind=" + current.kind() + " " + current.status() + "->" + nextStatus);
        Map<PhysicalIntentId, PhysicalIntent> next = new LinkedHashMap<>(state.physicalIntents());
        return owner.transition().apply(state, current, nextStatus, observation, next);
    }

    private static boolean allowed(PhysicalIntentStatus current, PhysicalIntentStatus next) {
        return current == PhysicalIntentStatus.PREPARED && (next == PhysicalIntentStatus.RUNNING
                || next == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || next == PhysicalIntentStatus.CONFLICTED)
                || current == PhysicalIntentStatus.RUNNING && (next == PhysicalIntentStatus.CONFIRMED
                || next == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || next == PhysicalIntentStatus.CONFLICTED)
                || current == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && next == PhysicalIntentStatus.CONFIRMED;
    }


    private static FrontierWorldState plainTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                       Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        return confirmCargoHandoff(state, intent, observation, next);
    }

    private static FrontierWorldState productionTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                            Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return ProductionTransformationStateSupport.unknown(state, intent, marked(intent, status, next));
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        if (!(evidence(state, intent, observation) instanceof ProductionTransformationObservation receipt)) {
            throw new IllegalArgumentException("production transformation requires exact physical receipt");
        }
        return ProductionTransformationStateSupport.complete(state, intent, receipt, next);
    }

    private static FrontierWorldState resourceSiteTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                             Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return ResourceSitePhysicalIntentStateSupport.conflict(state, intent, marked(intent, status, next));
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        PhysicalEffectObservation receipt = evidence(state, intent, observation);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) {
            if (!(receipt instanceof ResourceSitePreparationObservation preparation)) throw new IllegalArgumentException("resource-site preparation requires exact field evidence");
            return ResourceSitePhysicalIntentStateSupport.complete(state, intent, preparation, next);
        }
        if (!(receipt instanceof ResourceSiteHarvestObservation harvest)) throw new IllegalArgumentException("resource-site harvest requires exact field and output evidence");
        return ResourceSitePhysicalIntentStateSupport.completeHarvest(state, intent, harvest, next);
    }

    private static FrontierWorldState routeConstructionTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                                   Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return RouteConstructionStateSupport.conflict(state, intent, marked(intent, status, next));
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        PhysicalEffectObservation receipt = evidence(state, intent, observation);
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION) {
            if (!(receipt instanceof RouteConstructionObservation construction)) throw new IllegalArgumentException("route construction requires construction observation evidence");
            return RouteConstructionStateSupport.complete(state, intent, construction, next);
        }
        if (!(receipt instanceof RouteConstructionMaterialLoadObservation loading)) throw new IllegalArgumentException("route construction material loading requires exact pickup evidence");
        RouteConstructionStateSupport.validateMaterialLoadingReceipt(state, intent, loading);
        return observed(state, intent, loading, next);
    }

    private static FrontierWorldState routeMaintenanceTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                                  Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return RouteMaintenanceStateSupport.conflict(state, intent, marked(intent, status, next));
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        return RouteMaintenanceStateSupport.confirm(state, intent, evidence(state, intent, observation), next);
    }

    private static FrontierWorldState hiveEndpointTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                              Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return HiveNutrientTransferStateSupport.unknownEndpoint(state, intent, marked(intent, status, next));
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        return HiveNutrientTransferStateSupport.completeEndpoint(state, intent, evidence(state, intent, observation), next);
    }

    private static FrontierWorldState decontaminationTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                                 Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && SettlementServiceDecontaminationStateSupport.owns(state, intent)) {
            return SettlementServiceDecontaminationStateSupport.unknown(state, intent, marked(intent, status, next));
        }
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        return SettlementServiceDecontaminationStateSupport.complete(state, intent, evidence(state, intent, observation), next);
    }

    private static FrontierWorldState structuralRepairTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                                  Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        if (!(evidence(state, intent, observation) instanceof StructuralRepairObservation repair)) throw new IllegalArgumentException("structural repair requires repair observation evidence");
        return StructuralRepairStateSupport.complete(state, intent, repair, next);
    }

    private static FrontierWorldState sceneStrikeTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                             Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        if (!(evidence(state, intent, observation) instanceof SceneStrikeObservation strike)) throw new IllegalArgumentException("scene strike requires exact hit evidence");
        SceneStrikeStateSupport.validateObservation(state, intent, strike);
        FrontierWorldState confirmed = observed(state, intent, strike, next);
        if (strike.targetHealthAfter().compareTo(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO) > 0) {
            Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(confirmed.actorLocations());
            ActorLocation target = actors.get(strike.targetId());
            actors.put(strike.targetId(), new ActorLocation(target.body(), target.condition().withHealth(strike.targetHealthAfter())));
            confirmed = confirmed.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        }
        return confirmed.withStrategicPlans(confirmed.strategicPlans().afterConfirmedHotStrike(intent));
    }

    private static FrontierWorldState explosionTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                           Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        if (!(evidence(state, intent, observation) instanceof ExplosionObservation explosion)) throw new IllegalArgumentException("explosion requires post-impact observation evidence");
        return ExplosionStateSupport.complete(state, intent, explosion, next);
    }

    private static FrontierWorldState exactConsumptionTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                                  Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        PhysicalEffectObservation receipt = evidence(state, intent, observation);
        FrontierWorldState fungible = FungiblePhysicalIntentConfirmationStateSupport.complete(state, intent, receipt, next,
                intent.id(), PhysicalIntentStatus.CONFIRMED);
        if (fungible != null) return fungible;
        if (!(receipt instanceof ExactItemConsumedObservation consumed)) throw new IllegalArgumentException("exact consumption requires item observation evidence");
        SubjectId itemId = intent.subjectIds().stream().filter(id -> !id.equals(intent.causeSubjectId())).findFirst().orElseThrow();
        ExactItemStack item = state.inventory().items().get(itemId);
        if (!itemId.equals(consumed.itemId()) || item == null || item.count() != consumed.countBefore()
                || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("exact consumption receipt does not match current stack");
        ExactItemConsumptionStateSupport.Claim claim = ExactItemConsumptionStateSupport.claim(state, intent);
        if (!claim.item().equals(item) || !claim.containerId().equals(slot.containerId()) || claim.slot() != slot.slot() || claim.count() != consumed.consumedCount()) {
            throw new IllegalArgumentException("exact consumption stack is not in an active owner container");
        }
        next.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, Optional.of(consumed.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(consumed.id(), consumed);
        HiveColony colony = state.hiveColony().growthJobs().containsKey(intent.causeSubjectId())
                ? state.hiveColony().consumeTransferredNutrient(intent.causeSubjectId(), itemId) : state.hiveColony();
        FrontierWorldState consumedState = state.next(state.actorLocations(), state.structureConditions(), state.infection(),
                state.inventory().consume(itemId, consumed.consumedCount()), state.productionJobs(), state.contracts(), state.operations(),
                next, observations, state.sceneLeases(), colony, state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
        if (consumedState.humanPopulation().provisions().values().stream().anyMatch(provision -> provision.activeIntentId().filter(intent.id()::equals).isPresent())) {
            return SettlementProvisionStateSupport.reducePhysicalConsumptionAfterInventory(consumedState, intent, consumed);
        }
        return consumedState;
    }

    private static FrontierWorldState cargoLoadingTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                              Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        return CargoLoadingStateSupport.complete(state, intent, evidence(state, intent, observation), next);
    }

    private static FrontierWorldState serviceInputTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                              Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        return SettlementServiceInputIssueStateSupport.complete(state, intent,
                SettlementServiceInputIssueStateSupport.requireReceipt(evidence(state, intent, observation)), next);
    }

    private static FrontierWorldState equipmentTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                           Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        if (status != PhysicalIntentStatus.CONFIRMED) return unconfirmed(state, intent, status, next);
        return HumanEquipmentStateSupport.complete(state, intent, evidence(state, intent, observation), next);
    }

    private static FrontierWorldState confirmCargoHandoff(FrontierWorldState state, PhysicalIntent intent,
                                                           Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next) {
        FrontierWorldState fungible = FungiblePhysicalIntentConfirmationStateSupport.complete(state, intent, evidence(state, intent, observation), next,
                intent.id(), PhysicalIntentStatus.CONFIRMED);
        return fungible != null ? fungible : CargoHandoffConfirmationStateSupport.complete(state, intent, evidence(state, intent, observation), next,
                intent.id(), PhysicalIntentStatus.CONFIRMED);
    }

    private static FrontierWorldState unconfirmed(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                                   Map<PhysicalIntentId, PhysicalIntent> next) {
        return basic(state, marked(intent, status, next), state.physicalObservations());
    }

    private static Map<PhysicalIntentId, PhysicalIntent> marked(PhysicalIntent intent, PhysicalIntentStatus status,
                                                                 Map<PhysicalIntentId, PhysicalIntent> next) {
        next.put(intent.id(), intent.withStatus(status, Optional.empty()));
        return next;
    }

    private static PhysicalEffectObservation evidence(FrontierWorldState state, PhysicalIntent intent,
                                                      Optional<PhysicalEffectObservation> observation) {
        PhysicalEffectObservation evidence = observation.orElseThrow(() -> new IllegalArgumentException("confirmed physical intent requires observation evidence"));
        if (!intent.id().equals(evidence.intentId()) || state.physicalObservations().containsKey(evidence.id())) {
            throw new IllegalArgumentException("physical observation does not match a unique confirmed intent");
        }
        return evidence;
    }

    private static FrontierWorldState observed(FrontierWorldState state, PhysicalIntent intent, PhysicalEffectObservation evidence,
                                               Map<PhysicalIntentId, PhysicalIntent> next) {
        next.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, Optional.of(evidence.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(evidence.id(), evidence);
        return basic(state, next, observations);
    }

    private static FrontierWorldState basic(FrontierWorldState state, Map<PhysicalIntentId, PhysicalIntent> intents,
                                            Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), intents, observations, state.sceneLeases(), state.hiveColony(), state.structureDamage(),
                state.physicalDeltas(), state.ambientLeases());
    }

    private static Owner owner(FrontierWorldState state, PhysicalIntent intent) {
        List<Owner> matches = OWNERS.stream().filter(candidate -> candidate.owns().test(state, intent)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("physical intent must have exactly one registered family owner: "
                + intent.id().value() + " matches=" + matches.stream().map(Owner::family).toList());
        Owner owner = matches.getFirst();
        FrontierDurationProcessDriverRegistry.executionBoundary(owner.family());
        return owner;
    }

    /** These are executable registrations.  A no-physical family remains visible but cannot claim an intent. */
    private static List<Owner> owners() {
        List<Owner> values = new ArrayList<>();
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_PREPARATION,
                kind(PhysicalIntentKind.RESOURCE_SITE_PREPARATION), ResourceSitePhysicalIntentStateSupport::validateIntent,
                FrontierPhysicalIntentLifecycle::resourceSiteTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                kind(PhysicalIntentKind.RESOURCE_SITE_HARVEST), ResourceSitePhysicalIntentStateSupport::validateIntent,
                FrontierPhysicalIntentLifecycle::resourceSiteTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.PRODUCTION_WORK,
                kind(PhysicalIntentKind.PRODUCTION_TRANSFORMATION), ProductionTransformationStateSupport::validateIntent,
                FrontierPhysicalIntentLifecycle::productionTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.SETTLEMENT_SERVICE_WORK,
                any(PhysicalIntentKind.DECONTAMINATION, PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE),
                (state, intent) -> { if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) SettlementServiceDecontaminationStateSupport.owns(state, intent); else SettlementServiceInputIssueStateSupport.validateIntent(state, intent); },
                (state, intent, status, observation, next) -> intent.kind() == PhysicalIntentKind.DECONTAMINATION
                        ? decontaminationTransition(state, intent, status, observation, next) : serviceInputTransition(state, intent, status, observation, next)));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.ROUTE_OPERATION,
                any(PhysicalIntentKind.CARGO_LOADING, PhysicalIntentKind.CARGO_HANDOFF), (state, intent) -> { if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) CargoLoadingStateSupport.validateIntent(state, intent); },
                (state, intent, status, observation, next) -> intent.kind() == PhysicalIntentKind.CARGO_LOADING
                        ? cargoLoadingTransition(state, intent, status, observation, next) : plainTransition(state, intent, status, observation, next)));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.ROUTE_PATROL, never(), noop(), FrontierPhysicalIntentLifecycle::plainTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.HIVE_MOBILIZATION,
                kind(PhysicalIntentKind.EXPLOSION), (state, intent) -> ExplosionStateSupport.validateIntent(state, intent), FrontierPhysicalIntentLifecycle::explosionTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.ROUTE_ENGAGEMENT,
                (state, intent) -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE && !SceneStrikeStateSupport.isSettlementAssaultCause(state.strategicPlans(), intent),
                SceneStrikeStateSupport::validateIntent, FrontierPhysicalIntentLifecycle::sceneStrikeTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.SETTLEMENT_ASSAULT,
                (state, intent) -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE && SceneStrikeStateSupport.isSettlementAssaultCause(state.strategicPlans(), intent)
                        || (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                        && !state.routeConstructions().containsKey(intent.causeSubjectId()) && !state.routeMaintenances().containsKey(intent.causeSubjectId()),
                (state, intent) -> { if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE) SceneStrikeStateSupport.validateIntent(state, intent); else HumanEquipmentStateSupport.validateIntent(state, intent); },
                (state, intent, status, observation, next) -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE ? sceneStrikeTransition(state, intent, status, observation, next) : equipmentTransition(state, intent, status, observation, next)));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.POPULATION_MIGRATION,
                (state, intent) -> intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION && state.humanPopulation().birthJobs().containsKey(intent.causeSubjectId()), noop(), FrontierPhysicalIntentLifecycle::exactConsumptionTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.MEDICAL_TREATMENT,
                (state, intent) -> intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION && state.humanPopulation().medicalOperations().containsKey(intent.causeSubjectId()), noop(), FrontierPhysicalIntentLifecycle::exactConsumptionTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.ENGINEERING_WORKSITE,
                (state, intent) -> any(PhysicalIntentKind.ROUTE_CONSTRUCTION, PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING,
                        PhysicalIntentKind.ROUTE_MAINTENANCE, PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING, PhysicalIntentKind.STRUCTURAL_REPAIR).test(state, intent)
                        || (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                        && (state.routeConstructions().containsKey(intent.causeSubjectId()) || state.routeMaintenances().containsKey(intent.causeSubjectId())),
                (state, intent) -> { if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR) return; if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) RouteConstructionStateSupport.validateMaterialLoadingIntent(state, intent); else if (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN) HumanEquipmentStateSupport.validateIntent(state, intent); },
                (state, intent, status, observation, next) -> switch (intent.kind()) { case ROUTE_CONSTRUCTION, ROUTE_CONSTRUCTION_MATERIAL_LOADING -> routeConstructionTransition(state, intent, status, observation, next); case ROUTE_MAINTENANCE, ROUTE_MAINTENANCE_MATERIAL_LOADING -> routeMaintenanceTransition(state, intent, status, observation, next); case STRUCTURAL_REPAIR -> structuralRepairTransition(state, intent, status, observation, next); default -> equipmentTransition(state, intent, status, observation, next); }));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.HIVE_GROWTH,
                (state, intent) -> intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION && state.hiveColony().growthJobs().containsKey(intent.causeSubjectId()), noop(), FrontierPhysicalIntentLifecycle::exactConsumptionTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.HIVE_NUTRIENT_TRANSFER,
                any(PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE, PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL), noop(), FrontierPhysicalIntentLifecycle::hiveEndpointTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.SETTLEMENT_PROVISION,
                (state, intent) -> intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION && state.humanPopulation().provisions().containsKey(intent.causeSubjectId()), noop(), FrontierPhysicalIntentLifecycle::exactConsumptionTransition));
        values.add(owner(FrontierDurationProcessDriverRegistry.Family.AMBIENT_ACTOR_CUSTODY, never(), noop(), FrontierPhysicalIntentLifecycle::plainTransition));
        if (values.stream().map(Owner::family).collect(java.util.stream.Collectors.toSet()).size() != FrontierDurationProcessDriverRegistry.Family.values().length) {
            throw new IllegalStateException("duplicate or missing physical lifecycle family registration");
        }
        return List.copyOf(values);
    }

    private static Owner owner(FrontierDurationProcessDriverRegistry.Family family, java.util.function.BiPredicate<FrontierWorldState, PhysicalIntent> owns,
                               Preparation preparation, Transition transition) { return new Owner(family, owns, preparation, transition); }
    private static java.util.function.BiPredicate<FrontierWorldState, PhysicalIntent> kind(PhysicalIntentKind kind) { return (state, intent) -> intent.kind() == kind; }
    private static java.util.function.BiPredicate<FrontierWorldState, PhysicalIntent> any(PhysicalIntentKind... kinds) { return (state, intent) -> java.util.Set.of(kinds).contains(intent.kind()); }
    private static java.util.function.BiPredicate<FrontierWorldState, PhysicalIntent> never() { return (state, intent) -> false; }
    private static Preparation noop() { return (state, intent) -> { }; }
}
