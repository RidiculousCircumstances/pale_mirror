package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import net.minecraft.core.BlockPos;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stable information-board placement over local projected geometry.
 *
 * <p>This is strictly a presentation layout: it reads the materializer ledger
 * to avoid embedding a label in owned blocks, without adding or changing a
 * source fact.</p>
 */
final class SourceGrayboxLabelPositions {
    // One extra block clears a player's eye line over low one-block claims,
    // while keeping the label tied to its local object rather than a sky plane.
    private static final int CLEARANCE = 3;
    private final SourceGrayboxPresentationLedger ledger;
    private final Map<Column, Integer> nextSlotByAnchor = new LinkedHashMap<>();
    private final Set<Column> occupiedBoardColumns = new LinkedHashSet<>();

    SourceGrayboxLabelPositions(SourceGrayboxPresentationLedger ledger) {
        this.ledger = ledger;
    }

    BlockPos next(int x, int z) {
        Column anchor = new Column(x, z);
        int ordinal = nextSlotByAnchor.getOrDefault(anchor, 0);
        while (true) {
            SourceGrayboxLabelSlots.Offset offset = SourceGrayboxLabelSlots.offset(ordinal++);
            Column candidate = new Column(x + offset.x(), z + offset.z());
            if (!occupiedBoardColumns.add(candidate)) continue;
            nextSlotByAnchor.put(anchor, ordinal);
            return new BlockPos(candidate.x(), baseline(candidate.x(), candidate.z()), candidate.z());
        }
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
