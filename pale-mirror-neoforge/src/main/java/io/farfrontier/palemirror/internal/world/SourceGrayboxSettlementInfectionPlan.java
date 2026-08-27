package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, source-derived infection marks for settlement structures.
 *
 * <p>The territorial source cell remains the only infection authority. A
 * trace makes a short infected fascia, active infection spans the roofline,
 * and severe infection covers the visible roof. The plan owns no new disease
 * state and the normal presentation ledger retains any player obstruction.
 */
final class SourceGrayboxSettlementInfectionPlan {
    private static final int ROOF_Y = ReferenceGrayboxLayout.GROUND_Y + 3;
    private static final double MINIMUM = .01d;
    private static final double ACTIVE = .28d;
    private static final double SEVERE = .70d;

    private SourceGrayboxSettlementInfectionPlan() { }

    static List<SourceGrayboxPresentationPlan.Desired> from(ReferenceGrayboxSnapshot snapshot) {
        Map<String, ReferenceGrayboxSnapshot.Cell> cells = cellsByCoordinate(snapshot.cells());
        Map<Integer, ReferenceGrayboxSnapshot.Settlement> settlements = settlementsById(snapshot.settlements());
        return snapshot.facilities().stream().sorted(java.util.Comparator.comparing(ReferenceGrayboxSnapshot.Facility::id))
                .map(facility -> overlay(snapshot.stateRevision(), facility, settlements.get(facility.settlementId()), cells))
                .filter(java.util.Objects::nonNull).toList();
    }

    private static SourceGrayboxPresentationPlan.Desired overlay(String revision, ReferenceGrayboxSnapshot.Facility facility,
                                                                   ReferenceGrayboxSnapshot.Settlement settlement,
                                                                   Map<String, ReferenceGrayboxSnapshot.Cell> cells) {
        // A partial presentation fixture may deliberately materialize a
        // facility alone. There is then no canonical territorial owner from
        // which infection can be derived, so the safe result is no overlay,
        // never a guessed visual infection state.
        if (settlement == null) return null;
        ReferenceGrayboxSnapshot.Cell cell = cells.get(key(settlement.logicalX(), settlement.logicalY()));
        if (cell == null) {
            throw new IllegalStateException("settlement has no source infection cell: " + settlement.id());
        }
        double infection = infection(cell, settlement.id());
        if (infection < MINIMUM) return null;
        ReferenceGrayboxLayout.Rectangle area = facility.rectangle();
        Stage stage = stage(infection);
        return new SourceGrayboxPresentationPlan.Desired("infection-overlay:facility:" + facility.id(),
                "settlement:" + settlement.id(), "SETTLEMENT_INFECTION", revision, area.x(), ROOF_Y, area.z(),
                stage.width(area), stage.depth(area), 1, colour(cell, stage));
    }

    private static Map<String, ReferenceGrayboxSnapshot.Cell> cellsByCoordinate(List<ReferenceGrayboxSnapshot.Cell> cells) {
        Map<String, ReferenceGrayboxSnapshot.Cell> result = new LinkedHashMap<>();
        for (ReferenceGrayboxSnapshot.Cell cell : cells) {
            if (result.putIfAbsent(key(cell.x(), cell.y()), cell) != null) {
                throw new IllegalStateException("duplicate source infection cell: " + cell.x() + "," + cell.y());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, ReferenceGrayboxSnapshot.Settlement> settlementsById(List<ReferenceGrayboxSnapshot.Settlement> settlements) {
        Map<Integer, ReferenceGrayboxSnapshot.Settlement> result = new LinkedHashMap<>();
        for (ReferenceGrayboxSnapshot.Settlement settlement : settlements) {
            if (result.putIfAbsent(settlement.id(), settlement) != null) {
                throw new IllegalStateException("duplicate source settlement: " + settlement.id());
            }
        }
        return Map.copyOf(result);
    }

    private static double infection(ReferenceGrayboxSnapshot.Cell cell, int settlementId) {
        if (!Double.isFinite(cell.infection()) || cell.infection() < 0.0d || !Double.isFinite(cell.signal()) || cell.signal() < 0.0d) {
            throw new IllegalStateException("settlement infection source values are invalid: " + settlementId);
        }
        return cell.infection();
    }

    private static Stage stage(double infection) {
        if (infection >= SEVERE) return Stage.SEVERE;
        if (infection >= ACTIVE) return Stage.ACTIVE;
        return Stage.TRACE;
    }

    private static String colour(ReferenceGrayboxSnapshot.Cell cell, Stage stage) {
        if (cell.signal() >= MINIMUM) return stage == Stage.SEVERE ? "infection.settlement.signal_severe" : "infection.settlement.signal_active";
        return "infection.settlement." + stage.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String key(int x, int y) { return x + ":" + y; }

    private enum Stage {
        TRACE,
        ACTIVE,
        SEVERE;

        int width(ReferenceGrayboxLayout.Rectangle area) {
            return this == TRACE ? Math.min(2, area.width()) : area.width();
        }

        int depth(ReferenceGrayboxLayout.Rectangle area) {
            return this == SEVERE ? area.depth() : 1;
        }
    }
}
