package io.farfrontier.palemirror.frontier.v3.model.combat;

import java.util.Objects;

/** Explicit personal preference identity, independent of profession, appointment and encounter role. */
public record CombatStyleId(String value) {
    public CombatStyleId {
        Objects.requireNonNull(value);
        if(value.length()>80 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("invalid combat style id");
    }
}
