package io.farfrontier.palemirror.frontier.v3.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable, exact request for one external physical action. Its executor may run only after the
 * containing transaction is durable and must resolve the stated postcondition afterward.
 */
public record PhysicalIntent(
        PhysicalIntentId id,
        PhysicalIntentKind kind,
        PhysicalIntentStatus status,
        SubjectId causeSubjectId,
        List<SubjectId> subjectIds,
        FixedPosition origin,
        int radiusBlocks,
        PhysicalPostcondition postcondition,
        Optional<PhysicalObservationId> postconditionObservationId,
        Optional<PhysicalContainerSlot> targetSlot
) {
    public PhysicalIntent {
        Objects.requireNonNull(id, "physical intent id");
        Objects.requireNonNull(kind, "physical intent kind");
        Objects.requireNonNull(status, "physical intent status");
        Objects.requireNonNull(causeSubjectId, "physical intent cause subject");
        subjectIds = List.copyOf(subjectIds);
        Objects.requireNonNull(origin, "physical intent origin");
        Objects.requireNonNull(postcondition, "physical intent postcondition");
        postconditionObservationId = Objects.requireNonNull(postconditionObservationId, "physical intent postcondition observation");
        targetSlot = Objects.requireNonNull(targetSlot, "physical intent target slot");
        if (subjectIds.isEmpty() || subjectIds.size() > 32 || subjectIds.stream().distinct().count() != subjectIds.size()) {
            throw new IllegalArgumentException("physical intent must name one to thirty-two distinct subjects");
        }
        if (radiusBlocks < 0 || radiusBlocks > 64) throw new IllegalArgumentException("physical intent radius must be 0..64 blocks");
        switch (kind) {
            case CARGO_HANDOFF -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.CARGO_HANDOFF_OBSERVED) {
                    throw new IllegalArgumentException("cargo hand-off must use zero radius and cargo postcondition");
                }
            }
            case STRUCTURAL_REPAIR -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED) {
                    throw new IllegalArgumentException("structural repair must use zero radius and repair postcondition");
                }
            }
            case EXPLOSION -> {
                if (radiusBlocks == 0 || postcondition != PhysicalPostcondition.EXPLOSION_OBSERVED) {
                    throw new IllegalArgumentException("explosion must use positive radius and explosion postcondition");
                }
            }
            case SCENE_STRIKE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.SCENE_STRIKE_OBSERVED || subjectIds.size() != 2) {
                    throw new IllegalArgumentException("scene strike must bind one exact attacker and target without an area radius");
                }
            }
            case EXACT_ITEM_CONSUMPTION -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED || subjectIds.size() != 2) {
                    throw new IllegalArgumentException("exact item consumption must bind one owner and one exact stack without an area radius");
                }
            }
            case RESOURCE_SITE_PREPARATION -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED || subjectIds.size() != 2) {
                    throw new IllegalArgumentException("resource-site preparation must bind one site and one job without an area radius");
                }
            }
            case RESOURCE_SITE_HARVEST -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED || subjectIds.size() != 4) {
                    throw new IllegalArgumentException("resource-site harvest must bind one site, job, worker and output without an area radius");
                }
            }
            case PRODUCTION_TRANSFORMATION -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED || subjectIds.size() != 3) {
                    throw new IllegalArgumentException("production transformation must bind one job, input and output without an area radius");
                }
            }
            case CARGO_LOADING -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.CARGO_LOADED_FROM_DEPOT_OBSERVED || subjectIds.size() != 3) {
                    throw new IllegalArgumentException("cargo loading must bind contract, cargo and exact depot stack without an area radius");
                }
            }
            case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.ROUTE_CONSTRUCTION_MATERIAL_LOADED_OBSERVED || subjectIds.size() != 5) {
                    throw new IllegalArgumentException("route construction material loading must bind route, project, cargo, cargo item and source stack without an area radius");
                }
            }
            case ROUTE_MAINTENANCE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.ROUTE_MAINTENANCE_OBSERVED || subjectIds.size() != 4) {
                    throw new IllegalArgumentException("route maintenance must bind route, operation, cargo and exact material without an area radius");
                }
            }
            case ROUTE_MAINTENANCE_MATERIAL_LOADING -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.ROUTE_MAINTENANCE_MATERIAL_LOADED_OBSERVED || subjectIds.size() != 5) {
                    throw new IllegalArgumentException("route maintenance material loading must bind route, operation, cargo, cargo item and source stack without an area radius");
                }
            }
            case HIVE_NUTRIENT_DEPARTURE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.HIVE_NUTRIENT_DEPARTED_OBSERVED || subjectIds.size() != 3) {
                    throw new IllegalArgumentException("hive nutrient departure must bind transfer, cargo and exact source stack without an area radius");
                }
            }
            case HIVE_NUTRIENT_ARRIVAL -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.HIVE_NUTRIENT_ARRIVED_OBSERVED || subjectIds.size() != 3) {
                    throw new IllegalArgumentException("hive nutrient arrival must bind transfer, cargo and exact target stack without an area radius");
                }
            }
            case EQUIPMENT_ISSUE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED || subjectIds.size() != 3) {
                    throw new IllegalArgumentException("equipment issue must bind assault, defender and exact stack without an area radius");
                }
            }
            case EQUIPMENT_RETURN -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED || subjectIds.size() != 3 || targetSlot.isEmpty()) {
                    throw new IllegalArgumentException("equipment return must bind assault, defender, exact stack and typed target slot without an area radius");
                }
            }
        }
        if (kind != PhysicalIntentKind.EQUIPMENT_RETURN && targetSlot.isPresent()) {
            throw new IllegalArgumentException("only equipment return may retain a typed target slot");
        }
        if (status == PhysicalIntentStatus.CONFIRMED != postconditionObservationId.isPresent()) {
            throw new IllegalArgumentException("only confirmed physical intent has an observed postcondition");
        }
    }

    public PhysicalIntent(PhysicalIntentId id, PhysicalIntentKind kind, PhysicalIntentStatus status, SubjectId causeSubjectId,
                          List<SubjectId> subjectIds, FixedPosition origin, int radiusBlocks, PhysicalPostcondition postcondition) {
        this(id, kind, status, causeSubjectId, subjectIds, origin, radiusBlocks, postcondition, Optional.empty(), Optional.empty());
    }

    public PhysicalIntent(PhysicalIntentId id, PhysicalIntentKind kind, PhysicalIntentStatus status, SubjectId causeSubjectId,
                          List<SubjectId> subjectIds, FixedPosition origin, int radiusBlocks, PhysicalPostcondition postcondition,
                          PhysicalContainerSlot targetSlot) {
        this(id, kind, status, causeSubjectId, subjectIds, origin, radiusBlocks, postcondition, Optional.empty(), Optional.of(targetSlot));
    }

    public PhysicalIntent withStatus(PhysicalIntentStatus nextStatus, Optional<PhysicalObservationId> observationId) {
        return new PhysicalIntent(id, kind, nextStatus, causeSubjectId, subjectIds, origin, radiusBlocks, postcondition, observationId, targetSlot);
    }
}
