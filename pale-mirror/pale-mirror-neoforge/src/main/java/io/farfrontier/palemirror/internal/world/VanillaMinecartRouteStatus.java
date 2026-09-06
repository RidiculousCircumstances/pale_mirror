package io.farfrontier.palemirror.internal.world;

/** Physical lifecycle only; canonical cargo remains in the domain RouteContract. */
public enum VanillaMinecartRouteStatus {
    PLANNED,
    BUILDING,
    VERIFYING,
    ACTIVE,
    BLOCKED,
    SUSPENDED,
    LEGACY
}
