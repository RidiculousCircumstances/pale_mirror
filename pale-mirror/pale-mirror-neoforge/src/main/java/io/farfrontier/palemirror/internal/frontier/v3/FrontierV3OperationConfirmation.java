package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Joins the canonical command receipt to the physical journal without treating their
 * two independent WAL writes as one transaction. Neither payload nor job is interpreted. */
final class FrontierV3OperationConfirmation {
    enum Outcome { CONFIRMED, AWAITING_NATIVE_EVIDENCE, AWAITING_PHYSICAL_DURABILITY, AWAITING_CANONICAL_COMMIT, REJECTED }
    interface Canonical {
        void pin(CommandId id);
        void release(CommandId id);
        java.util.Optional<CommandReceipt> receipt(CommandId id);
        CommandResult submit(CommandId id, SubjectId owner, FrontierPayload consequence);
        default void afterCommit(CommandResult result, Runnable continuation) {
            if (result instanceof CommandResult.Accepted) continuation.run();
        }
    }
    interface Durability {
        boolean prepared(UUID ticket);
        void closed(Runnable release);
    }
    private static Durability synchronous(Runnable force) {
        return new Durability() {
            public boolean prepared(UUID ticket) { force.run(); return true; }
            public void closed(Runnable release) { force.run(); release.run(); }
        };
    }
    private static Durability durability(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime,
            FrontierV3OperationLedger ledger) {
        if (!runtime.inOrdinaryPersistenceTurn()) return synchronous(() -> ledger.persist(level));
        return new Durability() {
            public boolean prepared(UUID ticket) {
                ledger.persistAsync(level);
                return ledger.durableOperation(ticket);
            }
            public void closed(Runnable release) { ledger.afterDurable(ledger.persistAsync(level), release); }
        };
    }
    private FrontierV3OperationConfirmation() { }
    static boolean submit(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket, FrontierPayload consequence) {
        return submit(level, runtime, ticket, consequence, java.util.Optional.empty());
    }
    /** Owner declares that a true canonical rejection is an invariant violation;
     * missing native evidence and queued durability remain ordinary waits. */
    static boolean submitRequired(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket, FrontierPayload consequence) {
        var ledger = FrontierV3OperationLedger.get(level);
        if (!FrontierV3MinecraftOperation.recoverOutcome(level, ticket)) return false;
        return submit(ledger, () -> ledger.persist(level), canonical(level, runtime, java.util.Optional.empty(), true), ticket, consequence);
    }
    static boolean submit(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket,
            FrontierPayload consequence, Runnable afterCommit) {
        boolean accepted = submit(level, runtime, ticket, consequence);
        if (accepted) afterCommit.run();
        else FrontierV3OperationLedger.get(level).ownerCommand(ticket)
                .ifPresent(command -> runtime.afterCommandReceipt(command.id(), afterCommit));
        return accepted;
    }
    static boolean submit(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket, FrontierPayload consequence,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        var ledger = FrontierV3OperationLedger.get(level);
        if (!FrontierV3MinecraftOperation.recoverOutcome(level, ticket)) return false;
        return submitOutcome(ledger, durability(level, runtime, ledger), canonical(level, runtime, binding), ticket, consequence) == Outcome.CONFIRMED;
    }
    /** Waiting is not a rejected consequence or permission to replay the native effect. */
    static Outcome submitOutcome(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket, FrontierPayload consequence) {
        var ledger = FrontierV3OperationLedger.get(level);
        if (!FrontierV3MinecraftOperation.recoverOutcome(level, ticket)) return Outcome.AWAITING_NATIVE_EVIDENCE;
        return submitOutcome(ledger, durability(level, runtime, ledger), canonical(level, runtime, java.util.Optional.empty()), ticket, consequence);
    }
    static boolean submit(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical, UUID ticket, FrontierPayload consequence) {
        return submitOutcome(ledger, force, canonical, ticket, consequence) == Outcome.CONFIRMED;
    }
    static Outcome submitOutcome(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical, UUID ticket, FrontierPayload consequence) {
        return submitOutcome(ledger, synchronous(force), canonical, ticket, consequence);
    }
    static Outcome submitOutcome(FrontierV3OperationLedger ledger, Durability durability, Canonical canonical, UUID ticket, FrontierPayload consequence) {
        var operation = ledger.require(ticket);
        if (!ledger.nativeOutcomeAvailable(ticket)) return Outcome.AWAITING_NATIVE_EVIDENCE;
        if (operation.ownerClosed()) return Outcome.CONFIRMED;
        var retained = ledger.ownerCommand(ticket);
        var command = retained.orElseGet(() -> new FrontierV3OperationLedger.OwnerCommand(
                new CommandId("physical-operation:" + ticket), consequence));
        if (!command.fingerprint().equals(fingerprint(consequence)))
            throw new IllegalArgumentException("changed physical consequence requires explicit owner recovery");
        ledger.bindOwnerCommand(ticket, command);
        if (!durability.prepared(ticket)) return Outcome.AWAITING_PHYSICAL_DURABILITY;
        canonical.pin(command.id());
        if (canonical.receipt(command.id()).isEmpty()) {
            var result = canonical.submit(command.id(), operation.declaration().owner(), consequence);
            if (!(result instanceof CommandResult.Accepted)) {
                if (result instanceof CommandResult.Pending pending && pending.reason() == CommandResult.Pending.Reason.DURABILITY
                        && command.closure() == FrontierV3OperationLedger.OwnerCommand.Closure.AUTOMATIC)
                    canonical.afterCommit(result, () -> close(ledger, durability, canonical, ticket, command));
                // Synchronous rejection owns no canonical receipt. Retaining an absent
                // command here would let unrelated waiting operations exhaust the pin pool.
                if (result instanceof CommandResult.Rejected
                        || result instanceof CommandResult.Pending pending && pending.reason() == CommandResult.Pending.Reason.PREDECESSOR)
                    canonical.release(command.id());
                return result instanceof CommandResult.Rejected ? Outcome.REJECTED : Outcome.AWAITING_CANONICAL_COMMIT;
            }
        }
        if (command.closure() == FrontierV3OperationLedger.OwnerCommand.Closure.AUTOMATIC)
            close(ledger, durability, canonical, ticket, command);
        return Outcome.CONFIRMED;
    }
    /** Canonical acceptance does not discharge an owner's retained aftermath.
     * The caller supplies that completion; the coordinator checks the exact command. */
    static boolean completeOwner(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket) {
        var ledger = FrontierV3OperationLedger.get(level);
        return completeOwner(ledger, () -> ledger.persist(level), canonical(level, runtime, java.util.Optional.empty()), ticket);
    }
    static boolean completeOwner(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical, UUID ticket) {
        var command = ledger.ownerCommand(ticket).orElseThrow();
        if (command.closure() != FrontierV3OperationLedger.OwnerCommand.Closure.EXPLICIT_OWNER)
            throw new IllegalArgumentException("operation does not declare explicit owner closure");
        canonical.pin(command.id());
        if (canonical.receipt(command.id()).isEmpty()) { canonical.release(command.id()); return false; }
        close(ledger, force, canonical, ticket, command); return true;
    }
    /** The owner validates its recovery consequence. The coordinator first proves that
     * the pinned prior command was not accepted; it never replays the native effect. */
    static boolean submitRecovery(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket,
            FrontierPayload consequence, java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        var ledger = FrontierV3OperationLedger.get(level);
        if (!FrontierV3MinecraftOperation.recoverOutcome(level, ticket)) return false;
        return submitRecovery(ledger, durability(level, runtime, ledger), canonical(level, runtime, binding), ticket, consequence);
    }
    static boolean submitRecovery(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical,
            UUID ticket, FrontierPayload consequence) {
        return submitRecovery(ledger, synchronous(force), canonical, ticket, consequence);
    }
    static boolean submitRecovery(FrontierV3OperationLedger ledger, Durability durability, Canonical canonical,
            UUID ticket, FrontierPayload consequence) {
        if (!ledger.nativeOutcomeAvailable(ticket)) return false;
        if (ledger.require(ticket).ownerClosed()) return true;
        var retained = ledger.ownerCommand(ticket);
        if (retained.isPresent()) {
            var previous = retained.orElseThrow(); canonical.pin(previous.id());
            if (canonical.receipt(previous.id()).isPresent()) {
                if (previous.closure() == FrontierV3OperationLedger.OwnerCommand.Closure.AUTOMATIC)
                    close(ledger, durability, canonical, ticket, previous);
                return true;
            }
            canonical.release(previous.id()); // No accepted history to pin while waiting for the physical binding.
            String digest = fingerprint(consequence);
            if (!previous.fingerprint().equals(digest)) {
                var successor = new FrontierV3OperationLedger.OwnerCommand(
                        new CommandId("physical-operation:" + ticket + "/recovery/" + digest), consequence,
                        previous.closure(), previous.context());
                ledger.replaceUnacceptedOwnerCommand(ticket, previous, successor);
                // The predecessor has no receipt, so it owns no committed history.
                // The successor cannot submit until its exact binding is durable below.
            }
        }
        return submitOutcome(ledger, durability, canonical, ticket, consequence) == Outcome.CONFIRMED;
    }
    /** Must run before the first startup command/advance can expire canonical receipts. */
    static void recover(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime) {
        var ledger = FrontierV3OperationLedger.get(level);
        recover(ledger, () -> ledger.persist(level), canonical(level, runtime, java.util.Optional.empty()));
    }
    static void recover(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical) {
        for (var entry : ledger.ownerCommands().entrySet()) {
            var command = entry.getValue(); canonical.pin(command.id());
            if (ledger.require(entry.getKey()).ownerClosed() || canonical.receipt(command.id()).isPresent()
                    && command.closure() == FrontierV3OperationLedger.OwnerCommand.Closure.AUTOMATIC)
                close(ledger, force, canonical, entry.getKey(), command);
            else if (canonical.receipt(command.id()).isEmpty()) canonical.release(command.id());
        }
    }
    private static void close(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical,
            UUID ticket, FrontierV3OperationLedger.OwnerCommand command) {
        close(ledger, synchronous(force), canonical, ticket, command);
    }
    private static void close(FrontierV3OperationLedger ledger, Durability durability, Canonical canonical,
            UUID ticket, FrontierV3OperationLedger.OwnerCommand command) {
        ledger.closeOwner(ticket);
        if (ledger.require(ticket).retirable()) ledger.retire(ticket);
        durability.closed(() -> canonical.release(command.id())); // Failure keeps the receipt pinned.
    }
    private static Canonical canonical(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        return canonical(level, runtime, binding, false);
    }
    private static Canonical canonical(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding, boolean requireAcceptance) {
        return new Canonical() {
            public void pin(CommandId id) { runtime.pinCommandReceipt(id); }
            public void release(CommandId id) { runtime.releaseCommandReceipt(id); }
            public java.util.Optional<CommandReceipt> receipt(CommandId id) { return runtime.commandReceipt(id); }
            public void afterCommit(CommandResult result, Runnable continuation) {
                runtime.afterCommand(result, FrontierV3ServerRuntime.CommandCompletion.PROTOCOL, continuation);
            }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload consequence) {
                var result = FrontierV3CommandSubmission.submitOperation(runtime, id, owner.value(), consequence, binding);
                FrontierV3DiagnosticTrace.record(level.getServer(), id.value(), "owner-confirmation", owner, result);
                if (requireAcceptance && result instanceof CommandResult.Rejected rejected)
                    throw new IllegalStateException("required native consequence rejected: " + id + ": " + rejected.rejection());
                return result;
            }
        };
    }
    static String fingerprint(FrontierPayload payload) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(payload.type().getBytes(java.nio.charset.StandardCharsets.UTF_8)); digest.update((byte) 0);
            return java.util.HexFormat.of().formatHex(digest.digest(FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
