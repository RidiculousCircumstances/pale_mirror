package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Bounded geometric proposals for external sites. Callers retain land/terrain admission. */
public final class ExteriorLandCandidates {
    public record Candidate(BlockPosition anchor, FacilityFacing facing) {}

    public static List<Candidate> around(
            BlockPosition center, int distance, int spacing, List<FacilityFacing> directions) {
        if (distance < 1
                || spacing < 1
                || directions.size() != 4
                || new HashSet<>(directions).size() != 4)
            throw new IllegalArgumentException("invalid exterior land search");
        var result = new ArrayList<Candidate>();
        for (int lateral : List.of(0, 1, -1, 2, -2, 3, -3))
            for (var facing : directions) {
                int sideways = Math.multiplyExact(spacing, lateral);
                result.add(
                        new Candidate(
                                center.offset(
                                        facing.x() * distance - facing.z() * sideways,
                                        0,
                                        facing.z() * distance + facing.x() * sideways),
                                facing));
            }
        return List.copyOf(result);
    }

    private ExteriorLandCandidates() {}
}
