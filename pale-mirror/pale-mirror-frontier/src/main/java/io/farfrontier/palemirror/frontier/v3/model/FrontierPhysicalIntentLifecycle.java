package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Closed physical-intent lifecycle dispatch.  The world aggregate owns immutable state only;
 * this registry selects one already-existing family owner for preparation, ambiguity and
 * confirmation semantics.  It is deliberately not a second world database or executor.
 */
final class FrontierPhysicalIntentLifecycle {
    private FrontierPhysicalIntentLifecycle() { }

    @FunctionalInterface private interface Preparation { void validate(FrontierWorldState state, PhysicalIntent intent); }
    @FunctionalInterface private interface Transition {
        FrontierWorldState apply(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status,
                                 Optional<PhysicalEffectObservation> observation, Map<PhysicalIntentId, PhysicalIntent> next);
    }

    private static final Map<PhysicalIntentKind, Preparation> PREPARATIONS = preparations();
    private static final Map<PhysicalIntentKind, Transition> TRANSITIONS = transitions();

    static FrontierWorldState prepare(FrontierWorldState state, PhysicalIntent intent) {
        requireBoundary(state, intent);
        PREPARATIONS.get(intent.kind()).validate(state, intent);
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
        requireBoundary(state, current);
        if (!allowed(current.status(), nextStatus)) throw new IllegalArgumentException("physical intent transition is not allowed: "
                + current.id().value() + " kind=" + current.kind() + " " + current.status() + "->" + nextStatus);
        Map<PhysicalIntentId, PhysicalIntent> next = new LinkedHashMap<>(state.physicalIntents());
        return TRANSITIONS.get(current.kind()).apply(state, current, nextStatus, observation, next);
    }

    private static boolean allowed(PhysicalIntentStatus current, PhysicalIntentStatus next) {
        return current == PhysicalIntentStatus.PREPARED && (next == PhysicalIntentStatus.RUNNING
                || next == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || next == PhysicalIntentStatus.CONFLICTED)
                || current == PhysicalIntentStatus.RUNNING && (next == PhysicalIntentStatus.CONFIRMED
                || next == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || next == PhysicalIntentStatus.CONFLICTED)
                || current == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && next == PhysicalIntentStatus.CONFIRMED;
    }

    private static Map<PhysicalIntentKind, Preparation> preparations() {
        EnumMap<PhysicalIntentKind, Preparation> values = defaults((state, intent) -> { });
        values.put(PhysicalIntentKind.SCENE_STRIKE, SceneStrikeStateSupport::validateIntent);
        values.put(PhysicalIntentKind.RESOURCE_SITE_PREPARATION, ResourceSitePhysicalIntentStateSupport::validateIntent);
        values.put(PhysicalIntentKind.RESOURCE_SITE_HARVEST, ResourceSitePhysicalIntentStateSupport::validateIntent);
        values.put(PhysicalIntentKind.PRODUCTION_TRANSFORMATION, ProductionTransformationStateSupport::validateIntent);
        values.put(PhysicalIntentKind.CARGO_LOADING, CargoLoadingStateSupport::validateIntent);
        values.put(PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE, SettlementServiceInputIssueStateSupport::validateIntent);
        values.put(PhysicalIntentKind.EQUIPMENT_ISSUE, HumanEquipmentStateSupport::validateIntent);
        values.put(PhysicalIntentKind.EQUIPMENT_RETURN, HumanEquipmentStateSupport::validateIntent);
        return Map.copyOf(values);
    }

    private static Map<PhysicalIntentKind, Transition> transitions() {
        EnumMap<PhysicalIntentKind, Transition> values = defaults(FrontierPhysicalIntentLifecycle::plainTransition);
        values.put(PhysicalIntentKind.PRODUCTION_TRANSFORMATION, FrontierPhysicalIntentLifecycle::productionTransition);
        values.put(PhysicalIntentKind.RESOURCE_SITE_PREPARATION, FrontierPhysicalIntentLifecycle::resourceSiteTransition);
        values.put(PhysicalIntentKind.RESOURCE_SITE_HARVEST, FrontierPhysicalIntentLifecycle::resourceSiteTransition);
        values.put(PhysicalIntentKind.ROUTE_CONSTRUCTION, FrontierPhysicalIntentLifecycle::routeConstructionTransition);
        values.put(PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING, FrontierPhysicalIntentLifecycle::routeConstructionTransition);
        values.put(PhysicalIntentKind.ROUTE_MAINTENANCE, FrontierPhysicalIntentLifecycle::routeMaintenanceTransition);
        values.put(PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING, FrontierPhysicalIntentLifecycle::routeMaintenanceTransition);
        values.put(PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE, FrontierPhysicalIntentLifecycle::hiveEndpointTransition);
        values.put(PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL, FrontierPhysicalIntentLifecycle::hiveEndpointTransition);
        values.put(PhysicalIntentKind.DECONTAMINATION, FrontierPhysicalIntentLifecycle::decontaminationTransition);
        values.put(PhysicalIntentKind.STRUCTURAL_REPAIR, FrontierPhysicalIntentLifecycle::structuralRepairTransition);
        values.put(PhysicalIntentKind.SCENE_STRIKE, FrontierPhysicalIntentLifecycle::sceneStrikeTransition);
        values.put(PhysicalIntentKind.EXPLOSION, FrontierPhysicalIntentLifecycle::explosionTransition);
        values.put(PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, FrontierPhysicalIntentLifecycle::exactConsumptionTransition);
        values.put(PhysicalIntentKind.CARGO_LOADING, FrontierPhysicalIntentLifecycle::cargoLoadingTransition);
        values.put(PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE, FrontierPhysicalIntentLifecycle::serviceInputTransition);
        values.put(PhysicalIntentKind.EQUIPMENT_ISSUE, FrontierPhysicalIntentLifecycle::equipmentTransition);
        values.put(PhysicalIntentKind.EQUIPMENT_RETURN, FrontierPhysicalIntentLifecycle::equipmentTransition);
        return Map.copyOf(values);
    }

    private static <T> EnumMap<PhysicalIntentKind, T> defaults(T value) {
        EnumMap<PhysicalIntentKind, T> values = new EnumMap<>(PhysicalIntentKind.class);
        for (PhysicalIntentKind kind : PhysicalIntentKind.values()) values.put(kind, value);
        return values;
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

    private static void requireBoundary(FrontierWorldState state, PhysicalIntent intent) {
        FrontierDurationProcessDriverRegistry.executionBoundary(ownerFamily(state, intent));
    }

    private static FrontierDurationProcessDriverRegistry.Family ownerFamily(FrontierWorldState state, PhysicalIntent intent) {
        return switch (intent.kind()) {
            case RESOURCE_SITE_PREPARATION -> FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_PREPARATION;
            case RESOURCE_SITE_HARVEST -> FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST;
            case PRODUCTION_TRANSFORMATION -> FrontierDurationProcessDriverRegistry.Family.PRODUCTION_WORK;
            case SETTLEMENT_SERVICE_INPUT_ISSUE, DECONTAMINATION -> FrontierDurationProcessDriverRegistry.Family.SETTLEMENT_SERVICE_WORK;
            case ROUTE_CONSTRUCTION, ROUTE_CONSTRUCTION_MATERIAL_LOADING, ROUTE_MAINTENANCE, ROUTE_MAINTENANCE_MATERIAL_LOADING, STRUCTURAL_REPAIR -> FrontierDurationProcessDriverRegistry.Family.ENGINEERING_WORKSITE;
            case HIVE_NUTRIENT_DEPARTURE, HIVE_NUTRIENT_ARRIVAL -> FrontierDurationProcessDriverRegistry.Family.HIVE_NUTRIENT_TRANSFER;
            case CARGO_LOADING, CARGO_HANDOFF -> FrontierDurationProcessDriverRegistry.Family.ROUTE_OPERATION;
            case EXPLOSION -> FrontierDurationProcessDriverRegistry.Family.HIVE_MOBILIZATION;
            case SCENE_STRIKE -> FrontierDurationProcessDriverRegistry.Family.ROUTE_ENGAGEMENT;
            case EQUIPMENT_ISSUE, EQUIPMENT_RETURN -> FrontierDurationProcessDriverRegistry.Family.SETTLEMENT_ASSAULT;
            case EXACT_ITEM_CONSUMPTION -> exactConsumptionOwner(state, intent);
        };
    }

    private static FrontierDurationProcessDriverRegistry.Family exactConsumptionOwner(FrontierWorldState state, PhysicalIntent intent) {
        if (state.hiveColony().growthJobs().containsKey(intent.causeSubjectId())) return FrontierDurationProcessDriverRegistry.Family.HIVE_GROWTH;
        if (state.humanPopulation().medicalOperations().containsKey(intent.causeSubjectId())) return FrontierDurationProcessDriverRegistry.Family.MEDICAL_TREATMENT;
        if (state.humanPopulation().birthJobs().containsKey(intent.causeSubjectId())) return FrontierDurationProcessDriverRegistry.Family.POPULATION_MIGRATION;
        return FrontierDurationProcessDriverRegistry.Family.SETTLEMENT_PROVISION;
    }
}
