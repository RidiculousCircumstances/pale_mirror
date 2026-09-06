package io.farfrontier.palemirror.api;

/** Immutable provider readiness; clients are admitted only after the genesis catalog is ready. */
public record GenesisReadiness(State state, int catalogVersion, String catalogHash, String diagnostic) {
    public GenesisReadiness {
        if (catalogVersion < 0) throw new IllegalArgumentException("catalogVersion must not be negative");
        catalogHash = catalogHash == null ? "" : catalogHash;
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public boolean ready() { return state == State.READY; }

    public static GenesisReadiness planning() {
        return new GenesisReadiness(State.PLANNING, 0, "", "Authored regions are being planned");
    }

    public static GenesisReadiness failed(String diagnostic) {
        return new GenesisReadiness(State.FAILED, 0, "", diagnostic);
    }

    public enum State {
        PLANNING,
        COMPILING,
        READY,
        FAILED
    }
}
