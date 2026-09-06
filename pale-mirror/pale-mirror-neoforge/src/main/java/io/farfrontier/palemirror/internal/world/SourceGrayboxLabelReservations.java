package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, Minecraft-free eye-level reservation for source-graybox boards.
 *
 * <p>A reservation is presentation-only. It does not claim a block, move a
 * source object, or affect whether a board exists; it merely makes the next
 * local board choose another horizontal slot before two billboards would read
 * as one overlapping label.</p>
 */
final class SourceGrayboxLabelReservations {
    private final List<Reservation> values = new ArrayList<>();

    boolean available(String id, int x, int z) {
        int requested = SourceGrayboxLabelStyle.reservationRadius(id);
        for (Reservation existing : values) {
            int distance = Math.max(requested, SourceGrayboxLabelStyle.reservationRadius(existing.id()));
            // GameTest and real-world coordinates are allowed far from origin.
            // Squaring them as int makes an unrelated distant board appear to
            // collide after overflow, eventually exhausting every slot.
            long dx = (long) x - existing.x();
            long dz = (long) z - existing.z();
            long minimumSquared = (long) distance * distance;
            if (dx * dx + dz * dz < minimumSquared) return false;
        }
        return true;
    }

    void reserve(String id, int x, int z) {
        if (!available(id, x, z)) throw new IllegalArgumentException("source graybox label reservation overlaps");
        values.add(new Reservation(id, x, z));
    }

    private record Reservation(String id, int x, int z) { }
}
