package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Fences one exact physical container surface without treating its contents as rollbackable. */
public final class FencedRecoveryContainerSupport {
    private FencedRecoveryContainerSupport() { }
    public static FencedRecoveryState transition(FencedRecoveryState recovery, ContainerRecord container,
                                                 ContainerSurface surface, ContainerSurfaceStatus status) {
        if (!surface.containerId().equals(container.id())) throw new IllegalArgumentException("container recovery surface is foreign");
        surface.transitionTo(status); // Validate the exact predecessor, not a guessed recovery phase.
        SubjectId id = bindingId(container);
        if (surface.status() == ContainerSurfaceStatus.UNMATERIALIZED && status == ContainerSurfaceStatus.CONFLICT) {
            // Admission failed before any physical claim or write. There is no attempt to
            // recover; keep the local surface conflict without manufacturing an authority.
            if (recovery.current().containsKey(id) || recovery.tombstones().containsKey(id))
                throw new IllegalArgumentException("unmaterialized container already retains physical authority");
            return recovery;
        }
        return switch (status) {
            case PREPARED -> recovery.prepare(FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.CONTAINER, container.ownerId(), 0L,
                    recovery.nextEpoch(id), true));
            case ACTIVE -> {
                FencedRecoveryBinding binding = current(recovery, id, container);
                yield recovery.running(id, binding.authorityEpoch());
            }
            case CONFLICT -> {
                FencedRecoveryBinding binding = current(recovery, id, container);
                yield recovery.ambiguous(id, binding.authorityEpoch(), "container-surface-conflict", FencedRecoveryDisposition.INSPECT);
            }
            case UNMATERIALIZED -> throw new IllegalArgumentException("container surface cannot recover to unmaterialized");
        };
    }
    /** Stable exact physical-surface identity; callers never infer a container fence from a slot. */
    public static SubjectId bindingId(ContainerRecord container) {
        return new SubjectId("recovery:container_" + container.id().value().replace(':', '_'));
    }
    private static FencedRecoveryBinding current(FencedRecoveryState recovery, SubjectId id, ContainerRecord container) {
        FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null || binding.asset() != FencedRecoveryAsset.CONTAINER || !binding.ownerId().equals(container.ownerId())) {
            throw new IllegalArgumentException("container recovery authority is absent or stale");
        }
        return binding;
    }
}
