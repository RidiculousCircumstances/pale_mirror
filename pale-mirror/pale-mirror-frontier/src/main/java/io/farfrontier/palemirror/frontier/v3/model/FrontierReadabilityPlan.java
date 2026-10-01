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
        FrontierReadabilityPlan baseline = compileStableBaseline(state);
        FrontierReadabilityPlan dynamicHive = compileDynamicHiveOverlay(state, MAX_BOARDS - baseline.boards().size());
        Map<SubjectId, FrontierObjectBoard> values = new LinkedHashMap<>(baseline.boards());
        dynamicHive.boards().values().forEach(board -> add(values, board, MAX_BOARDS));
        return new FrontierReadabilityPlan(values);
    }

    /**
     * The settlement, route, and bootstrap-hive boards are the stable presentation baseline.
     * Added organs are intentionally absent: their bounded overlay has its own exact compiler
     * so a growth completion cannot derive all world object geometry on one server turn.
     */
    public static FrontierReadabilityPlan compileStableBaseline(FrontierWorldState state) {
        Objects.requireNonNull(state, "state");
        Map<SubjectId, FrontierObjectBoard> values = new LinkedHashMap<>();
        Map<SubjectId, InfectionOverlayStage> contamination = contamination(state, FrontierGrayboxPlan.currentStableObjectCellsByOwner(state));
        state.bootstrap().settlements().forEach(settlement -> settlement.structures().forEach(structure -> {
            StructureCondition condition = state.structureConditions().get(structure.id());
            InfectionOverlayStage stage = contamination.get(structure.id());
            boolean quarantine = structure.kind() == StructureKind.INFIRMARY && state.humanPopulation().quarantined(settlement.id());
            boolean foodRisk = structure.kind() == StructureKind.DEPOT && foodRisk(state, settlement.id());
            add(values, new FrontierObjectBoard(structure.id(), structureBoardPosition(structure, condition), tone(condition, stage, quarantine, foodRisk), scope(structure.kind()),
                    settlement.displayName() + "\n" + structureName(structure.kind()) + "\n" + withContamination(facilityText(state, settlement, structure, condition), stage)));
        }));
        state.bootstrap().hive().organs().forEach(organ -> addOrgan(values, state, organ, contamination.get(organ.id())));
        state.resourceSiteDescriptors().values().forEach(site -> addResourceSite(values, state, site));
        addRouteNetwork(values, state);
        return new FrontierReadabilityPlan(values);
    }

    /**
     * Exact current boards for bounded added-hive organs only.  {@code capacity} preserves the
     * whole-plan board ceiling when this overlay is recombined with its stable baseline.
     */
    public static FrontierReadabilityPlan compileDynamicHiveOverlay(FrontierWorldState state, int capacity) {
        Objects.requireNonNull(state, "state");
        if (capacity < 0 || capacity > MAX_BOARDS) throw new IllegalArgumentException("invalid dynamic board capacity");
        Map<SubjectId, FrontierObjectBoard> values = new LinkedHashMap<>();
        Map<SubjectId, InfectionOverlayStage> contamination = contamination(state, FrontierGrayboxPlan.currentDynamicHiveObjectCellsByOwner(state));
        state.hiveColony().addedOrgans().values().forEach(organ -> addOrgan(values, state, organ, contamination.get(organ.id()), capacity));
        return new FrontierReadabilityPlan(values);
    }

    public Map<SubjectId, FrontierObjectBoard> boards() { return boards; }

    /**
     * Exact canonical dependencies of the board projection. Deliberately excludes ambient
     * lease positions, actor body positions, checkpoint revision and other continuously changing
     * execution data: none of those facts changes a board's slot, text or tone.  Keeping those
     * motion-only revisions out of the NeoForge board cursor prevents a full player-facing plan
     * recompilation on every server tick.
     */
    public static ReadabilityInput input(FrontierWorldState state) {
        Objects.requireNonNull(state, "readability state");
        boolean routeDamaged = state.physicalDeltas().values().stream()
                .anyMatch(delta -> delta.semanticTarget().filter(target -> target.kind() == PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK
                        && FrontierRouteNetwork.OWNER.equals(target.subjectId())).isPresent());
        boolean sceneConflict = state.sceneLeases().values().stream().anyMatch(lease -> lease.status() == SceneLeaseStatus.CONFLICT);
        boolean caravan = state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE);
        RouteConstruction construction = state.routeConstructions().values().stream()
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst().orElse(null);
        return new ReadabilityInput(state.bootstrap(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.hiveColony().addedOrgans(), state.physicalDeltas(), state.resourceSites(), state.routeTopology(), state.humanPopulation(),
                state.companies(), state.hiveColony().mobilizations(), state.strategicPlans().objectives(), ambientLeaseStatuses(state),
                new ActorConditionView(state.actorLocations()), routeDamaged, sceneConflict, caravan, construction);
    }

    private static Map<SubjectId, AmbientLeaseStatus> ambientLeaseStatuses(FrontierWorldState state) {
        Map<SubjectId, AmbientLeaseStatus> statuses = new LinkedHashMap<>();
        state.ambientLeases().forEach((actor, lease) -> statuses.put(actor, lease.status()));
        return Map.copyOf(statuses);
    }

    /** Immutable equality key for the bounded physical board cursor. */
    public record ReadabilityInput(FrontierBootstrap bootstrap, Map<SubjectId, StructureCondition> structureConditions,
                                   Map<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> infection,
                                   ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
                                   Map<SubjectId, HiveOrgan> addedOrgans, Map<BlockPosition, PhysicalDelta> physicalDeltas,
                                   ResourceSiteState resourceSites, RouteTopology routeTopology, HumanPopulation humanPopulation,
                                   CompanyRegistry companies, Map<SubjectId, HiveMobilization> mobilizations,
                                   Map<SubjectId, StrategicObjective> objectives, Map<SubjectId, AmbientLeaseStatus> ambientLeaseStatuses,
                                   ActorConditionView actorConditions,
                                   boolean routeDamaged, boolean sceneConflict, boolean caravan, RouteConstruction construction) {
        public ReadabilityInput {
            Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(structureConditions, "structure conditions");
            Objects.requireNonNull(infection, "infection"); Objects.requireNonNull(inventory, "inventory");
            Objects.requireNonNull(productionJobs, "production jobs"); Objects.requireNonNull(addedOrgans, "added organs");
            Objects.requireNonNull(physicalDeltas, "physical deltas"); Objects.requireNonNull(resourceSites, "resource sites");
            Objects.requireNonNull(routeTopology, "route topology"); Objects.requireNonNull(humanPopulation, "human population");
            Objects.requireNonNull(companies, "companies"); Objects.requireNonNull(mobilizations, "hive mobilizations");
            Objects.requireNonNull(objectives, "objectives"); Objects.requireNonNull(ambientLeaseStatuses, "ambient lease statuses");
            Objects.requireNonNull(actorConditions, "actor conditions");
        }

        /**
         * Fast retained-baseline check for the physical board cursor.  Canonical transitions
         * retain untouched immutable indexes by identity; actor coordinates are deliberately
         * compared through their condition-only view.  Added organs are excluded because they
         * have a bounded presentation overlay.
         */
        public boolean matchesStableBaseline(ReadabilityInput other) {
            Objects.requireNonNull(other, "readability input");
            return bootstrap == other.bootstrap && structureConditions == other.structureConditions && infection == other.infection
                    && inventory == other.inventory && productionJobs == other.productionJobs && physicalDeltas == other.physicalDeltas
                    && resourceSites == other.resourceSites && routeTopology == other.routeTopology && humanPopulation == other.humanPopulation
                    && companies == other.companies && mobilizations == other.mobilizations && objectives == other.objectives
                    && ambientLeaseStatuses.equals(other.ambientLeaseStatuses) && actorConditions.equals(other.actorConditions)
                    && routeDamaged == other.routeDamaged && sceneConflict == other.sceneConflict && caravan == other.caravan
                    && construction == other.construction;
        }

        /** Exact bounded overlay dependency; callers use this only after the stable check. */
        public boolean matchesDynamicHiveOverlay(ReadabilityInput other) {
            return addedOrgans == Objects.requireNonNull(other, "readability input").addedOrgans;
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
        addOrgan(values, state, organ, stage, MAX_BOARDS);
    }

    private static void addOrgan(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state, HiveOrgan organ, InfectionOverlayStage stage, int capacity) {
        boolean operational = state.isHiveOrganOperational(organ.id());
        MobilizationReadout mobilization = mobilizationReadout(state, organ);
        FrontierObjectBoard.Tone tone = stage == null && operational && mobilization.tone() == FrontierObjectBoard.Tone.HIVE
                ? FrontierObjectBoard.Tone.HIVE : FrontierObjectBoard.Tone.WARNING;
        add(values, new FrontierObjectBoard(organ.id(), organ.anchor().offset(0, 2, -3), tone,
                organ.kind() == HiveOrganKind.GANGLION ? FrontierObjectBoard.Scope.LANDMARK : FrontierObjectBoard.Scope.LOCAL,
                "HIVE\n" + organName(organ.kind()) + "\n" + withContamination(operational ? mobilization.text() : "DISABLED · REPAIR NEEDED", stage)), capacity);
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
            case DEPARTED -> new MobilizationReadout(FrontierObjectBoard.Tone.HIVE,
                    "EXPEDITION DEPARTED · " + mobilization.memberIds().size());
            case RETURNING -> new MobilizationReadout(FrontierObjectBoard.Tone.HIVE,
                    "EXPEDITION RETURNING · " + mobilization.returnAssembly().orElseThrow().members().size()
                            + "/" + mobilization.memberIds().size());
            case COMPLETED -> new MobilizationReadout(FrontierObjectBoard.Tone.HIVE,
                    "EXPEDITION RESULT RETAINED · " + mobilization.memberIds().size());
            case CONFLICT -> new MobilizationReadout(FrontierObjectBoard.Tone.WARNING, "WAKE INTERRUPTED · INSPECT COCOONS");
        };
    }

    private record MobilizationReadout(FrontierObjectBoard.Tone tone, String text) { }

    private static void addResourceSite(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state, ResourceSite site) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        add(values, new FrontierObjectBoard(site.id(), fieldBoardPosition(state, site), fieldTone(lifecycle), FrontierObjectBoard.Scope.LOCAL,
                settlement.displayName() + "\nWHEAT FIELD\n" + fieldStateText(state, site, lifecycle)));
    }

    private static void addRouteNetwork(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state) {
        boolean damaged = state.physicalDeltas().values().stream().anyMatch(delta ->
                delta.semanticTarget().filter(target -> target.kind() == PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK
                        && FrontierRouteNetwork.OWNER.equals(target.subjectId())).isPresent());
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

    private static BlockPosition fieldBoardPosition(FrontierWorldState state, ResourceSite site) {
        SettlementStructure farm = state.bootstrap().settlements().stream().flatMap(settlement -> settlement.structures().stream())
                .filter(structure -> structure.id().equals(site.facilityId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("field has no owning farm: " + site.id().value()));
        int minX = site.cropSlots().stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int maxX = site.cropSlots().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        int minZ = site.cropSlots().stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int maxZ = site.cropSlots().stream().mapToInt(BlockPosition::z).max().orElseThrow();
        int centreX = (minX + maxX) / 2, centreZ = (minZ + maxZ) / 2;
        // Place the physical readout on the field's exterior edge away from its own farm.  The
        // former fixed north offset could sit behind a mature crop wall whenever a generated
        // field used the east/west farm side, so the semantic board technically existed but was
        // unreadable from the ordinary approach.
        if (Math.abs(centreX - farm.anchor().x()) >= Math.abs(centreZ - farm.anchor().z())) {
            return new BlockPosition(centreX >= farm.anchor().x() ? maxX + 1 : minX - 1, site.cropSlots().getFirst().y() + 3, centreZ);
        }
        return new BlockPosition(centreX, site.cropSlots().getFirst().y() + 3, centreZ >= farm.anchor().z() ? maxZ + 1 : minZ - 1);
    }

    private static FrontierObjectBoard.Tone tone(StructureCondition condition, InfectionOverlayStage stage, boolean quarantine, boolean foodRisk) {
        return condition == StructureCondition.INTACT && stage == null && !quarantine && !foodRisk ? FrontierObjectBoard.Tone.SETTLEMENT : FrontierObjectBoard.Tone.WARNING;
    }

    private static FrontierObjectBoard.Scope scope(StructureKind kind) {
        return kind == StructureKind.HALL ? FrontierObjectBoard.Scope.LANDMARK : FrontierObjectBoard.Scope.LOCAL;
    }

    private static FrontierObjectBoard.Tone fieldTone(ResourceSiteLifecycle lifecycle) {
        if (lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).flatMap(ResourceSiteHarvestJob::navigationBlock).isPresent())
            return FrontierObjectBoard.Tone.WARNING;
        return switch (lifecycle.phase()) {
            case CONFLICT, DESTROYED -> FrontierObjectBoard.Tone.WARNING;
            case UNPREPARED, GROWING, READY, HARVESTING -> FrontierObjectBoard.Tone.SETTLEMENT;
        };
    }

    private static String structureName(StructureKind kind) {
        return switch (kind) {
            case HALL -> "TOWN HALL";
            case HOUSING -> "HOMES";
            case FARM -> "FARM";
            case WORKSHOP -> "BAKERY";
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

    private static String fieldStateText(FrontierWorldState state, ResourceSite site, ResourceSiteLifecycle lifecycle) {
        return switch (lifecycle.phase()) {
            case UNPREPARED -> "PREPARING SOIL · KEEP CLEAR";
            case GROWING -> "GROWING · STAGE " + lifecycle.growthStage() + "/" + ResourceSiteLifecycle.MATURE_STAGE;
            case READY -> readyFieldText(state, site);
            case HARVESTING -> lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                    .map(ResourceSiteHarvestJob.class::cast).flatMap(ResourceSiteHarvestJob::navigationBlock)
                    .map(block -> "HARVEST PAUSED · ROUTE BLOCKED\n" + switch (block.reason()) {
                        case TARGET_SUPPORT -> "SUPPORT MISSING";
                        case TARGET_CLEARANCE -> "PATH OBSTRUCTED";
                        case TARGET_MEDIUM -> "UNSUPPORTED MEDIUM";
                        case PATH_UNAVAILABLE -> "NO SAFE PATH";
                        case PATH_STALLED -> "FARMER STALLED";
                        case TARGET_CHUNK_UNLOADED -> "TARGET NOT LOADED";
                        case CONTINUATION_UNAVAILABLE -> "NO SAFE ROUTE TO NEXT WORK GOAL";
                        case OFF_CONTRACT -> "FARMER OUTSIDE SAFE ROUTE";
                        case UNSUPPORTED_CAPABILITY -> "NO PEDESTRIAN NAVIGATOR";
                        case SEARCH_BUDGET_EXHAUSTED -> "LOCAL PATH BUDGET EXCEEDED";
                        case KNOWN_GEOMETRY_UNAVAILABLE -> "AWAITING OBSERVED ROUTE";
                    }).orElse("HARVEST IN PROGRESS");
            case CONFLICT -> conflictFieldText(lifecycle);
            case DESTROYED -> "LOST · REBUILD NEEDED";
        };
    }

    private static String conflictFieldText(ResourceSiteLifecycle lifecycle) {
        return switch (lifecycle.conflictDisposition().orElseThrow().reason()) {
            case FIELD_ROUTE_BLOCKED_SUPPORT -> "HARVEST BLOCKED · ROUTE SUPPORT MISSING";
            case FIELD_ROUTE_BLOCKED_CLEARANCE -> "HARVEST BLOCKED · ROUTE OBSTRUCTED";
            case FIELD_ROUTE_BLOCKED_MEDIUM -> "HARVEST BLOCKED · WATER ON FIELD ROUTE";
            case FIELD_ROUTE_OFF_CONTRACT -> "HARVEST BLOCKED · WORKER OFF FIELD ROUTE";
            default -> "DAMAGED · REPAIR NEEDED";
        };
    }

    /**
     * A READY board is player-facing explanation, not a generic demand slogan.  A retained
     * facility objective or a restart-unknown ambient lease is a real owned blocker and must
     * not be misrepresented as the absence of a farmer who is already known to be eligible.
     */
    private static String readyFieldText(FrontierWorldState state, ResourceSite site) {
        long livingFarmers = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(site.settlementId()))
                .filter(resident -> resident.profession() == ResidentProfession.AGRICULTURAL_WORKER)
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE).count();
        boolean unknownFarmer = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(site.settlementId()))
                .filter(resident -> resident.profession() == ResidentProfession.AGRICULTURAL_WORKER)
                .filter(resident -> FrontierWorldStateSupport.workCapable(state, resident))
                .filter(resident -> HumanAssignmentProjection.compile(state).idle(resident.id()))
                .map(ResidentProfile::id).map(state.ambientLeases()::get)
                .anyMatch(lease -> lease != null && lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART);
        if (unknownFarmer) return "READY TO HARVEST · FARMER RECOVERY IN PROGRESS";
        if (state.firstFreeContainerSlot(FrontierWorldState.depotId(site.settlementId())).isEmpty())
            return "READY TO HARVEST · DEPOT FULL";
        boolean pendingExactHarvest = state.strategicPlans().tasks().values().stream()
                .anyMatch(task -> task.ownerId().equals(site.settlementId())
                        && task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE
                        && task.resourceSiteTarget().equals(java.util.Optional.of(site.id()))
                        && task.status() == StrategicTaskStatus.PENDING);
        if (pendingExactHarvest) return "READY TO HARVEST · HARVEST START PENDING";
        boolean facilityLaneActive = state.strategicPlans().objectives().values().stream()
                .anyMatch(objective -> objective.ownerId().equals(site.settlementId())
                        && objective.lane() == StrategicObjectiveLane.FACILITY
                        && objective.status() == StrategicObjectiveStatus.ACTIVE);
        if (facilityLaneActive) return "READY TO HARVEST · FACILITY LANE BUSY";
        return FrontierWorldStateSupport.availableFieldResident(state, site.settlementId(), ResidentProfession.AGRICULTURAL_WORKER).isPresent()
                ? "READY TO HARVEST · FARMER ASSIGNMENT PENDING"
                : "READY TO HARVEST · FARMERS NEEDED";
    }

    /** Pure semantic contact: a live infection column intersects one current object cell. */
    private static Map<SubjectId, InfectionOverlayStage> contamination(FrontierWorldState state) {
        return contamination(state, FrontierGrayboxPlan.currentObjectCellsByOwner(state));
    }

    private static Map<SubjectId, InfectionOverlayStage> contamination(FrontierWorldState state, Map<SubjectId, java.util.Set<BlockPosition>> objectCellsByOwner) {
        Map<SubjectId, InfectionOverlayStage> values = new LinkedHashMap<>();
        objectCellsByOwner.forEach((owner, positions) -> positions.forEach(position -> {
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
        int available = SettlementFoodPolicy.breadStock(state, settlementId);
        int reserve = SettlementFoodPolicy.reserveRequirement(state, settlementId);
        if (ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(settlementId)))
            return "FOOD CONFLICT · INSPECT DEPOT";
        int hungry = (int) state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(resident -> state.humanPopulation().nutrition(resident.id()).wantsFood(state.bootstrap().ruleset().residentLife())).count();
        if (available == 0) return "FOOD SHORTAGE · BREAD NEEDED";
        if (hungry > 0) return "RESIDENTS HUNGRY · " + hungry + " · BREAD " + available + " / " + reserve;
        return available < reserve ? "FOOD RESERVE LOW · " + available + " / " + reserve
                : "FOOD SECURE · " + available + " / " + reserve;
    }

    private static boolean foodRisk(FrontierWorldState state, SubjectId settlementId) {
        return ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(settlementId))
                || SettlementFoodPolicy.breadStock(state, settlementId) < SettlementFoodPolicy.reserveRequirement(state, settlementId)
                || state.humanPopulation().residents().values().stream()
                    .filter(resident -> resident.settlementId().equals(settlementId))
                    .anyMatch(resident -> state.humanPopulation().nutrition(resident.id()).wantsFood(state.bootstrap().ruleset().residentLife()));
    }

    /**
     * A workshop never invents a production state for presentation: it may describe only the
     * exact accepted market order currently bound to that workshop's durable job.  The company,
     * demand, price and buyer all remain canonical elsewhere; this is their compact local view.
     */
    private static String workshopText(FrontierWorldState state, Settlement settlement, StructureCondition condition) {
        if (condition != StructureCondition.INTACT) return conditionText(condition);
        ProductionJob job = state.productionJobs().values().stream()
                .filter(candidate -> candidate.facilityId().equals(workshopId(settlement)))
                .reduce((left, right) -> { throw new IllegalStateException("workshop has multiple retained production jobs"); })
                .orElse(null);
        MarketWorkOrder order = job == null ? null : state.companies().market().acceptedForJob(job.id()).orElse(null);
        if (order == null) return "OPERATIONAL";
        MarketDemand demand = state.companies().market().demands().get(order.demandId());
        if (demand == null) throw new IllegalStateException("accepted workshop order has no buyer demand");
        StrategicTask task = state.strategicPlans().tasks().get(job.taskId());
        boolean bakerLost = state.actorLocations().get(job.workerId()).condition().status() != ActorLifeStatus.ALIVE;
        String bakery = job.bakeryWork().map(work -> "\n" + (bakerLost ? "BAKER PAUSED · WORKER UNAVAILABLE"
                : task != null && task.status() == StrategicTaskStatus.BLOCKED ? "BAKER PAUSED · WORK BLOCKED"
                : work.block().map(block -> switch (block.reason()) {
            case SOURCE_CHANGED -> "BAKER PAUSED · WHEAT CHANGED";
            case HAND_MISMATCH -> "BAKER PAUSED · HAND MISMATCH";
            case DESTINATION_OCCUPIED -> "BAKER PAUSED · STORAGE FULL";
            case ROUTE_BLOCKED -> "BAKER PAUSED · ROUTE BLOCKED";
            case AMBIGUOUS_EFFECT -> "BAKER PAUSED · TRANSFER UNCERTAIN";
            case MACHINE_UNAVAILABLE -> "BAKER PAUSED · STATION UNAVAILABLE";
        }).orElseGet(() -> ProductionOutputCapacity.depotDeliveryUnavailable(state, job)
                ? "BAKER PAUSED · STORAGE FULL" : switch (work.phase()) {
            case DEPOT_PICKUP -> "BAKER · WHEAT PICKUP";
            case STATION_LOAD -> "BAKER · STATION INPUT";
            case PROCESSING -> "BAKING · " + work.completedWorkTicks() + " / "
                    + ProductionWorkProgress.REQUIRED_PROCESSING_TICKS;
            case STATION_UNLOAD -> "BAKER · BREAD READY";
            case DEPOT_DELIVERY -> "BAKER · BREAD DELIVERY";
            case DELIVERED -> "BAKER · BREAD STORED";
        }))).orElse("");
        return "ORDER · " + demand.itemCount() + " " + itemName(demand.itemKind())
                + "\nFOR " + buyerName(state, demand.buyerId()) + " · " + credits(order.acceptedTotalPrice()) + bakery;
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
        add(values, board, MAX_BOARDS);
    }

    private static void add(Map<SubjectId, FrontierObjectBoard> values, FrontierObjectBoard board, int capacity) {
        if (values.putIfAbsent(board.ownerId(), board) != null) throw new IllegalArgumentException("duplicate object board: " + board.ownerId().value());
        if (values.size() > capacity) throw new IllegalArgumentException("object board limit exceeded");
    }
}
