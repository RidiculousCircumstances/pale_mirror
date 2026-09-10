package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Pure contract shared by the first depot and hive-store consumers of the replica kernel.
 *
 * <p>It deliberately identifies only the two F0.2B reference families.  A surface status is
 * presentation history; only a live lease against the current replica grants physical custody.
 */
public final class ReferenceContainerCustody {
    public static final SubjectId PROVIDER_ID = new SubjectId("provider:reference-container-adapter");
    private static final String DEPOT_KIND = "container.settlement-depot";
    private static final String HIVE_STORE_KIND = "container.hive-store";

    private ReferenceContainerCustody() { }

    public static boolean isReferenceContainer(FrontierWorldState state, SubjectId containerId) {
        Objects.requireNonNull(state, "world state"); Objects.requireNonNull(containerId, "container id");
        return state.bootstrap().settlements().stream().anyMatch(settlement -> FrontierWorldState.depotId(settlement.id()).equals(containerId))
                || state.isHiveStore(containerId);
    }

    public static String semanticKind(FrontierWorldState state, SubjectId containerId) {
        if (!isReferenceContainer(state, containerId)) throw new IllegalArgumentException("container is not an F0.2B reference scope");
        return state.isHiveStore(containerId) ? HIVE_STORE_KIND : DEPOT_KIND;
    }

    /** The smallest physical authority scope is the exact owned chest, never its settlement or nest. */
    public static SubjectId scopeId(SubjectId containerId) {
        return new SubjectId("custody:" + Objects.requireNonNull(containerId, "container id").value().replace(':', '-'));
    }

    public static String provenance(SubjectId containerId) {
        return "pale-mirror:reference-container:" + Objects.requireNonNull(containerId, "container id").value();
    }

    public static boolean hasLiveCustody(FrontierWorldState state, SubjectId containerId) {
        if (!isReferenceContainer(state, containerId)) return false;
        PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(scopeId(containerId));
        return lease != null && lease.live() && lease.objectId().equals(containerId)
                && lease.providerId().equals(PROVIDER_ID);
    }

    /** A retained unresolved epoch blocks its object but never authorizes another physical write. */
    public static boolean hasOperationalCustody(FrontierWorldState state, SubjectId containerId) {
        if (!hasLiveCustody(state, containerId)) return false;
        return state.replicaCustody().custodyByScope().get(scopeId(containerId)).status() != PhysicalCustodyLeaseStatus.UNRESOLVED;
    }

    public static boolean hasConflict(FrontierWorldState state, SubjectId containerId) {
        if (!isReferenceContainer(state, containerId)) return false;
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(containerId);
        return replica != null && replica.state() == PhysicalReplicaState.CONFLICT;
    }

    /**
     * A retained changed/foreign/missing observation is local evidence that this exact
     * container's canonical stock and capacity are unavailable.  It is deliberately not a
     * world, settlement, or hive-wide pause: an unobserved or safely released replica remains
     * eligible for ordinary COLD work.
     */
    public static boolean blocksCanonicalUse(FrontierWorldState state, SubjectId containerId) {
        return isReferenceContainer(state, containerId) && hasConflict(state, containerId);
    }

    /** Deterministic canonical fingerprint of the exact slots represented by one chest. */
    public static String canonicalFingerprint(FrontierWorldState state, SubjectId containerId) {
        ContainerRecord container = state.inventory().containers().get(Objects.requireNonNull(containerId, "container id"));
        if (container == null) throw new IllegalArgumentException("unknown reference container");
        StringBuilder value = new StringBuilder(semanticKind(state, containerId)).append('|').append(containerId.value()).append('|');
        for (int slot = 0; slot < container.slotCount(); slot++) {
            ExactItemStack item = state.inventory().itemAt(containerId, slot).orElse(null);
            value.append(slot).append(':');
            if (item == null) value.append("empty");
            else value.append(item.id().value()).append(':').append(item.itemKind()).append(':').append(item.count());
            value.append('|');
        }
        return sha256(value.toString());
    }

    /** Same slot grammar as {@link #canonicalFingerprint}; the adapter supplies observed item identity/kind/count. */
    public static String observedFingerprint(FrontierWorldState state, SubjectId containerId, java.util.List<ObservedSlot> slots) {
        ContainerRecord container = state.inventory().containers().get(Objects.requireNonNull(containerId, "container id"));
        if (container == null || slots.size() != container.slotCount()) throw new IllegalArgumentException("observed reference slots are incomplete");
        StringBuilder value = new StringBuilder(semanticKind(state, containerId)).append('|').append(containerId.value()).append('|');
        for (int slot = 0; slot < container.slotCount(); slot++) {
            ObservedSlot observed = slots.get(slot);
            if (observed.slot() != slot) throw new IllegalArgumentException("observed reference slots are unordered");
            value.append(slot).append(':');
            if (observed.empty()) value.append("empty");
            else value.append(observed.itemId()).append(':').append(observed.itemKind()).append(':').append(observed.count());
            value.append('|');
        }
        return sha256(value.toString());
    }

    public record ObservedSlot(int slot, String itemId, String itemKind, int count) {
        public ObservedSlot {
            if (slot < 0 || count < 0) throw new IllegalArgumentException("observed slot is invalid");
            itemId = Objects.requireNonNull(itemId, "observed item id"); itemKind = Objects.requireNonNull(itemKind, "observed item kind");
            if ((count == 0) != (itemId.isEmpty() && itemKind.isEmpty()) || (count > 0 && (itemId.isBlank() || itemKind.isBlank()))) {
                throw new IllegalArgumentException("observed slot identity is invalid");
            }
        }
        public static ObservedSlot empty(int slot) { return new ObservedSlot(slot, "", "", 0); }
        public boolean empty() { return count == 0; }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder("sha256:");
            for (byte octet : digest) encoded.append(String.format("%02x", octet));
            return encoded.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", unavailable);
        }
    }
}
