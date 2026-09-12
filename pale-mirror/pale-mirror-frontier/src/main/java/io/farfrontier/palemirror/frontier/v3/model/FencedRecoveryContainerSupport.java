package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Fences one exact physical container surface without treating its contents as rollbackable. */
public final class FencedRecoveryContainerSupport {
    private FencedRecoveryContainerSupport() { }
    public static FencedRecoveryState transition(FencedRecoveryState recovery, ContainerRecord container, ContainerSurfaceStatus status) {
        SubjectId id = new SubjectId("recovery:container_" + container.id().value().replace(':', '_'));
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
    private static FencedRecoveryBinding current(FencedRecoveryState recovery, SubjectId id, ContainerRecord container) {
        FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null || binding.asset() != FencedRecoveryAsset.CONTAINER || !binding.ownerId().equals(container.ownerId())) {
            throw new IllegalArgumentException("container recovery authority is absent or stale");
        }
        return binding;
    }
}
