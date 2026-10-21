package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Owner-produced conditional outcome, retained by an enclosing operation. It
 * cannot replace a living owner's command or infer application from item counts. */
record FrontierV3OperationAftermath(FrontierV3OperationReceipt child, Outcome outcome, FrontierPayload consequence) {
    enum Outcome { APPLIED, CANCEL_UNUSED }
    FrontierV3OperationAftermath {
        Objects.requireNonNull(child); Objects.requireNonNull(outcome); Objects.requireNonNull(consequence);
        if (!consequence.requiresDurableBeforeEffect()) throw new IllegalArgumentException("aftermath requires a durable owner consequence");
    }
    static Optional<FrontierV3OperationAftermath> capture(ServerLevel level, FrontierV3OperationReceipt.Declaration declaration,
            List<FrontierV3MinecraftOperation.Participant> ports, FrontierPayload consequence, boolean applied, BooleanSupplier unchanged) {
        if (!unchanged.getAsBoolean()) return Optional.empty();
        var ledger = FrontierV3OperationLedger.get(level);
        if (applied) {
            var observed = FrontierV3MinecraftOperation.observe(level, declaration, ports, unchanged).orElse(null);
            if (observed == null || !observed.complete()) return Optional.empty();
            return Optional.of(new FrontierV3OperationAftermath(ledger.require(observed.ticket()), Outcome.APPLIED, consequence));
        }
        if (!ledger.canPrepare(declaration)) return Optional.empty();
        var operation = ledger.prepare(declaration);
        if (!operation.cancelled() && !ledger.unattempted(operation.ticket())) return Optional.empty();
        return Optional.of(new FrontierV3OperationAftermath(operation, Outcome.CANCEL_UNUSED, consequence));
    }
    void retain(FrontierV3OperationLedger ledger, UUID parent) {
        var current = requireChild(ledger);
        if (outcome == Outcome.CANCEL_UNUSED && !current.cancelled()) ledger.deferCancellation(parent, child.ticket());
    }
    boolean settle(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID parent) {
        var ledger = FrontierV3OperationLedger.get(level);
        var enclosing = ledger.require(parent);
        if (enclosing.cancelled() || !enclosing.applied().equals(enclosing.declaration().participants())
                || !ledger.nativeOutcomeAvailable(parent)) return false;
        if (ledger.find(child.ticket()).isEmpty())
            return ledger.prunable(child.nativeReceipt(child.declaration().participants().iterator().next()));
        var current = requireChild(ledger);
        if (current.ownerClosed()) return true;
        if (outcome == Outcome.CANCEL_UNUSED && !ledger.sealUnattemptedCancellation(child.ticket())) return false;
        if (outcome == Outcome.APPLIED && current.cancelled()) throw new IllegalStateException("applied aftermath has a cancelled child");
        return FrontierV3OperationConfirmation.submitRecovery(level, runtime, child.ticket(), consequence, Optional.empty());
    }
    private FrontierV3OperationReceipt requireChild(FrontierV3OperationLedger ledger) {
        var current = ledger.require(child.ticket());
        if (current.sequence() != child.sequence() || !current.declaration().equals(child.declaration()))
            throw new IllegalArgumentException("aftermath substituted its exact child operation");
        return current;
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.put("child", FrontierV3OperationReceiptCodec.write(child));
        tag.putInt("outcome", switch (outcome) { case APPLIED -> 1; case CANCEL_UNUSED -> 2; });
        tag.putString("type", consequence.type());
        tag.putByteArray("payload", FrontierWorldRuntimeDefinition.payloadCodecs().encode(consequence)); return tag;
    }
    static FrontierV3OperationAftermath load(CompoundTag tag) {
        var result = new FrontierV3OperationAftermath(FrontierV3OperationReceiptCodec.read(tag.getCompound("child")),
                switch (tag.getInt("outcome")) { case 1 -> Outcome.APPLIED; case 2 -> Outcome.CANCEL_UNUSED;
                    default -> throw new IllegalArgumentException("unknown conditional native outcome"); },
                FrontierWorldRuntimeDefinition.payloadCodecs().decode(tag.getString("type"), tag.getByteArray("payload")));
        if (!result.save().equals(tag)) throw new IllegalArgumentException("invalid conditional native outcome image");
        return result;
    }
}
