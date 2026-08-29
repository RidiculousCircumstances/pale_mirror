package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

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
            add(values, new FrontierObjectBoard(structure.id(), structureBoardPosition(structure, condition), tone(condition, stage),
                    settlement.displayName() + "\n" + structureName(structure.kind()) + "\n" + withContamination(facilityText(state, settlement, structure, condition), stage)));
        }));
        state.bootstrap().hive().organs().forEach(organ -> addOrgan(values, state, organ, contamination.get(organ.id())));
        state.hiveColony().addedOrgans().values().forEach(organ -> addOrgan(values, state, organ, contamination.get(organ.id())));
        FrontierResourceSitePlan.compile(state.bootstrap()).values().forEach(site -> addResourceSite(values, state, site));
        addRouteNetwork(values, state);
        return new FrontierReadabilityPlan(values);
    }

    public Map<SubjectId, FrontierObjectBoard> boards() { return boards; }

    private static void addOrgan(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state, HiveOrgan organ, InfectionOverlayStage stage) {
        boolean operational = state.isHiveOrganOperational(organ.id());
        add(values, new FrontierObjectBoard(organ.id(), organ.anchor().offset(0, 2, -3), stage == null && operational ? FrontierObjectBoard.Tone.HIVE : FrontierObjectBoard.Tone.WARNING,
                "HIVE\n" + organName(organ.kind()) + "\n" + withContamination(operational ? "ACTIVE" : "DISABLED · REPAIR NEEDED", stage)));
    }

    private static void addResourceSite(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state, ResourceSite site) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        add(values, new FrontierObjectBoard(site.id(), fieldBoardPosition(site), fieldTone(lifecycle.phase()),
                settlement.displayName() + "\nWHEAT FIELD\n" + fieldStateText(lifecycle)));
    }

    private static void addRouteNetwork(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state) {
        boolean damaged = state.physicalDeltas().values().stream().anyMatch(delta ->
                delta.ownerId().equals(java.util.Optional.of(FrontierRouteNetwork.OWNER)));
        boolean sceneConflict = state.sceneLeases().values().stream().anyMatch(lease -> lease.status() == SceneLeaseStatus.CONFLICT);
        boolean caravan = state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE);
        FrontierObjectBoard.Tone tone = damaged || sceneConflict ? FrontierObjectBoard.Tone.WARNING : FrontierObjectBoard.Tone.SETTLEMENT;
        String stateText = damaged ? "ROUTE DAMAGE · PATROL NEEDED" : sceneConflict ? "SCENE BLOCKED · KEEP CLEAR"
                : caravan ? "CARAVAN EN ROUTE" : "ACTIVE · 12 SETTLEMENTS";
        add(values, new FrontierObjectBoard(FrontierRouteNetwork.OWNER,
                FrontierRouteNetwork.maintenanceContainerPosition(state.bootstrap()).offset(0, 3, -3), tone,
                "FRONTIER ROUTES\nNETWORK\n" + stateText));
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

    private static FrontierObjectBoard.Tone tone(StructureCondition condition, InfectionOverlayStage stage) {
        return condition == StructureCondition.INTACT && stage == null ? FrontierObjectBoard.Tone.SETTLEMENT : FrontierObjectBoard.Tone.WARNING;
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
            case HEART -> "HEART";
            case BROOD -> "BROOD";
            case STORE -> "STORE";
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
        FrontierGrayboxPlan.compile(state).cells().values().forEach(cell -> {
            var intensity = state.infection().get(InfectionCell.at(cell.position()));
            if (intensity != null && intensity.value().raw() > 0L) {
                values.merge(cell.ownerId(), InfectionOverlayStage.fromRaw(intensity.value().raw()),
                        (left, right) -> left.ordinal() >= right.ordinal() ? left : right);
            }
        });
        return Map.copyOf(values);
    }

    private static String withContamination(String stateText, InfectionOverlayStage stage) {
        return stage == null ? stateText : stateText + "\nINFECTED\n" + stage.name();
    }

    private static String facilityText(FrontierWorldState state, Settlement settlement, SettlementStructure structure, StructureCondition condition) {
        if (structure.kind() != StructureKind.HOUSING) return conditionText(condition);
        int residents = SettlementFacilityCapability.livingResidents(state, settlement.id());
        int beds = SettlementFacilityCapability.forStructure(state, structure).residentCapacity();
        return conditionText(condition) + "\n" + residents + " / " + beds + " RESIDENTS";
    }

    private static void add(Map<SubjectId, FrontierObjectBoard> values, FrontierObjectBoard board) {
        if (values.putIfAbsent(board.ownerId(), board) != null) throw new IllegalArgumentException("duplicate object board: " + board.ownerId().value());
        if (values.size() > MAX_BOARDS) throw new IllegalArgumentException("object board limit exceeded");
    }
}
