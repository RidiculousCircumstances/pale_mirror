package io.farfrontier.palemirror.frontier.v3.model.security;
import java.util.*;
/** Physical contact identity is explicit. An absent entity is never a COLD opponent. */
public record CombatTarget(Kind kind, UUID entity, String dimension) {
    public enum Kind {
        PLAYER(1), HOSTILE_MOB(2);
        private final int tag; Kind(int tag) { this.tag=tag; } public int wireTag(){return tag;}
        public static Kind require(int tag) { for (var value:values()) if(value.tag==tag)return value;
            throw new IllegalArgumentException("unknown combat target kind"); }
    }
    public CombatTarget { Objects.requireNonNull(kind); Objects.requireNonNull(entity); Objects.requireNonNull(dimension);
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("invalid contact dimension"); }
}
