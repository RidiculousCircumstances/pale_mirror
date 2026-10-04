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
        SubjectId taskId,
        SettlementServiceWorkKind kind,
        SubjectId settlementId,
        SubjectId workerId,
        SubjectId facilityId,
        InventoryCustody.ContainerSlot inputSource,
        SurfaceAnchor inputStation,
        SurfaceAnchor workStation,
        SubjectId inputItemId,
        SettlementServiceTarget target,
        PhysicalIntentId inputIssueIntentId,
        PhysicalIntentId endpointIntentId,
        TraversalTopology inputTraversal,
        int inputTraversalCursor,
        TraversalTopology workTraversal,
        int workTraversalCursor,
        SettlementServiceWorkPhase phase,
        int completedWorkTicks,
        StationApproachState spatial
) {
    public static final int REQUIRED_WORK_TICKS = 80;
    public static final int MAX_RETAINED = 1_024;

    /** Fresh admission only; hydration supplies its explicit saved spatial continuation. */
    public SettlementServiceWork(SubjectId id, SubjectId taskId, SettlementServiceWorkKind kind,
                                 SubjectId settlementId, SubjectId workerId, SubjectId facilityId,
                                 InventoryCustody.ContainerSlot inputSource, SurfaceAnchor inputStation,
                                 SurfaceAnchor workStation, SubjectId inputItemId, SettlementServiceTarget target,
                                 PhysicalIntentId inputIssueIntentId, PhysicalIntentId endpointIntentId,
                                 TraversalTopology inputTraversal, int inputTraversalCursor,
                                 TraversalTopology workTraversal, int workTraversalCursor,
                                 SettlementServiceWorkPhase phase, int completedWorkTicks) {
        this(id, taskId, kind, settlementId, workerId, facilityId, inputSource, inputStation, workStation,
                inputItemId, target, inputIssueIntentId, endpointIntentId, inputTraversal, inputTraversalCursor,
                workTraversal, workTraversalCursor, phase, completedWorkTicks, StationApproachState.initial());
    }

    public SettlementServiceWork {
        id = Objects.requireNonNull(id, "service work id");
        taskId = Objects.requireNonNull(taskId, "service work task");
        kind = Objects.requireNonNull(kind, "service work kind");
        settlementId = Objects.requireNonNull(settlementId, "service work settlement");
        workerId = Objects.requireNonNull(workerId, "service work worker");
        facilityId = Objects.requireNonNull(facilityId, "service work facility");
        inputSource = Objects.requireNonNull(inputSource, "service work input source");
        inputStation = Objects.requireNonNull(inputStation, "service work input station");
        workStation = Objects.requireNonNull(workStation, "service work station");
        inputItemId = Objects.requireNonNull(inputItemId, "service work input");
        target = Objects.requireNonNull(target, "service work target");
        inputIssueIntentId = Objects.requireNonNull(inputIssueIntentId, "service work input-issue intent");
        endpointIntentId = Objects.requireNonNull(endpointIntentId, "service work endpoint intent");
        inputTraversal = Objects.requireNonNull(inputTraversal, "service work input traversal");
        workTraversal = Objects.requireNonNull(workTraversal, "service work work traversal");
        phase = Objects.requireNonNull(phase, "service work phase");
        spatial = Objects.requireNonNull(spatial, "service spatial continuation");
        if (!id.value().startsWith("service:") || !taskId.value().startsWith("task:") || !settlementId.value().startsWith("settlement:")
                || !workerId.value().startsWith("resident:") || !facilityId.value().startsWith("structure:")
                || inputIssueIntentId.equals(endpointIntentId)
                || !validTraversal(inputTraversal, id, inputTraversalCursor)
                || !validTraversal(workTraversal, id, workTraversalCursor)) {
            throw new IllegalArgumentException("service work must retain exact pedestrian source and work corridors/cursors");
        }
        if (!inputTraversal.linearCorridorSurfaces().getLast().equals(inputStation)
                || !workTraversal.linearCorridorSurfaces().getFirst().equals(inputStation)
                || !workTraversal.linearCorridorSurfaces().getLast().equals(workStation)) {
            throw new IllegalArgumentException("service work corridors must join the exact source and work stations");
        }
        SurfaceAnchor nextStation = SettlementServiceJourneyKnowledge.target(phase, inputTraversal,
                inputTraversalCursor, workTraversal, workTraversalCursor);
        if (spatial.approach().isPresent() && !spatial.approach().orElseThrow().target().equals(nextStation))
            throw new IllegalArgumentException("service approach must retain the same unfinished station");
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

    private static boolean validTraversal(TraversalTopology traversal, SubjectId workId, int cursor) {
        return traversal.provenance().equals(workId) && traversal.edges().stream().noneMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                || !edge.traversableBy(TraversalCapability.PEDESTRIAN)) && cursor >= 0 && cursor < traversal.linearCorridorSurfaces().size();
    }

    public SettlementServiceWork withInputTraversalCursor(int nextCursor) {
        if (nextCursor != inputTraversalCursor + 1) throw new IllegalArgumentException("service work input cursor must advance one retained edge");
        SettlementServiceWorkPhase next = nextCursor == inputTraversal.linearCorridorSurfaces().size() - 1
                ? SettlementServiceWorkPhase.INPUT_ISSUE_PENDING : SettlementServiceWorkPhase.APPROACH_INPUT;
        return copy(next, nextCursor, workTraversalCursor, 0, spatial.cleared());
    }

    public SettlementServiceWork withWorkTraversalCursor(int nextCursor) {
        if (nextCursor != workTraversalCursor + 1) throw new IllegalArgumentException("service work work cursor must advance one retained edge");
        SettlementServiceWorkPhase next = nextCursor == workTraversal.linearCorridorSurfaces().size() - 1
                ? SettlementServiceWorkPhase.WORKING : SettlementServiceWorkPhase.APPROACH_WORK;
        return copy(next, inputTraversalCursor, nextCursor, 0, spatial.cleared());
    }

    public SettlementServiceWork withInputIssued() {
        if ((phase != SettlementServiceWorkPhase.INPUT_ISSUE_PENDING && phase != SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART)
                || inputTraversalCursor != inputTraversal.linearCorridorSurfaces().size() - 1)
            throw new IllegalArgumentException("service input outcome must retain its reached source boundary");
        SettlementServiceWorkPhase next = workTraversal.linearCorridorSurfaces().size() == 1
                ? SettlementServiceWorkPhase.WORKING : SettlementServiceWorkPhase.APPROACH_WORK;
        return copy(next, inputTraversalCursor, 0, 0, spatial.cleared());
    }

    public SettlementServiceWork withPhase(SettlementServiceWorkPhase next, int nextCompletedWorkTicks) {
        StationApproachState nextSpatial = next == SettlementServiceWorkPhase.WORKING
                || next == SettlementServiceWorkPhase.EFFECT_READY || next == SettlementServiceWorkPhase.COMPLETED
                || next == SettlementServiceWorkPhase.BLOCKED
                ? spatial.cleared() : spatial;
        return copy(next, inputTraversalCursor, workTraversalCursor, nextCompletedWorkTicks, nextSpatial);
    }

    public SettlementServiceWork withSpatial(StationApproachState next) {
        return copy(phase, inputTraversalCursor, workTraversalCursor, completedWorkTicks, next);
    }

    private SettlementServiceWork copy(SettlementServiceWorkPhase next, int nextInputCursor, int nextWorkCursor,
                                       int nextCompletedWorkTicks, StationApproachState nextSpatial) {
        return new SettlementServiceWork(id, taskId, kind, settlementId, workerId, facilityId, inputSource, inputStation, workStation,
                inputItemId, target, inputIssueIntentId, endpointIntentId, inputTraversal, nextInputCursor, workTraversal,
                nextWorkCursor, next, nextCompletedWorkTicks, nextSpatial);
    }
}
