package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Exact canonical association between one settlement-assault lease and its non-replayable strike. */
public final class SettlementAssaultStrikeReceiptBinding {
    private SettlementAssaultStrikeReceiptBinding() { }

    public static PhysicalIntentId intentId(FrontierWorldState state, SceneLease lease, SubjectId cause) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(lease, "lease"); Objects.requireNonNull(cause, "cause");
        if (!FrontierSceneBehaviors.isSettlementAssault(lease)) {
            throw new IllegalArgumentException("only a settlement-assault lease has this receipt binding");
        }
        return intentId(state.bootstrap().worldId(), cause, lease.id(), lease.revision());
    }

    /** Framed inputs prevent both delimiter collisions and cross-lease/revision adoption. */
    public static PhysicalIntentId intentId(WorldId world, SubjectId cause, SceneLeaseId leaseId, long revision) {
        Objects.requireNonNull(world, "world"); Objects.requireNonNull(cause, "cause"); Objects.requireNonNull(leaseId, "lease id");
        if (revision < 0L) throw new IllegalArgumentException("scene strike receipt revision must be non-negative");
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 is required for receipt identity", unavailable); }
        update(digest, "frontier-v3-settlement-assault-receipt-v1");
        update(digest, world.value()); update(digest, cause.value()); update(digest, leaseId.value());
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(revision).array());
        return new PhysicalIntentId("intent:scene-strike-assault-" + hex(digest.digest()));
    }

    public static boolean belongsToLease(FrontierWorldState state, SceneLease lease, PhysicalIntent intent) {
        Objects.requireNonNull(intent, "intent");
        return intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.id().equals(intentId(state, lease, intent.causeSubjectId()));
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) value.append(Character.forDigit((current >>> 4) & 15, 16)).append(Character.forDigit(current & 15, 16));
        return value.toString();
    }
}
