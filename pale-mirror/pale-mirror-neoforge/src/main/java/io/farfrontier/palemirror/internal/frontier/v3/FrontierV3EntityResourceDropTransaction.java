package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.FrontierExecutionSubjects;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** An owner's immutable consequence, retained before native execution. The common
 * outbox validates identity, not the family's job, claim or fatality policy. */
record FrontierV3EntityResourceDropTransaction(FrontierV3OperationReceipt.Participant source,
        FrontierV3OperationReceipt.Participant destination, FrontierV3OperationReceipt operation, FrontierPayload outcome) {
    FrontierV3EntityResourceDropTransaction {
        Objects.requireNonNull(source); Objects.requireNonNull(destination); Objects.requireNonNull(operation); Objects.requireNonNull(outcome);
        if (source.medium() != FrontierV3OperationReceipt.Medium.ENTITIES
                || destination.medium() != FrontierV3OperationReceipt.Medium.ENTITIES
                || source.identity().equals(destination.identity()) || !outcome.requiresDurableBeforeEffect()
                || operation.declaration().protocol() != FrontierV3OperationReceipt.Protocol.ENTITY_RESOURCE_DROP
                || !operation.declaration().owner().equals(FrontierExecutionSubjects.PHYSICAL_EXECUTOR)
                || !operation.declaration().participants().equals(Set.of(source, destination))
                || !operation.declaration().fingerprint().equals(FrontierV3OperationConfirmation.fingerprint(outcome)))
            throw new IllegalArgumentException("resource drop lacks its exact source, birth or durable consequence");
        UUID.fromString(source.identity()); UUID.fromString(destination.identity());
    }
    UUID id() { return operation.ticket(); }
    UUID entity() { return UUID.fromString(destination.identity()); }
    FrontierV3EntityResourceDropTransaction bind(FrontierV3OperationReceipt next) {
        if (!operation.ticket().equals(next.ticket()) || operation.sequence() != next.sequence()
                || !operation.declaration().equals(next.declaration()) || !next.applied().containsAll(operation.applied())
                || !next.saved().containsAll(operation.saved()) || operation.ownerClosed() && !next.ownerClosed())
            throw new IllegalArgumentException("resource drop changed or regressed its native operation");
        return new FrontierV3EntityResourceDropTransaction(source, destination, next, outcome);
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.put("operation", FrontierV3OperationReceiptCodec.write(operation));
        tag.putString("source", source.identity()); tag.putString("destination", destination.identity());
        tag.putString("type", outcome.type()); tag.putByteArray("outcome", FrontierWorldRuntimeDefinition.payloadCodecs().encode(outcome));
        return tag;
    }
    static FrontierV3EntityResourceDropTransaction read(CompoundTag tag) {
        var operation = FrontierV3OperationReceiptCodec.read(tag.getCompound("operation"));
        var outcome = FrontierWorldRuntimeDefinition.payloadCodecs().decode(tag.getString("type"), tag.getByteArray("outcome"));
        var source = participant(operation, tag.getString("source"));
        var destination = participant(operation, tag.getString("destination"));
        var result = new FrontierV3EntityResourceDropTransaction(source, destination, operation, outcome);
        if (!result.save().equals(tag)) throw new IllegalArgumentException("noncanonical resource drop witness");
        return result;
    }
    private static FrontierV3OperationReceipt.Participant participant(FrontierV3OperationReceipt operation, String identity) {
        var matches = operation.declaration().participants().stream().filter(p -> p.identity().equals(identity)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("missing or ambiguous declared drop participant");
        return matches.getFirst();
    }
}
