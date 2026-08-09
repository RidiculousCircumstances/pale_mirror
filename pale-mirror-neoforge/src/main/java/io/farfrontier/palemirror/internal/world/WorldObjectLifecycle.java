package io.farfrontier.palemirror.internal.world;

/** Physical representation lifecycle; domain facilities remain the source of desired state. */
public enum WorldObjectLifecycle {
    ABSTRACT,
    REPRESENTED,
    ACTIVE
}
