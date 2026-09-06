package io.farfrontier.palemirror.frontier.v3.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure deterministic perimeter placement for the exact bioforms of one hive nest.
 *
 * <p>A bioform hand-off point is part of the physical contract, not an abstract swarm centre.
 * It therefore stays outside every intact organ cell and its two body-clearance cells.  The
 * Minecraft executor may still defer a player/world obstruction; it must not make a silent
 * alternate canonical slot.</p>
 */
final class FrontierHiveActorSlots {
    private static final int FIRST_RING_RADIUS = 6;
    private static final int RING_INCREMENT = 3;
    private static final int MAX_RING_RADIUS = 256;

    private FrontierHiveActorSlots() { }

    static List<BlockPosition> slots(WorldBounds bounds, HiveNest nest, List<HiveOrgan> organs, int count) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(nest, "nest"); Objects.requireNonNull(organs, "organs");
        if (count < 0) throw new IllegalArgumentException("bioform placement count must not be negative");
        Set<BlockPosition> occupancy = FrontierGrayboxPlan.intactOrganOccupancy(organs.stream()
                .filter(organ -> organ.nestId().equals(nest.id())).toList());
        List<BlockPosition> accepted = new ArrayList<>(count);
        if (count == 0) return List.of();
        for (int radius = FIRST_RING_RADIUS; radius <= MAX_RING_RADIUS; radius += RING_INCREMENT) {
            for (int x = -radius; x <= radius; x += RING_INCREMENT) {
                if (accept(bounds, occupancy, nest.anchor().offset(x, 0, -radius), accepted, count)) return List.copyOf(accepted);
            }
            for (int z = -radius + RING_INCREMENT; z <= radius; z += RING_INCREMENT) {
                if (accept(bounds, occupancy, nest.anchor().offset(radius, 0, z), accepted, count)) return List.copyOf(accepted);
            }
            for (int x = radius - RING_INCREMENT; x >= -radius; x -= RING_INCREMENT) {
                if (accept(bounds, occupancy, nest.anchor().offset(x, 0, radius), accepted, count)) return List.copyOf(accepted);
            }
            for (int z = radius - RING_INCREMENT; z > -radius; z -= RING_INCREMENT) {
                if (accept(bounds, occupancy, nest.anchor().offset(-radius, 0, z), accepted, count)) return List.copyOf(accepted);
            }
        }
        throw new IllegalStateException("frontier hive nest has no bounded free bioform placement slots for count " + count);
    }

    private static boolean accept(WorldBounds bounds, Set<BlockPosition> occupancy, BlockPosition candidate,
                                  List<BlockPosition> accepted, int count) {
        // Candidate is the support cell. A standing Zombie's collision volume reaches almost
        // two full cells above its feet, so reserve support + feet + both head-overlap cells.
        // Treating only the first two cells as clear produced a physically embedded top ring
        // beside a tall BROOD organ on the outer perimeter.
        if (!bounds.contains(candidate) || occupancy.contains(candidate) || occupancy.contains(candidate.offset(0, 1, 0))
                || occupancy.contains(candidate.offset(0, 2, 0)) || occupancy.contains(candidate.offset(0, 3, 0))) return false;
        accepted.add(candidate); return accepted.size() == count;
    }
}
