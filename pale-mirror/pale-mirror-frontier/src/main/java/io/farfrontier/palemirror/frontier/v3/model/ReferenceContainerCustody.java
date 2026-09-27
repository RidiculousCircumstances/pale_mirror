package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReferenceMutationClosed;

/**
 * Pure contract shared by depot, hive-store and explicitly declared production-station consumers.
 *
 * <p>The original F0.2B depot/store families and explicitly declared production stations share
 * this physical boundary. A surface status is presentation history; only a live lease against
 * the current replica grants physical custody.
 */
public final class ReferenceContainerCustody {
    public static final SubjectId PROVIDER_ID = new SubjectId("provider:reference-container-adapter");
    private static final String DEPOT_KIND = "container.settlement-depot";
    private static final String HIVE_STORE_KIND = "container.hive-store";
    private static final String PRODUCTION_STATION_KIND = "container.production-station";

    private ReferenceContainerCustody() { }

    public static boolean isReferenceContainer(FrontierWorldState state, SubjectId containerId) {
        Objects.requireNonNull(state, "world state"); Objects.requireNonNull(containerId, "container id");
        ContainerRecord container = state.inventory().containers().get(containerId);
        return state.bootstrap().settlements().stream().anyMatch(settlement -> FrontierWorldState.depotId(settlement.id()).equals(containerId))
                || state.isHiveStore(containerId)
                || container != null && container.productionStation().isPresent();
    }

    public static String semanticKind(FrontierWorldState state, SubjectId containerId) {
        if (!isReferenceContainer(state, containerId)) throw new IllegalArgumentException("container is not an F0.2B reference scope");
        ContainerRecord container = state.inventory().containers().get(containerId);
        if (container != null && container.productionStation().isPresent()) return PRODUCTION_STATION_KIND;
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
        PhysicalCustodyLeaseStatus status = state.replicaCustody().custodyByScope().get(scopeId(containerId)).status();
        return status == PhysicalCustodyLeaseStatus.ACQUIRED || status == PhysicalCustodyLeaseStatus.CHECKPOINTED;
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

    /** Releases a checkpointed scope together with its unstarted exact production actuators. */
    public static FrontierWorldState release(FrontierWorldState state, PhysicalReplicaCustodyPayloads.CustodyReleased transition) {
        var lease = state.replicaCustody().custodyByScope().get(transition.scopeId());
        var custody = state.replicaCustody().release(transition.scopeId(), transition.expectedEpoch(),
                transition.expectedCanonicalRevision(), transition.expectedReplicaRevision());
        if (lease == null) throw new IllegalArgumentException("container release has no current custody");
        if (!boundFungibleSlots(state, lease.objectId()).isEmpty()) {
            throw new IllegalArgumentException("container release must first close its bound resource layout");
        }
        var released = ProductionTransformationStateSupport.releasePreparedForContainer(state, lease.objectId());
        return released.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
    }

    /**
     * Reduces one complete reference transition after an already-confirmed physical mutation.
     * The retained pre-mutation observation fences checkpoint/release; the successor fingerprint
     * is checked against canonical inventory. Shared resource layout and unstarted production
     * actuators are closed in the same transaction before the EXPECTED boundary is published.
     */
    public static FrontierWorldState closeConfirmedMutation(FrontierWorldState state, ReferenceMutationClosed transition) {
        Objects.requireNonNull(state, "reference mutation state"); Objects.requireNonNull(transition, "reference mutation transition");
        SubjectId containerId = transition.objectId();
        if (!isReferenceContainer(state, containerId) || !scopeId(containerId).equals(transition.scopeId())
                || !provenance(containerId).equals(transition.provenance())
                || !canonicalFingerprint(state, containerId).equals(transition.fingerprint())) {
            throw new IllegalArgumentException("reference mutation transition does not match its exact canonical successor");
        }
        PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(transition.scopeId());
        if (lease == null || !lease.live() || !lease.objectId().equals(containerId) || !lease.providerId().equals(PROVIDER_ID)
                || lease.authorityEpoch() != transition.expectedEpoch()) {
            throw new IllegalArgumentException("reference mutation transition has no exact live custody");
        }
        List<PhysicalStackBinding> bindings = boundFungibleSlots(state, containerId);
        Optional<SubjectId> accountId = oneBoundAccount(bindings);
        if (!accountId.equals(transition.releasedFungibleAccountId())
                || bindings.stream().anyMatch(binding -> binding.authorityEpoch() != lease.authorityEpoch())) {
            throw new IllegalArgumentException("reference mutation fungible disposition does not match custody epoch");
        }
        PhysicalReplicaCustodyState custody = state.replicaCustody();
        if (lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED) {
            custody = custody.checkpoint(transition.scopeId(), transition.expectedEpoch(), transition.expectedCanonicalRevision(), transition.expectedReplicaRevision());
        } else if (lease.status() != PhysicalCustodyLeaseStatus.CHECKPOINTED) {
            throw new IllegalArgumentException("reference mutation custody cannot close from its current lifecycle state");
        }
        PhysicalReplicaCustodyState closed = custody
                .release(transition.scopeId(), transition.expectedEpoch(), transition.expectedCanonicalRevision(), transition.expectedReplicaRevision())
                .emit(containerId, transition.expectedCanonicalRevision(), transition.expectedReplicaRevision(), transition.emittedCanonicalRevision(),
                        transition.fingerprint(), transition.provenance());
        FrontierWorldState released = accountId.map(id -> ProductionResourceCustody.release(state, id, transition.expectedEpoch())).orElse(state);
        released = ProductionTransformationStateSupport.releasePreparedForContainer(released, containerId);
        return released.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(closed));
    }

    /** Family-owned construction of the complete legal successor disposition. */
    public static ReferenceMutationClosed confirmedMutationTransition(FrontierWorldState state, SubjectId containerId,
                                                                       long emittedCanonicalRevision) {
        Objects.requireNonNull(state, "reference mutation state"); Objects.requireNonNull(containerId, "reference mutation container");
        PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(scopeId(containerId));
        if (lease == null) throw new IllegalArgumentException("reference mutation has no current custody");
        List<PhysicalStackBinding> bindings = boundFungibleSlots(state, containerId);
        if (bindings.stream().anyMatch(binding -> binding.authorityEpoch() != lease.authorityEpoch())) {
            throw new IllegalArgumentException("reference mutation has an epoch-mismatched fungible layout");
        }
        return new ReferenceMutationClosed(containerId, lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(),
                lease.expectedReplicaRevision(), emittedCanonicalRevision, canonicalFingerprint(state, containerId), provenance(containerId), oneBoundAccount(bindings));
    }

    private static List<PhysicalStackBinding> boundFungibleSlots(FrontierWorldState state, SubjectId containerId) {
        return state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> state.inventory().fungibleResources().accounts().get(binding.accountId()).custody() instanceof ResourceCustody.Container custody
                        && custody.containerId().equals(containerId)).toList();
    }

    private static Optional<SubjectId> oneBoundAccount(List<PhysicalStackBinding> bindings) {
        return bindings.stream().map(PhysicalStackBinding::accountId).distinct().reduce((left, right) -> {
            throw new IllegalArgumentException("reference mutation has multiple live fungible accounts");
        });
    }

    /** Canonical stock image; depot/store fungibles are independent of Vanilla slot placement. */
    public static String canonicalFingerprint(FrontierWorldState state, SubjectId containerId) {
        ContainerRecord container = state.inventory().containers().get(Objects.requireNonNull(containerId, "container id"));
        if (container == null) throw new IllegalArgumentException("unknown reference container");
        java.util.ArrayList<MaterialContainerImage.Slot> image = new java.util.ArrayList<>(container.slotCount());
        for (int slot = 0; slot < container.slotCount(); slot++) {
            ExactItemStack item = state.inventory().itemAt(containerId, slot).orElse(null);
            if (item != null) image.add(MaterialContainerImage.Slot.exact(slot, item.id().value(), item.itemKind(), item.count()));
            else {
                ProjectedFungibleSlot fungible = expectedFungibleSlot(state, containerId, slot).orElse(null);
                image.add(fungible == null ? MaterialContainerImage.Slot.empty(slot)
                        : MaterialContainerImage.Slot.fungible(slot, fungible.itemKind(), fungible.quantity()));
            }
        }
        return MaterialContainerImage.fingerprint(semanticKind(state, containerId), containerId.value(), layout(state, containerId), image);
    }

    /** Same semantic grammar as {@link #canonicalFingerprint}; the adapter supplies observed stock. */
    public static String observedFingerprint(FrontierWorldState state, SubjectId containerId, java.util.List<ObservedSlot> slots) {
        ContainerRecord container = state.inventory().containers().get(Objects.requireNonNull(containerId, "container id"));
        if (container == null || slots.size() != container.slotCount()) throw new IllegalArgumentException("observed reference slots are incomplete");
        java.util.ArrayList<MaterialContainerImage.Slot> image = new java.util.ArrayList<>(slots.size());
        for (int slot = 0; slot < container.slotCount(); slot++) {
            ObservedSlot observed = slots.get(slot);
            if (observed.slot() != slot) throw new IllegalArgumentException("observed reference slots are unordered");
            if (observed.empty()) image.add(MaterialContainerImage.Slot.empty(slot));
            else if (observed.fungible()) image.add(MaterialContainerImage.Slot.fungible(slot, observed.itemKind(), observed.count()));
            else image.add(MaterialContainerImage.Slot.exact(slot, observed.itemId(), observed.itemKind(), observed.count()));
        }
        return MaterialContainerImage.fingerprint(semanticKind(state, containerId), containerId.value(), layout(state, containerId), image);
    }

    /** Production stations declare fixed recipe ports; reference depot/store stock is bulk. */
    public static MaterialContainerImage.Layout layout(FrontierWorldState state, SubjectId containerId) {
        if (!isReferenceContainer(state, containerId)) throw new IllegalArgumentException("unknown reference material container");
        ContainerRecord container = state.inventory().containers().get(containerId);
        return container.productionStation().isPresent()
                ? MaterialContainerImage.Layout.FIXED_PORTS : MaterialContainerImage.Layout.BULK;
    }

    /**
     * The one fungible-slot grammar shared by initial COLD materialization and the reference
     * firewall.  Before a HOT observation binds a physical stack, the deterministic projected
     * slot is already canonical; once binding exists, only that exact transient address counts.
     */
    public static Optional<ProjectedFungibleSlot> expectedFungibleSlot(FrontierWorldState state, SubjectId containerId, int slot) {
        return expectedFungibleSlot(state.inventory(), containerId, slot);
    }

    /** The reservation owner can inspect an inventory's current projected slots during state construction. */
    static Optional<ProjectedFungibleSlot> expectedFungibleSlot(ExactInventory inventory, SubjectId containerId, int slot) {
        ContainerRecord container = inventory.containers().get(Objects.requireNonNull(containerId, "container id"));
        if (container == null || slot < 0 || slot >= container.slotCount()) throw new IllegalArgumentException("fungible reference slot is invalid");
        return Optional.ofNullable(expectedFungibleSlots(inventory, containerId).get(slot));
    }

    private static Map<Integer, ProjectedFungibleSlot> expectedFungibleSlots(ExactInventory inventory, SubjectId containerId) {
        ContainerRecord container = inventory.containers().get(containerId);
        if (container == null) throw new IllegalArgumentException("reference container is not declared");
        Map<Integer, ProjectedFungibleSlot> slots = new LinkedHashMap<>();
        List<PhysicalStackBinding> bindings = inventory.fungibleResources().bindings().values().stream()
                .filter(candidate -> candidate.address() instanceof PhysicalStackAddress.ContainerSlot address
                        && address.slot().containerId().equals(containerId)).toList();
        if (!bindings.isEmpty()) {
            for (PhysicalStackBinding binding : bindings) {
                PhysicalStackAddress.ContainerSlot address = (PhysicalStackAddress.ContainerSlot) binding.address();
                slots.put(address.slot().slot(), new ProjectedFungibleSlot(binding.itemKind(), binding.quantity()));
            }
            return Map.copyOf(slots);
        }
        List<CustodyAccount> accounts = inventory.fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container custody && custody.containerId().equals(containerId)).toList();
        if (accounts.size() != 1) return Map.of();
        Map<String, Integer> quantities = new LinkedHashMap<>();
        accounts.getFirst().lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLot lot = inventory.fungibleResources().lots().get(entry.getKey());
            if (lot == null) throw new IllegalStateException("reference custody account has unknown lot");
            quantities.merge(lot.itemKind(), entry.getValue(), Integer::sum);
        });
        if (container.productionStation().isPresent()) {
            ProductionStationSpec station = container.productionStation().orElseThrow();
            for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
                int slot = ProductionStationRecipe.portForItemKind(station, entry.getKey());
                if (entry.getValue() > 64 || inventory.itemAt(containerId, slot).isPresent()
                        || slots.putIfAbsent(slot, new ProjectedFungibleSlot(entry.getKey(), entry.getValue())) != null)
                    throw new IllegalStateException("station custody does not fit its declared recipe port");
            }
            return Map.copyOf(slots);
        }
        int next = 0;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                while (inventory.itemAt(containerId, next).isPresent()) next++;
                int quantity = Math.min(64, remaining);
                slots.put(next++, new ProjectedFungibleSlot(entry.getKey(), quantity));
                remaining -= quantity;
            }
        }
        return Map.copyOf(slots);
    }

    public record ProjectedFungibleSlot(String itemKind, int quantity) {
        public ProjectedFungibleSlot {
            if (itemKind == null || itemKind.isBlank() || quantity < 1 || quantity > 64) throw new IllegalArgumentException("projected fungible slot is invalid");
        }
    }

    public record ObservedSlot(int slot, String itemId, String itemKind, int count, boolean fungible) {
        public ObservedSlot {
            if (slot < 0 || count < 0) throw new IllegalArgumentException("observed slot is invalid");
            itemId = Objects.requireNonNull(itemId, "observed item id"); itemKind = Objects.requireNonNull(itemKind, "observed item kind");
            if ((count == 0) != (itemId.isEmpty() && itemKind.isEmpty() && !fungible)
                    || (count > 0 && (itemKind.isBlank() || (!fungible && itemId.isBlank()) || (fungible && !itemId.isEmpty())))) {
                throw new IllegalArgumentException("observed slot identity is invalid");
            }
        }
        public ObservedSlot(int slot, String itemId, String itemKind, int count) { this(slot, itemId, itemKind, count, false); }
        public static ObservedSlot empty(int slot) { return new ObservedSlot(slot, "", "", 0, false); }
        public static ObservedSlot fungible(int slot, String itemKind, int count) { return new ObservedSlot(slot, "", itemKind, count, true); }
        public boolean empty() { return count == 0; }
    }

}
