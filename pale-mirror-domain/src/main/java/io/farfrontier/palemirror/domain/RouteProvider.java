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
    CREATE
}
