package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** One native-write acknowledgement lifecycle. Format adapters supply validated immutable
 * receipts; the coordinator knows no entity, chunk layout, container or business operation. */
final class FrontierV3NativeOperationSaves {
    private static final Map<ServerLevel, Map<FrontierV3OperationReceipt.Medium, Batch>> BATCHES = new WeakHashMap<>();
    private FrontierV3NativeOperationSaves() { }
    static void observe(ServerLevel level, FrontierV3OperationReceipt.Medium medium, long unit,
            List<FrontierV3OperationReceipt.NativeReceipt> receipts, CompletableFuture<Void> written) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("native save observation outside server thread");
        var ledger = FrontierV3OperationLedger.get(level);
        var unresolved = receipts.stream().filter(receipt -> ledger.find(receipt.ticket()).isPresent()).toList();
        BATCHES.computeIfAbsent(level, ignored -> new EnumMap<>(FrontierV3OperationReceipt.Medium.class))
                .computeIfAbsent(medium, ignored -> new Batch()).observe(unit, unresolved, written);
    }
    static void complete(ServerLevel level, FrontierV3OperationReceipt.Medium medium, boolean complete,
            Supplier<CompletableFuture<Void>> synchronize) {
        var families = BATCHES.get(level); var batch = families == null ? null : families.get(medium); if (batch == null) return;
        var ticket = batch.complete(complete, synchronize).orElse(null); if (ticket == null) return;
        ticket.write().saved().whenComplete((ignored, failure) -> level.getServer().execute(() -> {
            if (BATCHES.get(level) != families || families.get(medium) != batch
                    || !FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level)) return;
            if (failure != null) {
                io.farfrontier.palemirror.PaleMirrorMod.LOGGER.error("Native operation {} save failed; receipts retained", medium, failure); return;
            }
            var confirmed = batch.confirmed(ticket);
            if (!confirmed.isEmpty()) {
                var ledger = FrontierV3OperationLedger.get(level);
                for (var receipt : confirmed) {
                    // Absence is neither proof nor permission to prune an orphan marker.
                    if (ledger.find(receipt.ticket()).isEmpty()) continue;
                    ledger.saved(receipt);
                    if (ledger.require(receipt.ticket()).retirable()) ledger.retire(receipt.ticket());
                }
                // Native data is already durable. Submit its acknowledgement without
                // blocking the owner; marker pruning still requires durableReady().
                // A crash before this receipt leaves the saved native marker available
                // for recovery, not permission to replay the physical operation.
                ledger.persistAsync(level);
            }
            batch.accept(ticket);
        }));
    }
    static class Batch {
        private record Unit(List<FrontierV3OperationReceipt.NativeReceipt> receipts, CompletableFuture<Void> written) { }
        private final Map<Long, Unit> candidates = new HashMap<>();
        private int count;
        private long generation;
        private Ticket inFlight;
        record Write(long generation, CompletableFuture<Void> saved) { }
        record Ticket(Write write, Map<Long, Unit> units, List<FrontierV3OperationReceipt.NativeReceipt> receipts) { }
        void observe(long unit, List<FrontierV3OperationReceipt.NativeReceipt> receipts, CompletableFuture<Void> written) {
            Objects.requireNonNull(written);
            if (receipts.isEmpty() && !candidates.containsKey(unit)) return;
            var previous = candidates.get(unit);
            int nextCount = count - (previous == null ? 0 : previous.receipts().size()) + receipts.size();
            if (nextCount > FrontierV3OperationLedger.MAX_NATIVE_RECEIPTS)
                throw new IllegalStateException("native save contains more active receipts than admitted participants");
            count = nextCount;
            generation = Math.incrementExact(generation);
            if (receipts.isEmpty()) candidates.remove(unit);
            else candidates.put(unit, new Unit(List.copyOf(receipts), written));
        }
        Optional<Ticket> complete(boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
            if (inFlight != null) {
                if (!inFlight.write().saved().isDone() || inFlight.write().generation() == generation) return Optional.empty();
                inFlight = null;
            }
            if (!complete || candidates.isEmpty()) return Optional.empty();
            var units = Map.copyOf(candidates);
            var receipts = unique(units);
            var writes = CompletableFuture.allOf(units.values().stream().map(Unit::written).toArray(CompletableFuture[]::new));
            var saved = FrontierV3StoredEntityInventory.afterSuccessfulWriteAndSync(writes, synchronize);
            inFlight = new Ticket(new Write(generation, saved), units, receipts);
            return Optional.of(inFlight);
        }
        private static List<FrontierV3OperationReceipt.NativeReceipt> unique(Map<Long, Unit> units) {
            var counts = new HashMap<FrontierV3OperationReceipt.NativeReceipt, Integer>();
            units.values().stream().flatMap(unit -> unit.receipts().stream()).forEach(receipt -> counts.merge(receipt, 1, Integer::sum));
            return counts.entrySet().stream().filter(entry -> entry.getValue() == 1).map(Map.Entry::getKey).toList();
        }
        List<FrontierV3OperationReceipt.NativeReceipt> confirmed(Ticket ticket) {
            if (!current(ticket)) return List.of();
            var unique = new HashSet<>(unique(candidates));
            var originallyUnique = new HashSet<>(ticket.receipts());
            return ticket.units().entrySet().stream().filter(entry -> candidates.get(entry.getKey()) == entry.getValue())
                    .flatMap(entry -> entry.getValue().receipts().stream()).filter(unique::contains).filter(originallyUnique::contains).toList();
        }
        boolean current(Ticket ticket) { return ticket.write().saved().isDone() && !ticket.write().saved().isCompletedExceptionally()
                && ticket.units().entrySet().stream().anyMatch(entry -> candidates.get(entry.getKey()) == entry.getValue()); }
        void accept(Ticket ticket) {
            if (!ticket.write().saved().isDone() || ticket.write().saved().isCompletedExceptionally()) return;
            var accepted = new HashSet<>(confirmed(ticket));
            // Another chunk's new write cannot invalidate this chunk's exact positive
            // save. A newer write of THIS chunk must retain its own pending receipt.
            ticket.units().forEach((unit, written) -> {
                if (candidates.get(unit) == written && accepted.containsAll(written.receipts())) {
                    candidates.remove(unit); count -= written.receipts().size();
                }
            });
            // Conflicting copies remain tracked until an actual newer write replaces
            // them. Forgetting a duplicate would make the next lone copy look valid.
            if (inFlight == ticket && ticket.units().entrySet().stream().noneMatch(entry -> candidates.get(entry.getKey()) == entry.getValue()))
                inFlight = null;
        }
    }
}
