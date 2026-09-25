package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Explicit actor identity supplied by a canonical birth producer, not discovered from a roster. */
public record ActorBirthIdentity(SubjectId actorId, Kind kind) {
    public ActorBirthIdentity {
        Objects.requireNonNull(actorId, "birth actor");
        Objects.requireNonNull(kind, "birth actor kind");
    }
    public enum Kind {
        RESIDENT("resident"), BIOFORM("bioform");
        private final String wire;
        Kind(String wire) { this.wire = wire; }
        public String wire() { return wire; }
        public static Kind fromWire(String wire) {
            return switch (wire) {
                case "resident" -> RESIDENT;
                case "bioform" -> BIOFORM;
                default -> throw new IllegalArgumentException("unknown birth actor kind: " + wire);
            };
        }
    }
}
