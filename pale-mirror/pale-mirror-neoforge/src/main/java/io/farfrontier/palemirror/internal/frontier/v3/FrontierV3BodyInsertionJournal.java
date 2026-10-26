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
        FrontierV3ActorOwnerBinding binding();
        boolean current(FrontierV3AmbientCarrierLedger ledger);
        boolean reject(FrontierV3AmbientCarrierLedger ledger);
        net.minecraft.nbt.CompoundTag save();
        static Undo load(net.minecraft.nbt.CompoundTag tag) {
            if (tag.size() != 2 || !tag.contains("kind", net.minecraft.nbt.Tag.TAG_INT)
                    || !tag.contains("value", net.minecraft.nbt.Tag.TAG_COMPOUND)) throw new IllegalArgumentException("incomplete body undo");
            return switch (tag.getInt("kind")) {
                case 1 -> new First(FrontierV3ActorFirstAdmission.load(tag.getCompound("value")));
                case 2 -> new Adoption(FrontierV3ActorAdoption.load(tag.getCompound("value")));
                default -> throw new IllegalArgumentException("unknown body undo kind");
            };
        }
    }
    record First(FrontierV3ActorFirstAdmission pending) implements Undo {
        First { if (pending.phase() != FrontierV3ActorFirstAdmission.Phase.PENDING) throw new IllegalArgumentException("first undo is not pending"); }
        public FrontierV3ActorOwnerBinding binding() { return pending.attempt().orElseThrow(); }
        public net.minecraft.nbt.CompoundTag save() {
            var tag = new net.minecraft.nbt.CompoundTag(); tag.putInt("kind", 1); tag.put("value", pending.save()); return tag;
        }
        public boolean current(FrontierV3AmbientCarrierLedger ledger) {
            return ledger.firstAdmission(pending.identity().actorId()).filter(pending::equals).isPresent();
        }
        public boolean reject(FrontierV3AmbientCarrierLedger ledger) { return ledger.rejectUncreatedFirstAdmission(pending); }
    }
    record Adoption(FrontierV3ActorAdoption pending) implements Undo {
        public FrontierV3ActorOwnerBinding binding() { return pending.admittedBinding(); }
        public net.minecraft.nbt.CompoundTag save() {
            var tag = new net.minecraft.nbt.CompoundTag(); tag.putInt("kind", 2); tag.put("value", pending.save()); return tag;
        }
        public boolean current(FrontierV3AmbientCarrierLedger ledger) {
            return ledger.pendingAdoption(pending.admitted().actorId()).filter(pending::equals).isPresent();
        }
        public boolean reject(FrontierV3AmbientCarrierLedger ledger) { return ledger.rejectUncreatedAdoption(pending); }
    }
    static Ticket get(ServerLevel level, WorldId world, SubjectId actor) { return PENDING.getOrDefault(level, Map.of()).get(new Key(world, actor)); }
    static Ticket prepare(ServerLevel level, FrontierWorldState state, FrontierV3AmbientCarrierLedger ledger,
                          FrontierV3ActorOwnerBinding binding, FrontierV3ActorBodyController.Admission admission,
                          net.minecraft.world.entity.Mob privateBody) {
        var actor = binding.declaration().actorId();
        var key = new Key(state.bootstrap().worldId(), actor);
        var tickets = PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (tickets.containsKey(key)) throw new IllegalStateException("duplicate unstarted body ticket");
        if (tickets.size() >= FrontierV3AmbientPendingAdmissions.MAX_ENTRIES || !ledger.canRetainBodyOperation(actor)) return null;
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
        var port = FrontierV3OperationEntityParticipant.birth(privateBody);
        var operations = FrontierV3OperationLedger.get(level);
        var declaration = FrontierV3BodyAdmissionOperation.declaration(key.world(), binding, residence, undo, port);
        var retained = operations.find(declaration).orElse(null);
        if (retained != null) {
            // A crash can leave the common prepare without the independent actor
            // publication. Reattach only its exact declaration; never turn a
            // recovered prepare into this process's unattempted insertion ticket.
            if (retained.declaration().fingerprint().equals(declaration.fingerprint())
                    && !retained.cancelled() && !retained.ownerClosed() && retained.applied().isEmpty())
                ledger.retainBodyOperation(new FrontierV3BodyAdmissionOperation(key.world(), binding, residence, undo, retained, false));
            ledger.persist(level, key.world());
            FrontierV3OperationDiagnostics.result(level, retained, FrontierV3MinecraftOperation.Status.AMBIGUOUS);
            return null;
        }
        if (!operations.canPrepare(declaration)) {
            if (!undo.reject(ledger)) throw new IllegalStateException("body capacity refusal lost unused permission");
            ledger.persistAsync(level, key.world()); return null;
        }
        var operation = operations.prepare(declaration);
        ledger.retainBodyOperation(new FrontierV3BodyAdmissionOperation(key.world(), binding, residence, undo, operation, false));
        var ownerDurable = ledger.persistAsync(level, key.world());
        var nativeDurable = operations.persistAsync(level);
        var ticket = new Ticket(key.world(), binding, residence, admission, undo,
                ownerDurable.thenCombine(nativeDurable, (ownerSequence, nativeSequence) -> nativeSequence));
        tickets.put(key, ticket); return ticket;
    }
    static boolean consume(ServerLevel level, Ticket ticket) {
        if (!ticket.ready()) return false;
        var tickets = PENDING.get(level);
        return tickets != null && tickets.remove(new Key(ticket.world(), ticket.binding().declaration().actorId()), ticket);
    }
    static void reject(ServerLevel level, FrontierV3AmbientCarrierLedger ledger, Ticket ticket) {
        var retained = ledger.bodyOperation(ticket.binding().declaration().actorId()).orElseThrow();
        var operations = FrontierV3OperationLedger.get(level);
        if (!operations.sealUnattemptedCancellation(retained.operation().ticket()))
            throw new IllegalStateException("body cancellation has no unattempted native permission");
        // The body integration durably seals this refusal before restoring the
        // unused permission. A second caller-owned force adds no evidence and
        // waits for unrelated writes on the same journal.
        FrontierV3BodyOperationIntegration.reject(level, ticket.world(), ledger, retained);
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
