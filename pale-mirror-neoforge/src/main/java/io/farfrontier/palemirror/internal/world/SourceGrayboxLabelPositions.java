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
    /** Inspect enough nearby board slots to step around a mast or metric tower. */
    private static final int SLOT_LOOKAHEAD = 64;
    private final SourceGrayboxPresentationLedger ledger;
    private final Map<Column, Integer> nextSlotByAnchor = new LinkedHashMap<>();
    private final Set<Column> occupiedBoardColumns = new LinkedHashSet<>();

    SourceGrayboxLabelPositions(SourceGrayboxPresentationLedger ledger) {
        this.ledger = ledger;
    }

    BlockPos next(String id, int x, int z) {
        // An operation board is a live field marker.  Unlike a static
        // infrastructure board, it must remain directly above the activity
        // cube so the player can unambiguously associate the event with the
        // nearby one-to-one participants.
        if (id.startsWith("activity:")) return new BlockPos(x, baseline(x, z), z);
        Column anchor = new Column(x, z);
        int firstOrdinal = nextSlotByAnchor.getOrDefault(anchor, 0);
        while (true) {
            Candidate best = null;
            for (int ordinal = firstOrdinal; ordinal < firstOrdinal + SLOT_LOOKAHEAD; ordinal++) {
                SourceGrayboxLabelSlots.Offset offset = SourceGrayboxLabelSlots.offset(ordinal);
                Column candidate = new Column(x + offset.x(), z + offset.z());
                if (occupiedBoardColumns.contains(candidate)) continue;
                Candidate proposed = new Candidate(candidate, ordinal, baseline(candidate.x(), candidate.z()));
                if (best == null || proposed.y() < best.y()) best = proposed;
                // Ground plus clearance is the lowest legal board position,
                // so later slots cannot produce a better local reading height.
                if (best.y() == ReferenceGrayboxLayout.GROUND_Y + CLEARANCE) break;
            }
            if (best != null) {
                occupiedBoardColumns.add(best.column());
                nextSlotByAnchor.put(anchor, best.ordinal() + 1);
                return new BlockPos(best.column().x(), best.y(), best.column().z());
            }
            firstOrdinal += SLOT_LOOKAHEAD;
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

    private record Candidate(Column column, int ordinal, int y) { }
}
