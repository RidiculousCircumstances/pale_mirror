package io.farfrontier.palemirror.internal.integration.spore;

import java.util.Arrays;
import java.util.Optional;

/** Version-pinned, intentionally small first roster from Spore 2.2.0j. */
enum SporeActorProfile {
    INFECTED_HUMAN("pale_mirror:spore_infected_human", "spore:inf_human"),
    BRAIOMIL("pale_mirror:spore_braiomil", "spore:braiomil");

    private final String id;
    private final String entityTypeId;

    SporeActorProfile(String id, String entityTypeId) {
        this.id = id;
        this.entityTypeId = entityTypeId;
    }

    String id() { return id; }
    String entityTypeId() { return entityTypeId; }

    static Optional<SporeActorProfile> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }
}
