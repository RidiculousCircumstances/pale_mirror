package io.farfrontier.palemirror.domain;

public enum RouteProvider {
    PALE_MIRROR,
    /**
     * PM-owned vanilla rail corridor. Its visual minecart never owns cargo;
     * persisted structural validation certifies the canonical abstract flow.
     */
    VANILLA_MINECART,
    /** Physical PM-owned carrier whose capacity still requires a typed commissioning receipt. */
    MANAGED_RAILWAY,
    CREATE;

    /**
     * Whether a successful observation describes persistent topology rather than a
     * time-bounded proof of service. A PM-authored vanilla corridor remains usable
     * until loaded physical evidence explicitly blocks it; a Create route still
     * requires renewed traversal evidence.
     */
    public boolean hasPersistentTopologyEvidence() {
        return this == VANILLA_MINECART;
    }
}
