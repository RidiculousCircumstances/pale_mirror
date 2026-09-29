package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only admission policy for the depot's shared access throat. Each contender carries
 * an explicit meal or work owner; this projection does not own a second job, item ledger or
 * due-time queue. Waiting processes retain their normal schedule and retry at their own tick.
 */
public final class ServiceAccessCoordinator {
    private ServiceAccessCoordinator() { }

    private record Applicant(SubjectId jobId, SubjectId workerId, boolean alreadyAtPort) { }

    /** A meal reserves the throat from start until its witnessed departure. */
    public static boolean depotAvailableForMeal(FrontierWorldState state, SubjectId depotId,
                                                SubjectId residentId) {
        Objects.requireNonNull(state, "service access state");
        Objects.requireNonNull(depotId, "service access depot");
        Objects.requireNonNull(residentId, "service access resident");
        return state.humanPopulation().meals().values().stream().noneMatch(meal -> meal.depotId().equals(depotId))
                // A safe-yielded baker or farmer retains the job but can eat with the
                // same body; only another worker's depot turn excludes this resident.
                && workApplicants(state, depotId).stream().allMatch(applicant -> applicant.workerId().equals(residentId));
    }

    /** A work owner can approach only when no meal owns the throat and it is next in line. */
    public static boolean depotAvailableForWork(FrontierWorldState state, SubjectId depotId, SubjectId jobId,
                                                SubjectId workerId) {
        Objects.requireNonNull(state, "service access state");
        Objects.requireNonNull(depotId, "service access depot");
        Objects.requireNonNull(jobId, "service access job");
        Objects.requireNonNull(workerId, "service access worker");
        if (state.humanPopulation().meals().values().stream().anyMatch(meal -> meal.depotId().equals(depotId)))
            return false;
        return workApplicants(state, depotId).stream().findFirst()
                .map(first -> first.jobId().equals(jobId) && first.workerId().equals(workerId))
                .orElse(false);
    }

    public static boolean depotAvailableForHarvest(FrontierWorldState state, ResourceSiteHarvestJob job) {
        SubjectId settlementId = ResourceSiteHarvestGoal.depotPort(state, job).settlementId();
        return depotAvailableForWork(state, FrontierWorldState.depotId(settlementId), job.id(), job.workerId());
    }

    private static List<Applicant> workApplicants(FrontierWorldState state, SubjectId depotId) {
        SettlementDepotServicePort port = port(state, depotId);
        List<Applicant> applicants = new ArrayList<>();
        for (ProductionJob job : state.productionJobs().values()) {
            if (job.bakeryWork().isEmpty() || !job.settlementId().equals(port.settlementId())) continue;
            BakeryWorkState.Phase phase = job.bakeryWork().orElseThrow().phase();
            SurfaceAnchor body = currentSurface(state, job.workerId());
            boolean atPort = atPort(port, body);
            if (phase == BakeryWorkState.Phase.DEPOT_PICKUP || phase == BakeryWorkState.Phase.DEPOT_DELIVERY
                    || atPort)
                applicants.add(new Applicant(job.id(), job.workerId(), atPort));
        }
        for (ResourceSiteLifecycle site : state.resourceSites().sites().values()) {
            if (!(site.activeWork().orElse(null) instanceof ResourceSiteHarvestJob job)) continue;
            ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
            if (!ResourceSiteHarvestGoal.depotPort(state, job).settlementId().equals(port.settlementId())) continue;
            SurfaceAnchor body = currentSurface(state, job.workerId());
            boolean atPort = atPort(port, body);
            if (goal.kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE || atPort)
                applicants.add(new Applicant(job.id(), job.workerId(), atPort));
        }
        applicants.sort(Comparator.comparing(Applicant::alreadyAtPort).reversed()
                .thenComparing(Applicant::jobId));
        return List.copyOf(applicants);
    }

    private static SurfaceAnchor currentSurface(FrontierWorldState state, SubjectId workerId) {
        BodyPosition leased = state.sceneLeases().values().stream()
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED
                        && lease.retainsMemberCustody(workerId))
                .map(lease -> lease.memberPosition(workerId))
                .reduce((left, right) -> { throw new IllegalArgumentException("worker has competing physical service custody"); })
                .orElse(null);
        if (leased != null) return leased.supportingSurface();
        ActorLocation actor = state.actorLocations().get(workerId);
        return actor == null ? null : actor.supportingSurface();
    }

    private static SettlementDepotServicePort port(FrontierWorldState state, SubjectId depotId) {
        ContainerRecord container = Objects.requireNonNull(state.inventory().containers().get(depotId),
                "unknown depot service container");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), container.ownerId());
        if (!FrontierWorldState.depotId(settlement.id()).equals(depotId))
            throw new IllegalArgumentException("service container is not the settlement's declared depot");
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT)
                .reduce((left, right) -> { throw new IllegalArgumentException("duplicate depot service identity"); })
                .orElseThrow(() -> new IllegalArgumentException("unknown depot service identity"));
        return SettlementDepotServicePort.forDepot(depot);
    }

    /**
     * A personal clear, materialized apron spot, selected before the permit is taken and then
     * retained by the meal. Another resident's current body is never chosen as an exit.
     */
    public static Optional<SurfaceAnchor> mealClearingSurface(FrontierWorldState state, SubjectId residentId) {
        ResidentProfile resident = Objects.requireNonNull(state.humanPopulation().resident(residentId), "service resident");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
        List<BlockPosition> homes = SettlementResidentIngressPlan.compile(state.bootstrap().bounds(),
                state.bootstrap().terrain(), settlement,
                state.bootstrap().ruleset().facilityCapacity().intactHousingBeds()).homeSlots();
        SurfaceAnchor current = state.actorLocations().get(residentId).supportingSurface();
        SettlementDepotServicePort port = port(state, FrontierWorldState.depotId(settlement.id()));
        if (homes.contains(current.support()) && outsideThroat(port, current)) return Optional.of(current);
        for (int ordinal = 0; ordinal < settlement.residents().size() && ordinal < homes.size(); ordinal++) {
            if (settlement.residents().get(ordinal).id().equals(residentId)) {
                SurfaceAnchor personal = new SurfaceAnchor(homes.get(ordinal));
                if (outsideThroat(port, personal) && unoccupied(state, residentId, personal))
                    return Optional.of(personal);
                break;
            }
        }
        int first = Math.floorMod(residentId.value().hashCode(), homes.size());
        for (int offset = 0; offset < homes.size(); offset++) {
            SurfaceAnchor candidate = new SurfaceAnchor(homes.get((first + offset) % homes.size()));
            if (!outsideThroat(port, candidate)) continue;
            if (unoccupied(state, residentId, candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static boolean unoccupied(FrontierWorldState state, SubjectId residentId, SurfaceAnchor candidate) {
        return state.actorLocations().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(residentId))
                .filter(entry -> entry.getValue().condition().status() == ActorLifeStatus.ALIVE)
                .noneMatch(entry -> entry.getValue().supportingSurface().equals(candidate));
    }

    /** A consumed meal does not free the socket until its exact actor has left the throat. */
    public static boolean cleared(FrontierWorldState state, ResidentMeal meal, BodyPosition body) {
        Objects.requireNonNull(state, "service access state");
        Objects.requireNonNull(meal, "service access meal");
        Objects.requireNonNull(body, "service access body");
        if (!body.equals(meal.clearingSurface().standingBody())) return false;
        SettlementDepotServicePort port = port(state, meal.depotId());
        return outsideThroat(port, body.supportingSurface());
    }

    private static boolean outsideThroat(SettlementDepotServicePort port, SurfaceAnchor surface) {
        return !port.ownedAccessSurfaces().contains(surface)
                && !port.exteriorApproach().equals(surface);
    }

    private static boolean atPort(SettlementDepotServicePort port, SurfaceAnchor surface) {
        return surface != null && !outsideThroat(port, surface);
    }
}
