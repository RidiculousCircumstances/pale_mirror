package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple;

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
        PhysicalIntentRoleBinding roles,
        FixedPosition origin,
        int radiusBlocks,
        PhysicalPostcondition postcondition,
        Optional<PhysicalObservationId> postconditionObservationId,
        Optional<PhysicalContainerSlot> targetSlot,
        Optional<PhysicalDeltaSemanticTarget> semanticTarget,
        PhysicalIntentLifecycleOwner lifecycleOwner,
        Optional<DiagnosticTuple> diagnostic
) {
    public PhysicalIntent {
        Objects.requireNonNull(id, "physical intent id");
        Objects.requireNonNull(kind, "physical intent kind");
        Objects.requireNonNull(status, "physical intent status");
        Objects.requireNonNull(causeSubjectId, "physical intent cause subject");
        roles = Objects.requireNonNull(roles, "physical intent roles");
        Objects.requireNonNull(origin, "physical intent origin");
        Objects.requireNonNull(postcondition, "physical intent postcondition");
        postconditionObservationId = Objects.requireNonNull(postconditionObservationId, "physical intent postcondition observation");
        targetSlot = Objects.requireNonNull(targetSlot, "physical intent target slot");
        semanticTarget = Objects.requireNonNull(semanticTarget, "physical intent semantic target");
        lifecycleOwner = Objects.requireNonNull(lifecycleOwner, "physical intent lifecycle owner");
        diagnostic = Objects.requireNonNull(diagnostic, "physical intent diagnostic");
        roles.validate(lifecycleOwner, kind);
        if (radiusBlocks < 0 || radiusBlocks > 64) throw new IllegalArgumentException("physical intent radius must be 0..64 blocks");
        switch (kind) {
            case CARGO_HANDOFF -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.CARGO_HANDOFF_OBSERVED) {
                    throw new IllegalArgumentException("cargo hand-off must use zero radius and cargo postcondition");
                }
            }
            case STRUCTURAL_REPAIR -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED || semanticTarget.isEmpty()) {
                    throw new IllegalArgumentException("structural repair must use zero radius and repair postcondition");
                }
            }
            case EXPLOSION -> {
                if (radiusBlocks == 0 || postcondition != PhysicalPostcondition.EXPLOSION_OBSERVED) {
                    throw new IllegalArgumentException("explosion must use positive radius and explosion postcondition");
                }
            }
            case SCENE_STRIKE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.SCENE_STRIKE_OBSERVED) {
                    throw new IllegalArgumentException("scene strike must bind one exact attacker and target without an area radius");
                }
            }
            case EXACT_ITEM_CONSUMPTION -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED) {
                    throw new IllegalArgumentException("exact item consumption must bind one owner and one exact stack without an area radius");
                }
            }
            case RESOURCE_SITE_PREPARATION -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) {
                    throw new IllegalArgumentException("resource-site preparation must bind one site and one job without an area radius");
                }
            }
            case RESOURCE_SITE_HARVEST -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED) {
                    throw new IllegalArgumentException("resource-site harvest must bind one site, job, worker and output without an area radius");
                }
            }
            case PRODUCTION_TRANSFORMATION -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED) {
                    throw new IllegalArgumentException("production transformation must bind one job, input and output without an area radius");
                }
            }
            case CARGO_LOADING -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.CARGO_LOADED_FROM_DEPOT_OBSERVED) {
                    throw new IllegalArgumentException("cargo loading must bind contract, cargo and exact depot stack without an area radius");
                }
            }
            case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.ROUTE_CONSTRUCTION_MATERIAL_LOADED_OBSERVED) {
                    throw new IllegalArgumentException("route construction material loading must bind route, project, cargo, cargo item and source stack without an area radius");
                }
            }
            case ROUTE_MAINTENANCE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.ROUTE_MAINTENANCE_OBSERVED) {
                    throw new IllegalArgumentException("route maintenance must bind route, operation, cargo and exact material without an area radius");
                }
            }
            case ROUTE_MAINTENANCE_MATERIAL_LOADING -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.ROUTE_MAINTENANCE_MATERIAL_LOADED_OBSERVED) {
                    throw new IllegalArgumentException("route maintenance material loading must bind route, operation, cargo, cargo item and source stack without an area radius");
                }
            }
            case HIVE_NUTRIENT_DEPARTURE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.HIVE_NUTRIENT_DEPARTED_OBSERVED) {
                    throw new IllegalArgumentException("hive nutrient departure must bind transfer, cargo and exact source stack without an area radius");
                }
            }
            case HIVE_NUTRIENT_ARRIVAL -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.HIVE_NUTRIENT_ARRIVED_OBSERVED) {
                    throw new IllegalArgumentException("hive nutrient arrival must bind transfer, cargo and exact target stack without an area radius");
                }
            }
            case EQUIPMENT_ISSUE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED) {
                    throw new IllegalArgumentException("equipment issue must bind assault, defender and exact stack without an area radius");
                }
            }
            case EQUIPMENT_RETURN -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED || targetSlot.isEmpty()) {
                    throw new IllegalArgumentException("equipment return must bind assault, defender, exact stack and typed target slot without an area radius");
                }
            }
            case SETTLEMENT_SERVICE_INPUT_ISSUE -> {
                if (radiusBlocks != 0 || postcondition != PhysicalPostcondition.SETTLEMENT_SERVICE_INPUT_ISSUED_OBSERVED) {
                    throw new IllegalArgumentException("service input issue must bind work, worker and exact stack without an area radius");
                }
            }
        }
        if (kind != PhysicalIntentKind.EQUIPMENT_RETURN && targetSlot.isPresent()) {
            throw new IllegalArgumentException("only equipment return may retain a typed target slot");
        }
        if (kind != PhysicalIntentKind.STRUCTURAL_REPAIR && semanticTarget.isPresent()) {
            throw new IllegalArgumentException("only structural repair may retain a physical-delta semantic target");
        }
        if (status == PhysicalIntentStatus.CONFIRMED != postconditionObservationId.isPresent()) {
            throw new IllegalArgumentException("only confirmed physical intent has an observed postcondition");
        }
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            DiagnosticTuple retained = diagnostic.orElseThrow(() -> new IllegalArgumentException("recovery-unknown physical intent requires a stamped diagnostic tuple"));
            if (retained.reason() != io.farfrontier.palemirror.frontier.v3.model.DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED
                    || retained.owner().id().value().equals(id.value()) == false
                    || retained.subject().id().value().equals(id.value()) == false) {
                throw new IllegalArgumentException("recovery-unknown physical intent retains a foreign diagnostic tuple");
            }
        } else if (diagnostic.isPresent()) {
            throw new IllegalArgumentException("only recovery-unknown physical intent may retain a diagnostic tuple");
        }
    }

    public PhysicalIntent(PhysicalIntentId id, PhysicalIntentKind kind, PhysicalIntentStatus status, SubjectId causeSubjectId,
                          PhysicalIntentRoleBinding roles, FixedPosition origin, int radiusBlocks, PhysicalPostcondition postcondition,
                          PhysicalIntentLifecycleOwner lifecycleOwner) {
        this(id, kind, status, causeSubjectId, roles, origin, radiusBlocks, postcondition, Optional.empty(), Optional.empty(), Optional.empty(), lifecycleOwner, Optional.empty());
    }

    public PhysicalIntent(PhysicalIntentId id, PhysicalIntentKind kind, PhysicalIntentStatus status, SubjectId causeSubjectId,
                          PhysicalIntentRoleBinding roles, FixedPosition origin, int radiusBlocks, PhysicalPostcondition postcondition,
                          Optional<PhysicalObservationId> postconditionObservationId, Optional<PhysicalContainerSlot> targetSlot,
                          PhysicalIntentLifecycleOwner lifecycleOwner) {
        this(id, kind, status, causeSubjectId, roles, origin, radiusBlocks, postcondition, postconditionObservationId, targetSlot, Optional.empty(), lifecycleOwner, Optional.empty());
    }
    public PhysicalIntent(PhysicalIntentId id, PhysicalIntentKind kind, PhysicalIntentStatus status, SubjectId causeSubjectId,
                          PhysicalIntentRoleBinding roles, FixedPosition origin, int radiusBlocks, PhysicalPostcondition postcondition,
                          PhysicalContainerSlot targetSlot, PhysicalIntentLifecycleOwner lifecycleOwner) {
        this(id, kind, status, causeSubjectId, roles, origin, radiusBlocks, postcondition, Optional.empty(), Optional.of(targetSlot), Optional.empty(), lifecycleOwner, Optional.empty());
    }
    public PhysicalIntent(PhysicalIntentId id, PhysicalIntentKind kind, PhysicalIntentStatus status, SubjectId causeSubjectId,
                          PhysicalIntentRoleBinding roles, FixedPosition origin, int radiusBlocks, PhysicalPostcondition postcondition,
                          PhysicalDeltaSemanticTarget semanticTarget, PhysicalIntentLifecycleOwner lifecycleOwner) {
        this(id, kind, status, causeSubjectId, roles, origin, radiusBlocks, postcondition, Optional.empty(), Optional.empty(), Optional.of(semanticTarget), lifecycleOwner, Optional.empty());
    }

    public PhysicalIntent(PhysicalIntentId id, PhysicalIntentKind kind, PhysicalIntentStatus status, SubjectId causeSubjectId,
                          PhysicalIntentRoleBinding roles, FixedPosition origin, int radiusBlocks, PhysicalPostcondition postcondition,
                          Optional<PhysicalObservationId> postconditionObservationId, Optional<PhysicalContainerSlot> targetSlot,
                          Optional<PhysicalDeltaSemanticTarget> semanticTarget, PhysicalIntentLifecycleOwner lifecycleOwner) {
        this(id, kind, status, causeSubjectId, roles, origin, radiusBlocks, postcondition, postconditionObservationId, targetSlot, semanticTarget, lifecycleOwner, Optional.empty());
    }

    public PhysicalIntent withStatus(PhysicalIntentStatus nextStatus, Optional<PhysicalObservationId> observationId) {
        return new PhysicalIntent(id, kind, nextStatus, causeSubjectId, roles, origin, radiusBlocks, postcondition, observationId, targetSlot, semanticTarget, lifecycleOwner, Optional.empty());
    }

    /** The boundary that retains restart ambiguity must supply its already-classified exact tuple. */
    public PhysicalIntent withRecoveryUnknown(DiagnosticTuple stampedDiagnostic) {
        return new PhysicalIntent(id, kind, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, causeSubjectId, roles, origin, radiusBlocks,
                postcondition, Optional.empty(), targetSlot, semanticTarget, lifecycleOwner, Optional.of(stampedDiagnostic));
    }

    /** Deterministic compatibility/index data. It is never semantic authority. */
    public java.util.List<SubjectId> subjectIds() { return roles.subjectIds(); }
}
