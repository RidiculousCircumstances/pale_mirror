package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;

/**
 * Bounded HOT-only latitude around one declared semantic movement goal.
 *
 * <p>The owner derives the permitted supports from known geometry and its retained goal.
 * Collision and local avoidance may vary inside this envelope; the provider may not
 * select another task or commit work progress. Existing topology-owned families may
 * still build a smaller envelope around their current and next checkpoint.</p>
 */
public record LocalNavigationEnvelope(Set<BlockPosition> permittedSupports) {
    // The physical provider caps a path at 54 nodes. A full three-height,
    // three-wide latitude for that many distinct nodes plus eight legal
    // stations needs at most (54 + 8) * 27 supports before overlap.
    private static final int MAX_SUPPORTS = 1_674;

    public LocalNavigationEnvelope {
        permittedSupports = Set.copyOf(Objects.requireNonNull(permittedSupports, "navigation envelope supports"));
        if (permittedSupports.isEmpty() || permittedSupports.size() > MAX_SUPPORTS) {
            throw new IllegalArgumentException("navigation envelope support bound is invalid");
        }
    }

    public boolean contains(BlockPosition observedSupport) {
        return permittedSupports.contains(Objects.requireNonNull(observedSupport, "observed support"));
    }

    /**
     * Derives the bounded HOT collision latitude for one already-retained edge.
     *
     * <p>The caller supplies the two canonical body positions; this factory deliberately
     * exposes neither a route search nor a mutable set of future checkpoints.</p>
     */
    public static LocalNavigationEnvelope around(BodyPosition current, BodyPosition next) {
        LinkedHashSet<BlockPosition> supports = new LinkedHashSet<>();
        addNeighborhood(supports, current); addNeighborhood(supports, next);
        return new LocalNavigationEnvelope(supports);
    }

    /** Ephemeral bounded HOT latitude around known geometry; the goal remains the only durable target. */
    public static LocalNavigationEnvelope along(List<SurfaceAnchor> knownPath, List<SurfaceAnchor> legalStations) {
        knownPath = List.copyOf(Objects.requireNonNull(knownPath, "known pedestrian path"));
        legalStations = List.copyOf(Objects.requireNonNull(legalStations, "legal pedestrian goal stations"));
        if (knownPath.isEmpty() || legalStations.isEmpty())
            throw new IllegalArgumentException("goal envelope needs a current support and declared station");
        LinkedHashSet<BlockPosition> supports = new LinkedHashSet<>();
        for (SurfaceAnchor anchor : knownPath) addNeighborhood(supports, anchor.standingBody());
        for (SurfaceAnchor anchor : legalStations) addNeighborhood(supports, anchor.standingBody());
        return new LocalNavigationEnvelope(supports);
    }

    /** A short Minecraft leg may round a corner or avoid a body two cells off its known centerline. */
    public static LocalNavigationEnvelope localLeg(List<SurfaceAnchor> knownLeg, SurfaceAnchor target) {
        knownLeg = List.copyOf(Objects.requireNonNull(knownLeg, "known local leg"));
        Objects.requireNonNull(target, "local leg target");
        if (knownLeg.isEmpty() || knownLeg.size() > 12 || !knownLeg.getLast().equals(target))
            throw new IllegalArgumentException("local leg needs at most twelve known supports ending at its target");
        LinkedHashSet<BlockPosition> supports = new LinkedHashSet<>();
        for (SurfaceAnchor anchor : knownLeg) addNeighborhood(supports, anchor.standingBody(), 2);
        return new LocalNavigationEnvelope(supports);
    }

    /** HOT-only physical re-probe when retained COLD knowledge cannot authorize a path. */
    public static LocalNavigationEnvelope between(BodyPosition retainedBody, List<SurfaceAnchor> legalStations) {
        Objects.requireNonNull(retainedBody, "retained navigation body");
        legalStations = List.copyOf(Objects.requireNonNull(legalStations, "navigation goal stations"));
        if (legalStations.isEmpty()) throw new IllegalArgumentException("physical re-probe needs a legal station");
        int minX = retainedBody.x(), maxX = retainedBody.x();
        int minY = retainedBody.y() - 1, maxY = minY;
        int minZ = retainedBody.z(), maxZ = retainedBody.z();
        for (SurfaceAnchor station : legalStations) {
            minX = Math.min(minX, station.x()); maxX = Math.max(maxX, station.x());
            minY = Math.min(minY, station.y()); maxY = Math.max(maxY, station.y());
            minZ = Math.min(minZ, station.z()); maxZ = Math.max(maxZ, station.z());
        }
        long volume = (long) (maxX - minX + 5) * (maxY - minY + 3) * (maxZ - minZ + 5);
        if (volume > MAX_SUPPORTS) throw new IllegalArgumentException("physical re-probe exceeds bounded support envelope");
        LinkedHashSet<BlockPosition> supports = new LinkedHashSet<>();
        for (int x = minX - 2; x <= maxX + 2; x++)
            for (int y = minY - 1; y <= maxY + 1; y++)
                for (int z = minZ - 2; z <= maxZ + 2; z++)
                    supports.add(new BlockPosition(x, y, z));
        return new LocalNavigationEnvelope(supports);
    }

    private static void addNeighborhood(Set<BlockPosition> supports, BodyPosition body) {
        addNeighborhood(supports, body, 1);
    }

    private static void addNeighborhood(Set<BlockPosition> supports, BodyPosition body, int radius) {
        // BodyPosition is the feet block *above* a support. Include a legal
        // one-block drop in support height as well as a one-block ascent;
        // centring this band on feet excluded ordinary lower-ground detours.
        for (int y = body.y() - 2; y <= body.y(); y++) {
            for (int x = body.x() - radius; x <= body.x() + radius; x++) {
                for (int z = body.z() - radius; z <= body.z() + radius; z++) supports.add(new BlockPosition(x, y, z));
            }
        }
    }
}
