package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** One exact fence for a physical intent; the intent ID is its immutable owner-version boundary. */
public final class FencedRecoveryPhysicalIntentSupport {
    private FencedRecoveryPhysicalIntentSupport() { }

    public static FencedRecoveryState prepared(FencedRecoveryState recovery, PhysicalIntent intent, FencedRecoveryAsset asset) {
        SubjectId id = bindingId(intent); long epoch = recovery.nextEpoch(id);
        return recovery.prepare(FencedRecoveryBinding.prepared(id, Objects.requireNonNull(asset, "family recovery asset"), intent.causeSubjectId(), 0L, epoch, false));
    }
    public static FencedRecoveryState transition(FencedRecoveryState recovery, PhysicalIntent intent, PhysicalIntentStatus status,
                                                 FencedRecoveryAsset asset) {
        SubjectId id = bindingId(intent); FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null || binding.asset() != Objects.requireNonNull(asset, "family recovery asset") || !binding.ownerId().equals(intent.causeSubjectId())) {
            throw new IllegalArgumentException("physical intent recovery authority is absent or stale");
        }
        long epoch = binding.authorityEpoch();
        return switch (status) {
            case RUNNING -> recovery.running(id, epoch);
            case CONFIRMED -> (binding.phase() == FencedRecoveryPhase.AMBIGUOUS
                    ? recovery.inspectedObserved(id, epoch) : recovery.observed(id, epoch)).confirm(id, epoch);
            case UNKNOWN_AFTER_RESTART -> recovery.ambiguous(id, epoch, "intent-restart-uninspected", FencedRecoveryDisposition.INSPECT);
            case CONFLICTED -> recovery.conflict(id, epoch, "terminal-physical-conflict");
            case PREPARED -> throw new IllegalArgumentException("physical intent cannot transition back to prepared");
        };
    }
    /** Retires a PREPARED effect only when the canonical reducer has composed its exact result. */
    public static FencedRecoveryState composed(FencedRecoveryState recovery, PhysicalIntent intent, FencedRecoveryAsset asset) {
        SubjectId id = bindingId(intent); FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null || binding.asset() != Objects.requireNonNull(asset, "family recovery asset") || !binding.ownerId().equals(intent.causeSubjectId())) {
            throw new IllegalArgumentException("physical intent composition authority is absent or stale");
        }
        return recovery.compose(id, binding.authorityEpoch());
    }
    /** A physical executor may start only while its exact PREPARED authority still exists. */
    public static void requirePreparedExecutionAuthority(FencedRecoveryState recovery, PhysicalIntent intent, FencedRecoveryAsset asset) {
        SubjectId id = bindingId(intent); FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null || binding.asset() != Objects.requireNonNull(asset, "family recovery asset") || !binding.ownerId().equals(intent.causeSubjectId())) {
            throw new IllegalArgumentException("physical intent execution authority is absent or stale");
        }
        binding.require(FencedRecoveryPhase.PREPARED);
    }
    public static SubjectId bindingId(PhysicalIntent intent) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(intent.id().value().getBytes(StandardCharsets.UTF_8));
            return new SubjectId("recovery:intent_" + java.util.HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required for physical intent recovery identity", unavailable);
        }
    }
}
