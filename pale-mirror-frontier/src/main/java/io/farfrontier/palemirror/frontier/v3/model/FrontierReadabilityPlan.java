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
        state.bootstrap().settlements().forEach(settlement -> settlement.structures().forEach(structure -> {
            StructureCondition condition = state.structureConditions().get(structure.id());
            add(values, new FrontierObjectBoard(structure.id(), structureBoardPosition(structure, condition), tone(condition),
                    settlement.displayName() + "\n" + structureName(structure.kind()) + "\n" + conditionText(condition)));
        }));
        state.bootstrap().hive().organs().forEach(organ -> addOrgan(values, state, organ));
        state.hiveColony().addedOrgans().values().forEach(organ -> addOrgan(values, state, organ));
        return new FrontierReadabilityPlan(values);
    }

    public Map<SubjectId, FrontierObjectBoard> boards() { return boards; }

    private static void addOrgan(Map<SubjectId, FrontierObjectBoard> values, FrontierWorldState state, HiveOrgan organ) {
        boolean operational = state.isHiveOrganOperational(organ.id());
        add(values, new FrontierObjectBoard(organ.id(), organ.anchor().offset(0, 2, -3), operational ? FrontierObjectBoard.Tone.HIVE : FrontierObjectBoard.Tone.WARNING,
                "HIVE\n" + organName(organ.kind()) + "\n" + (operational ? "ACTIVE" : "DISABLED · REPAIR NEEDED")));
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

    private static FrontierObjectBoard.Tone tone(StructureCondition condition) {
        return condition == StructureCondition.INTACT ? FrontierObjectBoard.Tone.SETTLEMENT : FrontierObjectBoard.Tone.WARNING;
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

    private static void add(Map<SubjectId, FrontierObjectBoard> values, FrontierObjectBoard board) {
        if (values.putIfAbsent(board.ownerId(), board) != null) throw new IllegalArgumentException("duplicate object board: " + board.ownerId().value());
        if (values.size() > MAX_BOARDS) throw new IllegalArgumentException("object board limit exceeded");
    }
}
