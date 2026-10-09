package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Common body's unstarted insertion, not an activity lease or physical custody grant.
 * Server-thread ticket ownership is independent of the disk worker. Only the retained
 * durable prefix permits consuming a ticket and attempting insertion once.
 */
final class FrontierV3BodyInsertionJournal {
    private record Key(WorldId world, SubjectId actor) { }
    private static final Map<ServerLevel, Map<Key, Ticket>> PENDING = new WeakHashMap<>();
    private FrontierV3BodyInsertionJournal() { }
    record Ticket(WorldId world, FrontierV3ActorOwnerBinding binding, long residence, FrontierV3ActorBodyController.Admission admission,
                  Undo undo, CompletableFuture<Long> durable) {
        Ticket {
            Objects.requireNonNull(world); Objects.requireNonNull(binding); Objects.requireNonNull(admission); Objects.requireNonNull(undo); Objects.requireNonNull(durable);
            if (residence < 1 || admission == FrontierV3ActorBodyController.Admission.FIRST && !(undo instanceof First)
                    || admission == FrontierV3ActorBodyController.Admission.RECONSTRUCTION && !(undo instanceof Adoption)
                    || admission == FrontierV3ActorBodyController.Admission.DEFERRED || admission == FrontierV3ActorBodyController.Admission.CONFLICT)
                throw new IllegalArgumentException("incomplete unstarted insertion declaration");
        }
        boolean ready() { if (!durable.isDone()) return false; durable.join(); return true; }
        boolean current(FrontierV3AmbientCarrierLedger ledger) {
            return ledger.currentBodyResidence(binding.declaration().actorId(), residence) && undo.current(ledger);
        }
    }
    sealed interface Undo permits First, Adoption {
        boolean current(FrontierV3AmbientCarrierLedger ledger);
        boolean reject(FrontierV3AmbientCarrierLedger ledger);
    }
    record First(FrontierV3ActorFirstAdmission pending) implements Undo {
        public boolean current(FrontierV3AmbientCarrierLedger ledger) {
            return ledger.firstAdmission(pending.identity().actorId()).filter(pending::equals).isPresent();
        }
        public boolean reject(FrontierV3AmbientCarrierLedger ledger) { return ledger.rejectUncreatedFirstAdmission(pending); }
    }
    record Adoption(FrontierV3ActorAdoption pending) implements Undo {
        public boolean current(FrontierV3AmbientCarrierLedger ledger) {
            return ledger.pendingAdoption(pending.admitted().actorId()).filter(pending::equals).isPresent();
        }
        public boolean reject(FrontierV3AmbientCarrierLedger ledger) { return ledger.rejectUncreatedAdoption(pending); }
    }
    static Ticket get(ServerLevel level, WorldId world, SubjectId actor) { return PENDING.getOrDefault(level, Map.of()).get(new Key(world, actor)); }
    static Ticket prepare(ServerLevel level, FrontierWorldState state, FrontierV3AmbientCarrierLedger ledger,
                          FrontierV3ActorOwnerBinding binding, FrontierV3ActorBodyController.Admission admission) {
        var actor = binding.declaration().actorId();
        var key = new Key(state.bootstrap().worldId(), actor);
        var tickets = PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (tickets.containsKey(key)) throw new IllegalStateException("duplicate unstarted body ticket");
        if (tickets.size() >= FrontierV3AmbientPendingAdmissions.MAX_ENTRIES) return null;
        Undo undo = switch (admission) {
            case FIRST -> {
                if (!ledger.beginFirstAdmission(binding)) yield null;
                yield new First(ledger.firstAdmission(actor).orElseThrow());
            }
            case RECONSTRUCTION -> {
                if (!ledger.adopt(binding)) yield null;
                yield new Adoption(ledger.pendingAdoption(actor).orElseThrow());
            }
            case DEFERRED, CONFLICT -> null;
        };
        if (undo == null) return null;
        long residence = ledger.beginBodyResidence(binding.declaration());
        var ticket = new Ticket(key.world(), binding, residence, admission, undo, ledger.persistAsync(level, key.world()));
        tickets.put(key, ticket); return ticket;
    }
    static boolean consume(ServerLevel level, Ticket ticket) {
        if (!ticket.ready()) return false;
        var tickets = PENDING.get(level);
        return tickets != null && tickets.remove(new Key(ticket.world(), ticket.binding().declaration().actorId()), ticket);
    }
    static void reject(ServerLevel level, FrontierV3AmbientCarrierLedger ledger, Ticket ticket) {
        if (!ticket.undo().reject(ledger)) throw new IllegalStateException("uncreated body ticket lost exact predecessor");
        var tickets = PENDING.get(level);
        if (tickets == null || !tickets.remove(new Key(ticket.world(), ticket.binding().declaration().actorId()), ticket))
            throw new IllegalStateException("uncreated body ticket lost insertion ownership");
    }
    static void cancelAll(ServerLevel level, WorldId world, FrontierV3AmbientCarrierLedger ledger) {
        var tickets = PENDING.get(level);
        if (tickets == null) return;
        for (var ticket : List.copyOf(tickets.values())) if (ticket.world().equals(world)) reject(level, ledger, ticket);
        if (tickets.isEmpty()) PENDING.remove(level);
    }
}
