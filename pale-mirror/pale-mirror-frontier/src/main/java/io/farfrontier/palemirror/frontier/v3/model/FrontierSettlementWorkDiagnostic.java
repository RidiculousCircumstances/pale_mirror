package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded, pure explanation of one settlement's current work admission. */
public record FrontierSettlementWorkDiagnostic(
        String settlementId, Lane strategic, Lane facility, List<String> readySites,
        List<String> pendingHarvestSchedules, String harvestAdmission,
        int livingFarmers, String availableFarmerId, String farmStatus,
        String depotSurface, boolean depotHasFreeSlot
) {
    public FrontierSettlementWorkDiagnostic {
        Objects.requireNonNull(settlementId, "settlement id"); Objects.requireNonNull(strategic, "strategic lane");
        Objects.requireNonNull(facility, "facility lane"); readySites = List.copyOf(readySites);
        pendingHarvestSchedules = List.copyOf(pendingHarvestSchedules); Objects.requireNonNull(harvestAdmission, "harvest admission");
        Objects.requireNonNull(availableFarmerId, "available farmer"); Objects.requireNonNull(farmStatus, "farm status");
        Objects.requireNonNull(depotSurface, "depot surface");
    }

    public record Lane(String objectiveId, String kind, String status) {
        public Lane {
            Objects.requireNonNull(objectiveId, "objective id"); Objects.requireNonNull(kind, "objective kind"); Objects.requireNonNull(status, "objective status");
        }
        static Lane none() { return new Lane("", "", "NONE"); }
    }

    public static Optional<FrontierSettlementWorkDiagnostic> inspect(CheckpointImage checkpoint, FrontierWorldState state, SubjectId settlementId) {
        Objects.requireNonNull(checkpoint, "checkpoint"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(settlementId, "settlement id");
        if (state.bootstrap().settlements().stream().noneMatch(value -> value.id().equals(settlementId))) return Optional.empty();
        List<ResourceSite> sites = FrontierResourceSitePlan.compile(state.bootstrap()).values().stream()
                .filter(site -> site.settlementId().equals(settlementId)).sorted(Comparator.comparing(ResourceSite::id)).toList();
        List<String> readySites = sites.stream().filter(site -> state.resourceSites().site(site.id()).phase() == ResourceSitePhase.READY)
                .map(site -> site.id().value()).toList();
        Lane strategic = lane(state, settlementId, StrategicObjectiveLane.STRATEGIC);
        Lane facility = lane(state, settlementId, StrategicObjectiveLane.FACILITY);
        List<String> pendingSchedules = checkpoint.schedules().stream()
                .filter(action -> action.kind().equals("frontier.objective.resource_harvest"))
                .filter(action -> sites.stream().anyMatch(site -> site.id().equals(action.subject())))
                .sorted(Comparator.comparing(action -> action.dueAt()))
                .map(action -> action.id().value() + "@" + action.dueAt().ticks()).toList();
        int livingFarmers = (int) state.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(settlementId) && value.profession() == ResidentProfession.AGRICULTURAL_WORKER)
                .filter(value -> state.actorLocations().get(value.id()).condition().status() == ActorLifeStatus.ALIVE).count();
        boolean workCapableFarmer = state.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(settlementId) && value.profession() == ResidentProfession.AGRICULTURAL_WORKER)
                .anyMatch(value -> FrontierWorldStateSupport.workCapable(state, value));
        String availableFarmer = FrontierWorldStateSupport.availableFieldResident(state, settlementId, ResidentProfession.AGRICULTURAL_WORKER)
                .map(value -> value.id().value()).orElse("");
        String farmStatus = sites.stream().map(site -> state.structureConditions().get(site.facilityId()).name()).distinct().sorted()
                .reduce((left, right) -> left.equals(right) ? left : "MIXED").orElse("MISSING");
        var surface = state.inventory().surfaces().get(FrontierWorldState.depotId(settlementId));
        String depotSurface = surface == null ? "MISSING" : surface.status().name();
        boolean depotHasFreeSlot = surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE
                && state.firstFreeContainerSlot(surface.containerId()).isPresent();
        String admission = harvestAdmission(readySites, facility, farmStatus, livingFarmers, workCapableFarmer, availableFarmer, depotSurface, depotHasFreeSlot, pendingSchedules);
        return Optional.of(new FrontierSettlementWorkDiagnostic(settlementId.value(), strategic, facility, readySites, pendingSchedules, admission,
                livingFarmers, availableFarmer, farmStatus, depotSurface, depotHasFreeSlot));
    }

    private static Lane lane(FrontierWorldState state, SubjectId settlementId, StrategicObjectiveLane lane) {
        return state.strategicPlans().objectives().values().stream().filter(objective -> objective.ownerId().equals(settlementId)
                        && objective.lane() == lane && objective.status() == StrategicObjectiveStatus.ACTIVE)
                .sorted(Comparator.comparing(StrategicObjective::id)).findFirst()
                .map(value -> new Lane(value.id().value(), value.kind().name(), value.status().name())).orElseGet(Lane::none);
    }

    private static String harvestAdmission(List<String> readySites, Lane facility, String farmStatus, int livingFarmers, boolean workCapableFarmer, String availableFarmer,
                                           String depotSurface, boolean depotHasFreeSlot, List<String> pendingSchedules) {
        if (readySites.isEmpty()) return "NO_READY_SITE";
        if (!facility.status().equals("NONE")) return "FACILITY_LANE_BUSY";
        if (!farmStatus.equals(StructureCondition.INTACT.name())) return "FARM_UNAVAILABLE";
        if (livingFarmers == 0) return "NO_LIVING_FARMER";
        if (!workCapableFarmer) return "FARMERS_STARVING";
        if (availableFarmer.isEmpty()) return "FARMER_RESERVED";
        if (!depotSurface.equals(ContainerSurfaceStatus.ACTIVE.name())) return "DEPOT_SURFACE_" + depotSurface;
        if (!depotHasFreeSlot) return "DEPOT_FULL";
        if (pendingSchedules.isEmpty()) return "MISSING_HARVEST_SCHEDULE";
        return "READY_TO_PLAN";
    }
}
