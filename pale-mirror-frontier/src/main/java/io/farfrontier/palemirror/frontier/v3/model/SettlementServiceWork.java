package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Sole durable owner of one settlement resident's duration-bearing service work.
 *
 * <p>The attached physical intent remains the sole irreversible effect owner. This aggregate
 * retains the person, station, input, target and cursor which make that effect eligible; it is
 * deliberately not a task projection, ambient role or physical executor scratch record.</p>
 */
public record SettlementServiceWork(
        SubjectId id,
        SettlementServiceWorkKind kind,
        SubjectId settlementId,
        SubjectId workerId,
        SubjectId facilityId,
        SurfaceAnchor station,
        SubjectId inputItemId,
        SettlementServiceTarget target,
        PhysicalIntentId intentId,
        TraversalTopology traversal,
        int traversalCursor,
        SettlementServiceWorkPhase phase,
        int completedWorkTicks
) {
    public static final int REQUIRED_WORK_TICKS = 80;
    public static final int MAX_RETAINED = 1_024;

    public SettlementServiceWork {
        id = Objects.requireNonNull(id, "service work id");
        kind = Objects.requireNonNull(kind, "service work kind");
        settlementId = Objects.requireNonNull(settlementId, "service work settlement");
        workerId = Objects.requireNonNull(workerId, "service work worker");
        facilityId = Objects.requireNonNull(facilityId, "service work facility");
        station = Objects.requireNonNull(station, "service work station");
        inputItemId = Objects.requireNonNull(inputItemId, "service work input");
        target = Objects.requireNonNull(target, "service work target");
        intentId = Objects.requireNonNull(intentId, "service work intent");
        traversal = Objects.requireNonNull(traversal, "service work traversal");
        phase = Objects.requireNonNull(phase, "service work phase");
        if (!id.value().startsWith("service:") || !settlementId.value().startsWith("settlement:")
                || !workerId.value().startsWith("resident:") || !facilityId.value().startsWith("structure:")
                || !traversal.provenance().equals(id) || traversal.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                || !edge.traversableBy(TraversalCapability.PEDESTRIAN)) || traversalCursor < 0
                || traversalCursor >= traversal.linearCorridorSurfaces().size()) {
            throw new IllegalArgumentException("service work must retain one exact pedestrian corridor and cursor");
        }
        if (!traversal.linearCorridorSurfaces().getLast().equals(station)) {
            throw new IllegalArgumentException("service work traversal must terminate at its exact semantic station");
        }
        if (completedWorkTicks < 0 || completedWorkTicks > REQUIRED_WORK_TICKS
                || (phase != SettlementServiceWorkPhase.WORKING && completedWorkTicks != 0)
                || (phase == SettlementServiceWorkPhase.WORKING && completedWorkTicks >= REQUIRED_WORK_TICKS)) {
            throw new IllegalArgumentException("service work progress is invalid");
        }
        if (kind == SettlementServiceWorkKind.STRUCTURAL_REPAIR && !(target instanceof SettlementServiceTarget.StructureCell)
                || kind == SettlementServiceWorkKind.DECONTAMINATION && !(target instanceof SettlementServiceTarget.Infection)) {
            throw new IllegalArgumentException("service-work kind and semantic target disagree");
        }
    }

    public SettlementServiceWork withCursor(int nextCursor) {
        if (nextCursor != traversalCursor + 1) throw new IllegalArgumentException("service work cursor must advance one retained edge");
        return new SettlementServiceWork(id, kind, settlementId, workerId, facilityId, station, inputItemId, target, intentId,
                traversal, nextCursor, SettlementServiceWorkPhase.APPROACH, 0);
    }

    public SettlementServiceWork withPhase(SettlementServiceWorkPhase next, int nextCompletedWorkTicks) {
        return new SettlementServiceWork(id, kind, settlementId, workerId, facilityId, station, inputItemId, target, intentId,
                traversal, traversalCursor, next, nextCompletedWorkTicks);
    }
}
