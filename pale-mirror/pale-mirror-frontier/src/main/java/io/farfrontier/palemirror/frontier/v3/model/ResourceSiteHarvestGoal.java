package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The farmer's next semantic destination, independently of any pedestrian route cache. */
public record ResourceSiteHarvestGoal(SubjectId jobId, SubjectId siteId, SubjectId workerId,
                                      long layoutRevision, long goalRevision, int nextWorkSlot, Kind kind,
                                      Optional<ResourceFieldLayout.CellId> cellId,
                                      List<SurfaceAnchor> legalStations,
                                      TraversalCapability capability, ArrivalContract arrivalContract) {
    public enum Kind {
        WORK_CELL(1), DEPOT_SERVICE(2), EQUIPMENT_SERVICE(3), EQUIPMENT_RETURN(4);
        private final int wireTag;
        Kind(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Kind requireWireTag(int tag) {
            for (Kind value : values()) if (value.wireTag == tag) return value;
            throw new IllegalArgumentException("unknown resource-site harvest goal kind");
        }
    }
    public enum ArrivalContract { EXACT_WORK_STATION, ANY_DEPOT_SERVICE_STATION, EXACT_STORAGE_STATION }

    public ResourceSiteHarvestGoal {
        Objects.requireNonNull(jobId, "field goal job");
        Objects.requireNonNull(siteId, "field goal site");
        Objects.requireNonNull(workerId, "field goal worker");
        Objects.requireNonNull(kind, "field goal kind");
        cellId = Objects.requireNonNull(cellId, "field goal cell identity");
        legalStations = List.copyOf(Objects.requireNonNull(legalStations, "field goal legal stations"));
        Objects.requireNonNull(capability, "field goal capability");
        Objects.requireNonNull(arrivalContract, "field goal arrival contract");
        if (layoutRevision < 1 || goalRevision < 1 || nextWorkSlot < 0 || legalStations.isEmpty()
                || legalStations.size() > 8 || legalStations.stream().anyMatch(Objects::isNull)
                || legalStations.stream().distinct().count() != legalStations.size()
                || (kind == Kind.WORK_CELL) != cellId.isPresent()
                || (kind == Kind.WORK_CELL && (arrivalContract != ArrivalContract.EXACT_WORK_STATION || legalStations.size() != 1))
                || (kind == Kind.DEPOT_SERVICE && arrivalContract != ArrivalContract.ANY_DEPOT_SERVICE_STATION)
                || ((kind == Kind.EQUIPMENT_SERVICE || kind == Kind.EQUIPMENT_RETURN)
                    && (arrivalContract != ArrivalContract.EXACT_STORAGE_STATION || legalStations.size() != 1))
                || capability != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("field goal must name one typed, bounded pedestrian destination");
    }

    public boolean arrivedAt(SurfaceAnchor observed) {
        return legalStations.contains(Objects.requireNonNull(observed, "observed goal station"));
    }

    /** The work owner chooses the CellId/depot; the movement subsystem sees only this destination. */
    public MovementOrder movementOrder() {
        return new MovementOrder(jobId, workerId, kind == Kind.EQUIPMENT_SERVICE || kind == Kind.EQUIPMENT_RETURN
                ? ResourceFieldLayout.MAX_CELLS + (long) kind.wireTag() : nextWorkSlot, goalRevision, legalStations, capability,
                kind != Kind.DEPOT_SERVICE ? MovementOrder.ArrivalPolicy.EXACT_STATION
                        : MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
    }

    /** Crop work requires the current CellId and the canonical actor at its legal station. */
    public static boolean actorAtWorkCell(FrontierWorldState state, ResourceSiteHarvestJob job) {
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) return false;
        ResourceSiteHarvestGoal goal = current(state, job);
        return job.matchesWorkGoal(goal, actor.supportingSurface());
    }

    /** Depot arrival uses the actor's actual supported body, never a cached route endpoint. */
    public static boolean actorAtDepot(FrontierWorldState state, ResourceSiteHarvestJob job) {
        ActorLocation actor = state.actorLocations().get(job.workerId());
        ResourceSiteHarvestGoal goal = current(state, job);
        return actor != null && actor.condition().status() == ActorLifeStatus.ALIVE
                && goal.kind() == Kind.DEPOT_SERVICE && goal.arrivedAt(actor.supportingSurface());
    }

    /** A stable diagnostic representative; navigation itself may choose any legal station. */
    public SurfaceAnchor representative() { return legalStations.getFirst(); }

    public static ResourceSiteHarvestGoal current(FrontierWorldState state, ResourceSiteHarvestJob job) {
        if (FieldEquipment.transferring(job)) {
            var cycle = state.resourceSites().cycle(job.siteId());
            return new ResourceSiteHarvestGoal(job.id(), job.siteId(), job.workerId(), cycle.layout().revision(),
                    job.storageRevision(), cycle.layout().cells().size(), job.equipment().stage() == WorkToolAssignment.Stage.ACQUIRING
                        ? Kind.EQUIPMENT_SERVICE : Kind.EQUIPMENT_RETURN, Optional.empty(),
                    List.of(FieldEquipment.endpoint(state, job).station()), TraversalCapability.PEDESTRIAN, ArrivalContract.EXACT_STORAGE_STATION);
        }
        int slot = job.returningForBatch() || job.progress().complete()
                ? state.resourceSites().cycle(job.siteId()).layout().cells().size()
                : job.progress().nextCropSlotIndex();
        return forSlot(state, job, slot);
    }

    /** Used after an accounted obstruction without consulting the obsolete physical route. */
    public static ResourceSiteHarvestGoal forSlot(FrontierWorldState state, ResourceSiteHarvestJob job, int nextSlot) {
        Objects.requireNonNull(state, "field goal world");
        Objects.requireNonNull(job, "field goal farmer");
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        if (nextSlot < 0 || nextSlot > cycle.layout().cells().size())
            throw new IllegalArgumentException("field goal slot is outside retained work progress");
        if (nextSlot < cycle.layout().cells().size()) {
            ResourceFieldLayout.Cell cell = cycle.layout().cells().get(nextSlot);
            return new ResourceSiteHarvestGoal(job.id(), job.siteId(), job.workerId(), cycle.layout().revision(), cycle.layout().revision(), nextSlot,
                    Kind.WORK_CELL, Optional.of(cell.id()), List.of(cell.workstation()),
                    TraversalCapability.PEDESTRIAN, ArrivalContract.EXACT_WORK_STATION);
        }
        return new ResourceSiteHarvestGoal(job.id(), job.siteId(), job.workerId(), cycle.layout().revision(), job.storageRevision(), nextSlot,
                Kind.DEPOT_SERVICE, Optional.empty(), depotPort(state, job).stations(),
                TraversalCapability.PEDESTRIAN, ArrivalContract.ANY_DEPOT_SERVICE_STATION);
    }

    /** The current bootstrap's exact container-to-structure service binding. */
    public static ResourceStorageAccess depotPort(FrontierWorldState state, ResourceSiteHarvestJob job) {
        ResourceSite site = Objects.requireNonNull(state.resourceSite(job.siteId()), "field goal site descriptor");
        var access = ResourceStorageAccess.at(state, job.outputSlot().containerId());
        if (!access.settlementId().equals(site.settlementId()))
            throw new IllegalArgumentException("field goal has a foreign output container");
        return access;
    }

    public static ResourceStorageAccess servicePort(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return FieldEquipment.transferring(job)
                ? ResourceStorageAccess.at(state, FieldEquipment.endpoint(state, job).containerId()) : depotPort(state, job);
    }
}
