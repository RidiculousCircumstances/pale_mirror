package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable player-facing board plan. It explains owned local objects but is never canonical
 * state, a debug dashboard, or permission to overwrite a physical world change.
 */
public final class FrontierReadabilityPlan {
    private static final int MAX_BOARDS = 256;
    private final Map<SubjectId, FrontierObjectBoard> boards;

    private FrontierReadabilityPlan(Map<SubjectId, FrontierObjectBoard> boards) { this.boards = Map.copyOf(boards); }

    public static FrontierReadabilityPlan compile(FrontierWorldState state) {
        Objects.requireNonNull(state, "state");
        Map<SubjectId, FrontierObjectBoard> values = new LinkedHashMap<>();
        Map<SubjectId, InfectionOverlayStage> contamination = contamination(state);
        state.bootstrap().settlements().forEach(settlement -> settlement.structures().forEach(structure -> {
            StructureCondition condition = state.structureConditions().get(structure.id());
            InfectionOverlayStage stage = contamination.get(structure.id());
            boolean quarantine = structure.kind() == StructureKind.INFIRMARY && state.humanPopulation().quarantined(settlement.id());
            boolean foodRisk = structure.kind() == StructureKind.DEPOT && foodRisk(state, settlement.id());
            add(values, new FrontierObjectBoard(structure.id(), structureBoardPosition(structure, condition), tone(condition, stage, quarantine, foodRisk), scope(structure.kind()),
                    settlement.displayName() + "\n" + structureName(structure.kind()) + "\n" + withContamination(facilityText(state, settlement, structure, condition), stage)));
        }));
        state.bootstrap().hive().organs().forEach(organ -> addOrgan(values, state, organ, contamination.get(organ.id())));
        state.hiveColony().addedOrgans().values().forEach(organ -> addOrgan(values, state, organ, contamination.get(organ.id())));
        FrontierResourceSitePlan.compile(state.bootstrap()).values().forEach(site -> addResourceSite(values, state, site));
        addRouteNetwork(values, state);
        return new FrontierReadabilityPlan(values);
    }

    public Map<SubjectId, FrontierObjectBoard> boards() { return boards; }

    /**
     * Exact canonical dependencies of the board projection.  Deliberately excludes ambient
     * lease positions, actor body positions, checkpoint revision and other continuously changing
     * execution data: none of those facts changes a board's slot, text or tone.  Keeping those
     * motion-only revisions out of the NeoForge board cursor prevents a full player-facing plan
     * recompilation on every server tick.
     */
    public static ReadabilityInput input(FrontierWorldState state) {
        Objects.requireNonNull(state, "readability state");
        boolean routeDamaged = state.physicalDeltas().values().stream()
                .anyMatch(delta -> delta.ownerId().equals(java.util.Optional.of(FrontierRouteNetwork.OWNER)));
        boolean sceneConflict = state.sceneLeases().values().stream().anyMatch(lease -> lease.status() == SceneLeaseStatus.CONFLICT);
        boolean caravan = state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE);
        RouteConstruction construction = state.routeConstructions().values().stream()
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst().orElse(null);
        return new ReadabilityInput(state.bootstrap(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.hiveColony().addedOrgans(), state.physicalDeltas(), state.resourceSites(), state.routeTopology(), state.humanPopulation(),
                state.companies(), state.hiveColony().mobilizations(), new ActorConditionView(state.actorLocations()), routeDamaged, sceneConflict, caravan, construction);
    }

    /** Immutable equality key for the bounded physical board cursor. */
    public record ReadabilityInput(FrontierBootstrap bootstrap, Map<SubjectId, StructureCondition> structureConditions,
                                   Map<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> infection,
                                   ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
                                   Map<SubjectId, HiveOrgan> addedOrgans, Map<BlockPosition, PhysicalDelta> physicalDeltas,
                                   ResourceSiteState resourceSites, RouteTopology routeTopology, HumanPopulation humanPopulation,
                                   CompanyRegistry companies, Map<SubjectId, HiveMobilization> mobilizations, ActorConditionView actorConditions,
                                   boolean routeDamaged, boolean sceneConflict, boolean caravan, RouteConstruction construction) {
        public ReadabilityInput {
            Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(structureConditions, "structure conditions");
            Objects.requireNonNull(infection, "infection"); Objects.requireNonNull(inventory, "inventory");
            Objects.requireNonNull(productionJobs, "production jobs"); Objects.requireNonNull(addedOrgans, "added organs");
            Objects.requireNonNull(physicalDeltas, "physical deltas"); Objects.requireNonNull(resourceSites, "resource sites");
            Objects.requireNonNull(routeTopology, "route topology"); Objects.requireNonNull(humanPopulation, "human population");
            Objects.requireNonNull(companies, "companies"); Objects.requireNonNull(mobilizations, "hive mobilizations"); Objects.requireNonNull(actorConditions, "actor conditions");
        }
    }

    /**
     * A board plan needs actor vitality, never a walking body's coordinates.  This view retains
     * the immutable actor-location map without allocating a second map each tick and compares
     * only the exact condition values which can change player-facing text.
     */
    public static final class ActorConditionView {
        private final Map<SubjectId, ActorLocation> locations;

        private ActorConditionView(Map<SubjectId, ActorLocation> locations) {
            this.locations = Objects.requireNonNull(locations, "actor locations");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ActorConditionView candidate) || locations.size() != candidate.locations.size()) return false;
            for (Map.Entry<SubjectId, ActorLocation> entry : locations.entrySet()) {
                ActorLocation otherLocation = candidate.locations.get(entry.getKey());
                if (otherLocation == null || !entry.getValue().condition().equals(otherLocation.condition())) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            for (Map.Entry<SubjectId, ActorLocation> entry : locations.entrySet()) {
                hash += entry.getKey().hashCode() ^ entry.getValue().condition().hashCode();
            }
            return hash;
        }
    }

    private static void addOrgan(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state, HiveOrgan organ, InfectionOverlayStage stage) {
        boolean operational = state.isHiveOrganOperational(organ.id());
        MobilizationReadout mobilization = mobilizationReadout(state, organ);
        FrontierObjectBoard.Tone tone = stage == null && operational && mobilization.tone() == FrontierObjectBoard.Tone.HIVE
                ? FrontierObjectBoard.Tone.HIVE : FrontierObjectBoard.Tone.WARNING;
        add(values, new FrontierObjectBoard(organ.id(), organ.anchor().offset(0, 2, -3), tone,
                organ.kind() == HiveOrganKind.GANGLION ? FrontierObjectBoard.Scope.LANDMARK : FrontierObjectBoard.Scope.LOCAL,
                "HIVE\n" + organName(organ.kind()) + "\n" + withContamination(operational ? mobilization.text() : "DISABLED · REPAIR NEEDED", stage)));
    }

    private static MobilizationReadout mobilizationReadout(FrontierWorldState state, HiveOrgan organ) {
        if (organ.kind() != HiveOrganKind.HIBERNACULUM) return new MobilizationReadout(FrontierObjectBoard.Tone.HIVE, "ACTIVE");
        HiveMobilization mobilization = state.hiveColony().mobilizations().values().stream()
                .filter(value -> value.memberIds().stream().anyMatch(member -> state.hiveColony().bioformLifecycles().get(member).homeSlot()
                        .map(slot -> slot.hibernaculumId().equals(organ.id())).orElse(false)))
                .sorted(Comparator.comparing(HiveMobilization::id)).findFirst().orElse(null);
        if (mobilization == null) return new MobilizationReadout(FrontierObjectBoard.Tone.HIVE, "ACTIVE");
        return switch (mobilization.status()) {
            case WAKING, RELEASING -> new MobilizationReadout(FrontierObjectBoard.Tone.WARNING,
                    "WAKE SEQUENCE · " + mobilization.releasedMemberIds().size() + "/" + mobilization.memberIds().size());
            case ASSEMBLING -> new MobilizationReadout(FrontierObjectBoard.Tone.HIVE,
                    "ASSEMBLY · " + mobilization.memberIds().size() + " FORMED");
            case CONFLICT -> new MobilizationReadout(FrontierObjectBoard.Tone.WARNING, "WAKE INTERRUPTED · INSPECT COCOONS");
        };
    }

    private record MobilizationReadout(FrontierObjectBoard.Tone tone, String text) { }

    private static void addResourceSite(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state, ResourceSite site) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        add(values, new FrontierObjectBoard(site.id(), fieldBoardPosition(site), fieldTone(lifecycle.phase()), FrontierObjectBoard.Scope.LOCAL,
                settlement.displayName() + "\nWHEAT FIELD\n" + fieldStateText(lifecycle)));
    }

    private static void addRouteNetwork(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state) {
        boolean damaged = state.physicalDeltas().values().stream().anyMatch(delta ->
                delta.ownerId().equals(java.util.Optional.of(FrontierRouteNetwork.OWNER)));
        boolean sceneConflict = state.sceneLeases().values().stream().anyMatch(lease -> lease.status() == SceneLeaseStatus.CONFLICT);
        boolean caravan = state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE);
        FrontierObjectBoard.Tone tone = damaged || sceneConflict ? FrontierObjectBoard.Tone.WARNING : FrontierObjectBoard.Tone.SETTLEMENT;
        RouteConstruction construction = state.routeConstructions().values().stream()
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst().orElse(null);
        String stateText = construction == null
                ? damaged ? "ROUTE DAMAGE · PATROL NEEDED" : sceneConflict ? "SCENE BLOCKED · KEEP CLEAR"
                : caravan ? "CARAVAN EN ROUTE" : "ACTIVE · 12 SETTLEMENTS"
                : routeConstructionText(state, construction);
        add(values, new FrontierObjectBoard(FrontierRouteNetwork.OWNER,
                FrontierRouteNetwork.maintenanceContainerPosition(state.bootstrap()).offset(0, 3, -3), tone, FrontierObjectBoard.Scope.LANDMARK,
                "FRONTIER ROUTES\nNETWORK\n" + stateText));
    }

    private static String routeConstructionText(FrontierWorldState state, RouteConstruction construction) {
        int total = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), construction.settlementId(), construction.waypoints()).size();
        if (construction.status() == RouteConstructionStatus.CONFLICT) return "REPAIR BLOCKED · INSPECT ROUTE";
        if (construction.cargoId().isEmpty()) return "ROUTE REPAIR · MATERIALS NEEDED";
        return "BYPASS BUILDING · " + construction.confirmedCells() + "/" + total;
    }

    private static BlockPosition structureBoardPosition(SettlementStructure structure, StructureCondition condition) {
        int depth = switch (structure.kind()) {
            case HALL, FARM, WORKSHOP, DEPOT -> 7;
            case HOUSING, INFIRMARY -> 6;
        };
        int height = condition == StructureCondition.DAMAGED ? 2 : switch (structure.kind()) {
            case HALL -> 5;
            case DEPOT, WORKSHOP -> 4;
            default -> 3;
        };
        return structure.anchor().offset(0, Math.max(2, height - 1), -depth / 2 - 1);
    }

    private static BlockPosition fieldBoardPosition(ResourceSite site) {
        BlockPosition firstCrop = site.cropSlots().getFirst();
        return firstCrop.offset(4, 3, -2);
    }

    private static FrontierObjectBoard.Tone tone(StructureCondition condition, InfectionOverlayStage stage, boolean quarantine, boolean foodRisk) {
        return condition == StructureCondition.INTACT && stage == null && !quarantine && !foodRisk ? FrontierObjectBoard.Tone.SETTLEMENT : FrontierObjectBoard.Tone.WARNING;
    }

    private static FrontierObjectBoard.Scope scope(StructureKind kind) {
        return kind == StructureKind.HALL ? FrontierObjectBoard.Scope.LANDMARK : FrontierObjectBoard.Scope.LOCAL;
    }

    private static FrontierObjectBoard.Tone fieldTone(ResourceSitePhase phase) {
        return switch (phase) {
            case CONFLICT, DESTROYED -> FrontierObjectBoard.Tone.WARNING;
            case UNPREPARED, GROWING, READY, HARVESTING -> FrontierObjectBoard.Tone.SETTLEMENT;
        };
    }

    private static String structureName(StructureKind kind) {
        return switch (kind) {
            case HALL -> "TOWN HALL";
            case HOUSING -> "HOMES";
            case FARM -> "FARM";
            case WORKSHOP -> "WORKSHOP";
            case DEPOT -> "DEPOT";
            case INFIRMARY -> "INFIRMARY";
        };
    }

    private static String organName(HiveOrganKind kind) {
        return switch (kind) {
            case GANGLION -> "GANGLION";
            case RELAY -> "RELAY";
            case BROOD -> "BROOD";
            case STORE -> "STORE";
            case DIGESTER -> "DIGESTER";
            case HIBERNACULUM -> "HIBERNACULUM";
            case MORPHER -> "MORPHER";
            case SPORULATOR -> "SPORULATOR";
            case SENSOR -> "SENSOR";
        };
    }

    private static String conditionText(StructureCondition condition) {
        return switch (condition) {
            case INTACT -> "OPERATIONAL";
            case DAMAGED -> "DAMAGED · REPAIR NEEDED";
            case DESTROYED -> "DESTROYED · SITE LOST";
        };
    }

    private static String fieldStateText(ResourceSiteLifecycle lifecycle) {
        return switch (lifecycle.phase()) {
            case UNPREPARED -> "PREPARING SOIL · KEEP CLEAR";
            case GROWING -> "GROWING · STAGE " + lifecycle.growthStage() + "/" + ResourceSiteLifecycle.MATURE_STAGE;
            case READY -> "READY TO HARVEST · FARMERS NEEDED";
            case HARVESTING -> "HARVEST IN PROGRESS";
            case CONFLICT -> "DAMAGED · REPAIR NEEDED";
            case DESTROYED -> "LOST · REBUILD NEEDED";
        };
    }

    /** Pure semantic contact: a live infection column intersects one current object cell. */
    private static Map<SubjectId, InfectionOverlayStage> contamination(FrontierWorldState state) {
        Map<SubjectId, InfectionOverlayStage> values = new LinkedHashMap<>();
        FrontierGrayboxPlan.currentObjectCellsByOwner(state).forEach((owner, positions) -> positions.forEach(position -> {
            var intensity = state.infection().get(InfectionCell.at(position));
            if (intensity != null && intensity.value().raw() > 0L) values.merge(owner, InfectionOverlayStage.fromRaw(intensity.value().raw()),
                    (left, right) -> left.ordinal() >= right.ordinal() ? left : right);
        }));
        return Map.copyOf(values);
    }

    private static String withContamination(String stateText, InfectionOverlayStage stage) {
        return stage == null ? stateText : stateText + "\nINFECTED\n" + stage.name();
    }

    private static String facilityText(FrontierWorldState state, Settlement settlement, SettlementStructure structure, StructureCondition condition) {
        if (structure.kind() == StructureKind.INFIRMARY && state.humanPopulation().quarantined(settlement.id())) {
            return "QUARANTINE · " + state.humanPopulation().activeCases(settlement.id()) + " ACTIVE CASES";
        }
        if (structure.kind() == StructureKind.DEPOT) return conditionText(condition) + "\n" + foodText(state, settlement.id());
        if (structure.kind() == StructureKind.WORKSHOP) return workshopText(state, settlement, condition);
        if (structure.kind() != StructureKind.HOUSING) return conditionText(condition);
        int residents = SettlementFacilityCapability.livingResidents(state, settlement.id());
        int beds = SettlementFacilityCapability.forStructure(state, structure).residentCapacity();
        return conditionText(condition) + "\n" + residents + " / " + beds + " RESIDENTS";
    }

    private static String foodText(FrontierWorldState state, SubjectId settlementId) {
        SettlementProvision provision = state.humanPopulation().provision(settlementId);
        int available = availableFood(state, settlementId);
        int reserve = reserveRequirement(state, settlementId);
        return switch (provision.status()) {
            case IDLE -> "FOOD REVIEW PENDING · " + available + " / " + reserve;
            case IN_PROGRESS -> "FOOD SERVING · " + provision.fulfilledRations() + " / " + provision.requiredRations();
            case SECURE -> "FOOD SECURE · " + available + " / " + reserve;
            case RATIONED -> "FOOD RATIONED · " + provision.fulfilledRations() + " / " + provision.requiredRations();
            case SHORTAGE -> "FOOD SHORTAGE · BREAD NEEDED";
            case CONFLICT -> "FOOD CONFLICT · INSPECT DEPOT";
        };
    }

    private static boolean foodRisk(FrontierWorldState state, SubjectId settlementId) {
        SettlementProvisionStatus status = state.humanPopulation().provision(settlementId).status();
        return status == SettlementProvisionStatus.RATIONED || status == SettlementProvisionStatus.SHORTAGE || status == SettlementProvisionStatus.CONFLICT;
    }

    private static int reserveRequirement(FrontierWorldState state, SubjectId settlementId) {
        int living = Math.toIntExact(state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE).count());
        SettlementProvision provision = state.humanPopulation().provision(settlementId);
        int pending = provision.status() == SettlementProvisionStatus.IN_PROGRESS ? provision.requiredRations() - provision.fulfilledRations() : 0;
        return Math.addExact(Math.multiplyExact(living, 2), pending);
    }

    private static int availableFood(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        return state.inventory().items().values().stream().filter(item -> "minecraft:bread".equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
    }

    /**
     * A workshop never invents a production state for presentation: it may describe only the
     * exact accepted market order currently bound to that workshop's durable job.  The company,
     * demand, price and buyer all remain canonical elsewhere; this is their compact local view.
     */
    private static String workshopText(FrontierWorldState state, Settlement settlement, StructureCondition condition) {
        if (condition != StructureCondition.INTACT) return conditionText(condition);
        MarketWorkOrder order = state.companies().market().workOrders().values().stream()
                .filter(candidate -> candidate.status() == MarketWorkOrderStatus.ACCEPTED)
                .filter(candidate -> state.productionJobs().containsKey(candidate.jobId()))
                .filter(candidate -> state.productionJobs().get(candidate.jobId()).facilityId().equals(workshopId(settlement)))
                .sorted(Comparator.comparing(MarketWorkOrder::id)).findFirst().orElse(null);
        if (order == null) return "OPERATIONAL";
        MarketDemand demand = state.companies().market().demands().get(order.demandId());
        if (demand == null) throw new IllegalStateException("accepted workshop order has no buyer demand");
        return "ORDER · " + demand.itemCount() + " " + itemName(demand.itemKind())
                + "\nFOR " + buyerName(state, demand.buyerId()) + " · " + credits(order.acceptedTotalPrice());
    }

    private static SubjectId workshopId(Settlement settlement) {
        return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.WORKSHOP)
                .map(SettlementStructure::id).sorted().findFirst().orElseThrow(() -> new IllegalStateException("settlement has no workshop"));
    }

    private static String buyerName(FrontierWorldState state, SubjectId buyerId) {
        return state.bootstrap().settlements().stream().filter(settlement -> settlement.id().equals(buyerId)).map(Settlement::displayName)
                .findFirst().orElse("BUYER DEPOT");
    }

    private static String itemName(String itemKind) {
        return switch (itemKind) {
            case "minecraft:bread" -> "BREAD";
            case "minecraft:wheat" -> "WHEAT";
            default -> "GOODS";
        };
    }

    private static String credits(FixedScalar amount) {
        long raw = amount.raw(); long whole = raw / FixedScalar.SCALE; long fraction = Math.abs(raw % FixedScalar.SCALE);
        if (fraction == 0L) return whole + " CREDITS";
        String decimal = String.format(java.util.Locale.ROOT, "%06d", fraction).replaceFirst("0+$", "");
        return whole + "." + decimal + " CREDITS";
    }

    private static void add(Map<SubjectId, FrontierObjectBoard> values, FrontierObjectBoard board) {
        if (values.putIfAbsent(board.ownerId(), board) != null) throw new IllegalArgumentException("duplicate object board: " + board.ownerId().value());
        if (values.size() > MAX_BOARDS) throw new IllegalArgumentException("object board limit exceeded");
    }
}
