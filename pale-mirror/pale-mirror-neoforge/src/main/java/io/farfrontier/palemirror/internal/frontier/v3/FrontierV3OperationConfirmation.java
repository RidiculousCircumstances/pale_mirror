package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Joins the canonical command receipt to the physical journal without treating their
 * two independent WAL writes as one transaction. Neither payload nor job is interpreted. */
final class FrontierV3OperationConfirmation {
    interface Canonical {
        void pin(CommandId id);
        void release(CommandId id);
        java.util.Optional<CommandReceipt> receipt(CommandId id);
        CommandResult submit(CommandId id, SubjectId owner, FrontierPayload consequence);
    }
    private FrontierV3OperationConfirmation() { }
    static boolean submit(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket, FrontierPayload consequence) {
        return submit(level, runtime, ticket, consequence, java.util.Optional.empty());
    }
    static boolean submit(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket, FrontierPayload consequence,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        var ledger = FrontierV3OperationLedger.get(level);
        if (!FrontierV3MinecraftOperation.recoverOutcome(level, ticket)) return false;
        return submit(ledger, () -> ledger.persist(level), canonical(level, runtime, binding), ticket, consequence);
    }
    static boolean submit(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical, UUID ticket, FrontierPayload consequence) {
        var operation = ledger.require(ticket);
        if (!ledger.nativeOutcomeAvailable(ticket)) return false;
        if (operation.ownerClosed()) return true;
        var retained = ledger.ownerCommand(ticket);
        var command = retained.orElseGet(() -> new FrontierV3OperationLedger.OwnerCommand(
                new CommandId("physical-operation:" + ticket), consequence));
        if (!command.fingerprint().equals(fingerprint(consequence)))
            throw new IllegalArgumentException("changed physical consequence requires explicit owner recovery");
        ledger.bindOwnerCommand(ticket, command); force.run();
        canonical.pin(command.id());
        if (canonical.receipt(command.id()).isEmpty()) {
            var result = canonical.submit(command.id(), operation.declaration().owner(), consequence);
            if (!(result instanceof CommandResult.Accepted)) {
                // Synchronous rejection owns no canonical receipt. Retaining an absent
                // command here would let unrelated waiting operations exhaust the pin pool.
                canonical.release(command.id()); return false;
            }
        }
        close(ledger, force, canonical, ticket, command);
        return true;
    }
    /** The owner validates its recovery consequence. The coordinator first proves that
     * the pinned prior command was not accepted; it never replays the native effect. */
    static boolean submitRecovery(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID ticket,
            FrontierPayload consequence, java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        var ledger = FrontierV3OperationLedger.get(level);
        if (!FrontierV3MinecraftOperation.recoverOutcome(level, ticket)) return false;
        return submitRecovery(ledger, () -> ledger.persist(level), canonical(level, runtime, binding), ticket, consequence);
    }
    static boolean submitRecovery(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical,
            UUID ticket, FrontierPayload consequence) {
        if (!ledger.nativeOutcomeAvailable(ticket)) return false;
        if (ledger.require(ticket).ownerClosed()) return true;
        var retained = ledger.ownerCommand(ticket);
        if (retained.isPresent()) {
            var previous = retained.orElseThrow(); canonical.pin(previous.id());
            if (canonical.receipt(previous.id()).isPresent()) {
                close(ledger, force, canonical, ticket, previous); return true;
            }
            String digest = fingerprint(consequence);
            if (!previous.fingerprint().equals(digest)) {
                var successor = new FrontierV3OperationLedger.OwnerCommand(
                        new CommandId("physical-operation:" + ticket + "/recovery/" + digest), consequence);
                ledger.replaceUnacceptedOwnerCommand(ticket, previous, successor);
                force.run(); // Crash recovery restores the successor pin from this durable binding.
                canonical.pin(successor.id()); canonical.release(previous.id());
            }
        }
        return submit(ledger, force, canonical, ticket, consequence);
    }
    /** Must run before the first startup command/advance can expire canonical receipts. */
    static void recover(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime) {
        var ledger = FrontierV3OperationLedger.get(level);
        recover(ledger, () -> ledger.persist(level), canonical(level, runtime, java.util.Optional.empty()));
    }
    static void recover(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical) {
        for (var entry : ledger.ownerCommands().entrySet()) {
            var command = entry.getValue(); canonical.pin(command.id());
            if (ledger.require(entry.getKey()).ownerClosed() || canonical.receipt(command.id()).isPresent())
                close(ledger, force, canonical, entry.getKey(), command);
            else canonical.release(command.id());
        }
    }
    private static void close(FrontierV3OperationLedger ledger, Runnable force, Canonical canonical,
            UUID ticket, FrontierV3OperationLedger.OwnerCommand command) {
        ledger.closeOwner(ticket);
        if (ledger.require(ticket).retirable()) ledger.retire(ticket);
        force.run(); // A failed force must keep the canonical receipt pinned.
        canonical.release(command.id());
    }
    private static Canonical canonical(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        return new Canonical() {
            public void pin(CommandId id) { runtime.pinCommandReceipt(id); }
            public void release(CommandId id) { runtime.releaseCommandReceipt(id); }
            public java.util.Optional<CommandReceipt> receipt(CommandId id) { return runtime.commandReceipt(id); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload consequence) {
                var result = FrontierV3CommandSubmission.submitOperation(runtime, id, owner.value(), consequence, binding);
                FrontierV3DiagnosticTrace.record(level.getServer(), id.value(), "owner-confirmation", owner, result);
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
