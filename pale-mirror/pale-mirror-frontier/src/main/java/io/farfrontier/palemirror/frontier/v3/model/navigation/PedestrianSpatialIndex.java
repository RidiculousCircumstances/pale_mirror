package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Conservative chunk candidates, never positions or authority. Exact as-of projection follows lookup. */
public final class PedestrianSpatialIndex {
    private static final int MAX_MEMBERSHIPS = 262_144;
    private record Chunk(int x, int z) {
        static Chunk at(int x, int z) { return new Chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)); }
    }
    private final Map<Chunk, Set<SubjectId>> chunks = new HashMap<>();
    private final Map<SubjectId, Set<Chunk>> footprints = new HashMap<>();
    private final Set<SubjectId> overflow = new HashSet<>();
    private int memberships;

    /** Index the whole retained segment, so crossing a loaded area needs no per-cell durable update. */
    public void replace(SubjectId actor, BodyPosition current, List<SurfaceAnchor> segment) {
        Objects.requireNonNull(actor); Objects.requireNonNull(current); Objects.requireNonNull(segment);
        if (segment.size() > TimedKnownRoute.MAX_SURFACES) throw new IllegalArgumentException("spatial segment exceeds movement retention");
        var occupied = new HashSet<Chunk>(); occupied.add(Chunk.at(current.x(), current.z()));
        for (var surface : segment) occupied.add(Chunk.at(surface.x(), surface.z()));
        remove(actor);
        if (memberships + occupied.size() > MAX_MEMBERSHIPS) {
            // Capacity pressure degrades to exact candidate filtering, never invisible actors.
            overflow.add(actor); return;
        }
        footprints.put(actor, occupied); memberships += occupied.size();
        for (var chunk : occupied) chunks.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(actor);
    }
    public void remove(SubjectId actor) {
        overflow.remove(actor); var previous = footprints.remove(actor);
        if (previous == null) return;
        memberships -= previous.size();
        for (var chunk : previous) {
            var actors = chunks.get(chunk); actors.remove(actor);
            if (actors.isEmpty()) chunks.remove(chunk);
        }
    }
    public List<SubjectId> candidates(int x, int z, int radius) {
        if (radius < 0 || radius > 4096) throw new IllegalArgumentException("spatial query exceeds bounded observer radius");
        var min = Chunk.at(Math.subtractExact(x, radius), Math.subtractExact(z, radius));
        var max = Chunk.at(Math.addExact(x, radius), Math.addExact(z, radius));
        var result = new TreeSet<>(overflow);
        for (int cx = min.x(); cx <= max.x(); cx++) for (int cz = min.z(); cz <= max.z(); cz++)
            result.addAll(chunks.getOrDefault(new Chunk(cx, cz), Set.of()));
        return List.copyOf(result);
    }
}
