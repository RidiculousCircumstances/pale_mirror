package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects only physical consequences the current source day has committed.
 *
 * <p>This is deliberately not a second combat planner. A breach exists
 * because the canonical attack receipt has a structural breach; ordinary
 * swarm pressure and settlement containment remain source-day effects without
 * inventing a blast. The NeoForge boundary may physically execute a descriptor
 * only while its target is HOT at this exact day boundary. Otherwise it records
 * a cold receipt and lets the changed canonical snapshot describe off-screen
 * aftermath.</p>
 */
final class ReferenceGrayboxEffectProjection {
    private ReferenceGrayboxEffectProjection() { }

    static List<ReferenceGrayboxSnapshot.Effect> from(
            ReferenceWorld world, Map<Integer, ReferenceGrayboxLayout.Rectangle> settlementAreas
    ) {
        List<ReferenceGrayboxSnapshot.Effect> result = new ArrayList<>();
        Map<Integer, Integer> ordinals = new LinkedHashMap<>();
        List<ReferenceCombatReceipt> combat = world.combatHistory().stream().filter(item -> item.day() == world.day())
                .sorted(Comparator.comparingInt(ReferenceCombatReceipt::swarmId)
                        .thenComparingInt(ReferenceCombatReceipt::settlementId)
                        .thenComparingDouble(ReferenceCombatReceipt::power)
                        .thenComparing(ReferenceCombatReceipt::composition))
                .toList();
        for (ReferenceCombatReceipt receipt : combat) {
            ReferenceGrayboxLayout.Rectangle area = requiredArea(settlementAreas, receipt.settlementId());
            int ordinal = ordinals.merge(receipt.settlementId(), 1, Integer::sum) - 1;
            boolean breach = receipt.structuralBreach() > 1.0e-9d;
            result.add(new ReferenceGrayboxSnapshot.Effect("combat:" + receipt.day() + ":" + receipt.swarmId() + ":"
                    + receipt.settlementId() + ":" + ordinal, breach ? "breach_bomb" : "swarm_assault",
                    "settlement:" + receipt.settlementId(), receipt.day(), effectPoint(area, ordinal), receipt.power(),
                    breach ? Math.min(4.0d, 1.5d + Math.sqrt(receipt.structuralBreach()) * .40d) : 0.0d,
                    "swarm=" + receipt.swarmId() + "; composition=" + receipt.composition() + "; damage=" + rounded(receipt.damage()),
                    breach ? "effect.breach" : "effect.assault"));
        }
        for (ReferenceContainmentReceipt receipt : world.containmentHistory().stream().filter(item -> item.day() == world.day())
                .sorted(Comparator.comparingInt(ReferenceContainmentReceipt::settlementId)).toList()) {
            ReferenceGrayboxLayout.Rectangle area = requiredArea(settlementAreas, receipt.settlementId());
            result.add(new ReferenceGrayboxSnapshot.Effect("containment:" + receipt.day() + ":" + receipt.settlementId(), "containment",
                    "settlement:" + receipt.settlementId(), receipt.day(), effectPoint(area, 7), receipt.removed(),
                    Math.min(8.0d, Math.max(1.0d, receipt.radius())), "ammo=" + rounded(receipt.ammo()) + "; tissue_removed="
                    + rounded(receipt.removed()), "effect.containment"));
        }
        return List.copyOf(result);
    }

    private static ReferenceGrayboxLayout.Rectangle requiredArea(
            Map<Integer, ReferenceGrayboxLayout.Rectangle> areas, int settlementId
    ) {
        ReferenceGrayboxLayout.Rectangle area = areas.get(settlementId);
        if (area == null) throw new IllegalStateException("settlement has no graybox area: " + settlementId);
        return area;
    }

    private static ReferenceGrayboxLayout.Point effectPoint(ReferenceGrayboxLayout.Rectangle area, int ordinal) {
        int width = Math.max(1, area.width() - 4);
        int depth = Math.max(1, area.depth() - 4);
        return new ReferenceGrayboxLayout.Point(area.x() + 2 + Math.floorMod(ordinal * 5, width),
                area.z() + 2 + Math.floorMod(ordinal * 7, depth));
    }

    private static String rounded(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
