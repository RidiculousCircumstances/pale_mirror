package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable nameplate placement over the local projected roof.
 *
 * <p>This is strictly a presentation layout: it reads the materializer ledger
 * to avoid embedding a label in owned blocks, without adding or changing a
 * source fact.</p>
 */
final class SourceGrayboxLabelPositions {
    // One extra block clears a player's eye line over low one-block claims,
    // while keeping the label tied to its local object rather than a sky plane.
    private static final int CLEARANCE = 3;
    private static final int STACK_GAP = 2;
    private final SourceGrayboxPresentationLedger ledger;
    private final Map<Column, Integer> nextByColumn = new LinkedHashMap<>();

    SourceGrayboxLabelPositions(SourceGrayboxPresentationLedger ledger) {
        this.ledger = ledger;
    }

    int nextY(int x, int z) {
        Column column = new Column(x, z);
        int result = nextByColumn.getOrDefault(column, baseline(x, z));
        nextByColumn.put(column, result + STACK_GAP);
        return result;
    }

    private int baseline(int x, int z) {
        return ledger.claims().stream()
                .filter(claim -> x >= claim.x() && x < claim.x() + claim.width()
                        && z >= claim.z() && z < claim.z() + claim.depth())
                .mapToInt(claim -> claim.y() + claim.height() - 1)
                .max().orElse(ReferenceGrayboxLayout.GROUND_Y) + CLEARANCE;
    }

    private record Column(int x, int z) { }
}
