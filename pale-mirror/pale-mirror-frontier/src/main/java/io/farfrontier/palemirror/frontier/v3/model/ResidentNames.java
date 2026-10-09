package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Genesis/birth presentation data producer. A name is never an identity or dispatch key. */
public final class ResidentNames {
    private static final List<String> GIVEN = List.of("Alex", "Mila", "Robin", "Nora", "Eli", "Ada",
            "Leon", "Iris", "Owen", "Lena", "Theo", "Vera", "Finn", "Rosa", "Noel", "Maya");
    private static final List<String> FAMILY = List.of("Reed", "Stone", "Vale", "Brooks", "Wood", "Hill",
            "Baker", "Moss", "Lake", "Fox", "Bell", "Wells", "Ash", "Rowan", "West", "Field");
    private ResidentNames() { }

    public static String create(long seed, SubjectId residentId) {
        Objects.requireNonNull(residentId, "named resident");
        long hash = seed ^ residentId.value().hashCode();
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        hash ^= hash >>> 31;
        return GIVEN.get((int) Math.floorMod(hash, GIVEN.size())) + " "
                + FAMILY.get((int) Math.floorMod(hash >>> 16, FAMILY.size()));
    }
}
