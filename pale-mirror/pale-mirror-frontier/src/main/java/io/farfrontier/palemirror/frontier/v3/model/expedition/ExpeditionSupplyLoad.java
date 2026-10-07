package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Retained loading obligations, not another stock ledger. Only a custody receipt marks a load done. */
public record ExpeditionSupplyLoad(String foodKind, long forecastDurationTicks, long calculatedAtTick,
                                    Map<SubjectId, Integer> foodTargets, Map<SubjectId, SurfaceAnchor> assemblyStations, List<Allocation> allocations,
                                    long revision) {
    public ExpeditionSupplyLoad(String foodKind, long duration, long tick, Map<SubjectId, Integer> targets,
                                Map<SubjectId, SurfaceAnchor> assembly, List<Allocation> allocations) {
        this(foodKind, duration, tick, targets, assembly, allocations, 1);
    }
    public enum Outcome {
        RESERVED(0), LOADED(1), SOURCE_WITHDRAWN(2);
        private final int wireTag;
        Outcome(int tag) { wireTag = tag; }
        public int wireTag() { return wireTag; }
        public static Outcome fromWireTag(int tag) {
            for (var outcome : values()) if (outcome.wireTag == tag) return outcome;
            throw new IllegalArgumentException("unknown expedition loading outcome");
        }
    }
    public record Allocation(SubjectId claimId, SubjectId actorId, SubjectId sourceAccountId,
                             SubjectId destinationAccountId, ActorItemSlot slot, Map<SubjectId, Integer> lots,
                             Outcome outcome, Optional<ActorItemTransferStep> pending) {
        public Allocation {
            Objects.requireNonNull(claimId); Objects.requireNonNull(actorId); Objects.requireNonNull(sourceAccountId);
            Objects.requireNonNull(destinationAccountId); Objects.requireNonNull(slot);
            lots = Map.copyOf(lots); pending = Objects.requireNonNull(pending); Objects.requireNonNull(outcome);
            if (lots.isEmpty() || lots.size() > 64 || lots.values().stream().anyMatch(q -> q == null || q < 1 || q > 64)
                    || lots.values().stream().mapToInt(Integer::intValue).sum() > 64 || outcome != Outcome.RESERVED && pending.isPresent())
                throw new IllegalArgumentException("invalid expedition supply allocation");
        }
        public int quantity() { return lots.values().stream().mapToInt(Integer::intValue).sum(); }
        public boolean loaded() { return outcome == Outcome.LOADED; }
        public boolean withdrawn() { return outcome == Outcome.SOURCE_WITHDRAWN; }
        public Allocation prepare(ActorItemTransferStep step) {
            if (outcome != Outcome.RESERVED || pending.isPresent()) throw new IllegalArgumentException("supply allocation already applied or prepared");
            return new Allocation(claimId, actorId, sourceAccountId, destinationAccountId, slot, lots, Outcome.RESERVED, Optional.of(step));
        }
        public Allocation confirmed() {
            if (outcome != Outcome.RESERVED) throw new IllegalArgumentException("supply allocation already settled");
            return new Allocation(claimId, actorId, sourceAccountId, destinationAccountId, slot, lots, Outcome.LOADED, Optional.empty());
        }
        public Allocation sourceWithdrawn() {
            if (outcome != Outcome.RESERVED || pending.isPresent()) throw new IllegalArgumentException("supply source cannot withdraw an applied or prepared interaction");
            return new Allocation(claimId, actorId, sourceAccountId, destinationAccountId, slot, lots, Outcome.SOURCE_WITHDRAWN, Optional.empty());
        }
        Allocation unappliedAfterDeath() {
            if (outcome != Outcome.RESERVED || pending.isEmpty()) throw new IllegalArgumentException("death settlement has no prepared allocation");
            return new Allocation(claimId, actorId, sourceAccountId, destinationAccountId, slot, lots, Outcome.SOURCE_WITHDRAWN, Optional.empty());
        }
    }
    public ExpeditionSupplyLoad {
        Objects.requireNonNull(foodKind); foodTargets = Map.copyOf(foodTargets); assemblyStations = Map.copyOf(assemblyStations); allocations = List.copyOf(allocations);
        if (assemblyStations.isEmpty() || foodTargets.size() > 32 || assemblyStations.size() > 32 || forecastDurationTicks < 0 || calculatedAtTick < 0 || revision < 1
                || foodTargets.values().stream().anyMatch(q -> q == null || q < 0 || q > 640)
                || allocations.size() > 320 || allocations.stream().map(Allocation::claimId).distinct().count() != allocations.size()
                || allocations.stream().filter(a -> a.pending().isPresent()).count() > 1
                || !assemblyStations.keySet().containsAll(foodTargets.keySet())
                || assemblyStations.values().stream().distinct().count() != assemblyStations.size())
            throw new IllegalArgumentException("invalid retained expedition loading plan");
        for (var allocation : allocations) if (!assemblyStations.containsKey(allocation.actorId())
                || !(allocation.slot() instanceof ActorItemSlot.AttachedStorage) && !foodTargets.containsKey(allocation.actorId()))
            throw new IllegalArgumentException("supply allocation names a foreign participant");
        for (var actor : foodTargets.keySet()) {
            var owned = allocations.stream().filter(a -> a.actorId().equals(actor) && !(a.slot() instanceof ActorItemSlot.AttachedStorage)).toList();
            if (owned.stream().map(Allocation::slot).distinct().count() != owned.size()
                    || owned.stream().mapToInt(Allocation::quantity).sum() > foodTargets.get(actor))
                throw new IllegalArgumentException("supply loading promises overlap a personal slot or exceed its target");
        }
        var destinations = new java.util.HashMap<SubjectId, Allocation>();
        for (var a : allocations) {
            var other = destinations.putIfAbsent(a.destinationAccountId(), a);
            if (other != null && (!(a.slot() instanceof ActorItemSlot.AttachedStorage) || !a.slot().equals(other.slot()) || !a.actorId().equals(other.actorId())))
                throw new IllegalArgumentException("supply allocations alias unrelated destination accounts");
        }
    }
    public Optional<Allocation> next() { return allocations.stream().filter(a -> a.outcome() == Outcome.RESERVED).findFirst(); }
    public boolean complete() { return allocations.stream().allMatch(Allocation::loaded); }
    public boolean needsReplan() { return allocations.stream().anyMatch(Allocation::withdrawn); }
    public ExpeditionSupplyLoad replace(Allocation before, Allocation after) {
        if (!allocations.contains(before) || !before.claimId().equals(after.claimId()) || !before.actorId().equals(after.actorId())
                || !before.sourceAccountId().equals(after.sourceAccountId()) || !before.destinationAccountId().equals(after.destinationAccountId())
                || !before.slot().equals(after.slot()) || !before.lots().equals(after.lots()))
            throw new IllegalArgumentException("supply transition changes its exact allocation");
        return new ExpeditionSupplyLoad(foodKind, forecastDurationTicks, calculatedAtTick, foodTargets, assemblyStations,
                allocations.stream().map(a -> a.equals(before) ? after : a).toList(), revision);
    }
    public ActorContainerItemOrder order(SubjectId owner, ShipmentEndpoint source, Allocation allocation) {
        int ordinal = allocations.indexOf(allocation);
        if (ordinal < 0 || allocation.loaded()) throw new IllegalArgumentException("supply order lacks a current loading obligation");
        return new ActorContainerItemOrder(owner, allocation.actorId(), ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(allocation.sourceAccountId(), new ResourceCustody.Container(source.containerId()),
                        allocation.destinationAccountId(), allocation.slot() instanceof ActorItemSlot.AttachedStorage storage
                            ? new ResourceCustody.Container(storage.containerId()) : new ResourceCustody.Actor(allocation.actorId()), Optional.of(allocation.claimId()), foodKind,
                        allocation.lots()), new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(source.containerId()), source.station(),
                allocation.slot(), ordinal + 1L, revision);
    }
}
