package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import java.util.*;

/** A single persisted cause fences every explicitly ordered physical member. */
record FrontierV3ContainerClickTransaction(List<FrontierV3DepotClickWitness> members, Optional<FrontierV3OperationReceipt> operation,
        Optional<io.farfrontier.palemirror.frontier.v3.model.ContainerPlayerEditObserved> outcome) {
    FrontierV3ContainerClickTransaction(List<FrontierV3DepotClickWitness> members) { this(members, Optional.empty(), Optional.empty()); }
    FrontierV3ContainerClickTransaction {
        members = List.copyOf(members);
        Objects.requireNonNull(operation); Objects.requireNonNull(outcome);
        if (members.isEmpty() || members.size() > 54
                || members.stream().map(FrontierV3DepotClickWitness::containerId).distinct().count() != members.size())
            throw new IllegalArgumentException("invalid composed click membership");
        var first = members.getFirst();
        if (members.stream().anyMatch(m -> !m.playerId().equals(first.playerId()) || !m.interactionId().equals(first.interactionId())
                || !m.returnSource().equals(first.returnSource())))
            throw new IllegalArgumentException("composed click lacks one cause and complete postimages");
        if (operation.isPresent()) {
            var receipt = operation.orElseThrow(); var declaration = receipt.declaration();
            if (declaration.protocol() != FrontierV3OperationReceipt.Protocol.PLAYER_CONTAINER_CLICK
                    || !declaration.owner().equals(io.farfrontier.palemirror.frontier.v3.model.FrontierExecutionSubjects.PHYSICAL_EXECUTOR)
                    || !declaration.operation().equals(first.interactionId().toString())
                    || !receipt.applied().equals(declaration.participants()) || outcome.isEmpty())
                throw new IllegalArgumentException("click has a foreign or incomplete native operation");
        }
        if (outcome.isPresent()) {
            var fact = outcome.orElseThrow();
            if (!fact.playerId().equals(first.playerId()) || !fact.interactionId().equals(first.interactionId()) || fact.members().size() != members.size())
                throw new IllegalArgumentException("click outcome has a foreign cause");
            if (first.returnSource().isPresent() != fact.returning().isPresent())
                throw new IllegalArgumentException("click outcome changed player return authority");
            if (first.returnSource().isPresent()) {
                var a = first.returnSource().orElseThrow(); var b = fact.returning().orElseThrow();
                if (!a.bindingId().equals(b.bindingId()) || !a.accountId().equals(b.accountId()) || a.epoch() != b.epoch()
                        || a.playerSlot() != b.slot() || !a.itemKind().equals(b.itemKind()) || a.quantity() != b.quantity())
                    throw new IllegalArgumentException("click outcome changed its tracked player source");
            }
            for (int i = 0; i < members.size(); i++) {
                var a = members.get(i); var b = fact.members().get(i);
                if (!a.containerId().equals(b.containerId()) || !a.accountId().equals(b.accountId()) || !a.ownerId().equals(b.ownerId())
                        || a.authorityEpoch() != b.epoch() || !a.before().equals(b.before()))
                    throw new IllegalArgumentException("click outcome changed its admitted predecessor");
            }
        }
    }
    SubjectId id() { return members.getFirst().containerId(); }
    boolean contains(SubjectId id) { return members.stream().anyMatch(m -> m.containerId().equals(id)); }
    CompoundTag save() {
        var tag = new CompoundTag(); var entries = new ListTag();
        members.forEach(m -> entries.add(m.save())); tag.put("members", entries);
        operation.ifPresent(value -> tag.put("operation", FrontierV3OperationReceiptCodec.write(value)));
        outcome.ifPresent(value -> tag.putByteArray("outcome", io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().encode(value)));
        return tag;
    }
    static FrontierV3ContainerClickTransaction read(CompoundTag tag) {
        var entries = tag.getList("members", Tag.TAG_COMPOUND);
        if (entries.size() > 54) throw new IllegalArgumentException("unbounded composed click");
        var members = new ArrayList<FrontierV3DepotClickWitness>();
        for (int i = 0; i < entries.size(); i++) members.add(FrontierV3DepotClickWitness.read(entries.getCompound(i)));
        if (tag.contains("operation") && !tag.contains("operation", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("invalid click operation");
        if (tag.contains("outcome") && !tag.contains("outcome", Tag.TAG_BYTE_ARRAY)) throw new IllegalArgumentException("invalid click outcome");
        return new FrontierV3ContainerClickTransaction(members, tag.contains("operation")
                ? Optional.of(FrontierV3OperationReceiptCodec.read(tag.getCompound("operation"))) : Optional.empty(),
                tag.contains("outcome") ? Optional.of((io.farfrontier.palemirror.frontier.v3.model.ContainerPlayerEditObserved)
                    io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()
                        .decode("frontier.container_player_edit_observed", tag.getByteArray("outcome"))) : Optional.empty());
    }
}
