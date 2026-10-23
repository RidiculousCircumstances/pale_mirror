package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.io.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.*;

final class ContainerPlayerEditPayloadCodec implements PayloadCodec {
    @Override public String type() { return "frontier.container_player_edit_observed"; }
    @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(out -> {
        var edit = (ContainerPlayerEditObserved) payload;
        out.writeByte(3);
        writeString(out, edit.playerId().toString()); writeString(out, edit.interactionId().toString());
        out.writeByte(edit.members().size());
        for (var member : edit.members()) {
            writeSubject(out, member.containerId()); writeSubject(out, member.accountId()); writeSubject(out, member.ownerId());
            out.writeLong(member.epoch()); stacks(out, member.before()); stacks(out, member.after());
        }
        out.writeBoolean(edit.returning().isPresent());
        if (edit.returning().isPresent()) {
            var source = edit.returning().orElseThrow(); writeSubject(out, source.bindingId()); writeSubject(out, source.accountId());
            out.writeLong(source.epoch()); out.writeInt(source.slot()); writeString(out, source.itemKind());
            out.writeInt(source.quantity()); out.writeInt(source.remaining());
        }
        out.writeBoolean(edit.exactIngress().isPresent());
        if (edit.exactIngress().isPresent()) {
            switch (edit.exactIngress().orElseThrow()) {
                case ExactItemIngress.Contribution contribution -> {
                    out.writeByte(1); ExactItemStateCodec.write(out, contribution.after());
                }
                case ExactItemIngress.Returned returned -> {
                    out.writeByte(2); ExactItemStateCodec.write(out, returned.before()); ExactItemStateCodec.write(out, returned.after());
                }
            }
        }
    }); }
    @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, in -> {
        if (in.readUnsignedByte() != 3) throw new IllegalArgumentException("incompatible player interaction payload format");
        var player = UUID.fromString(readString(in)); var interaction = UUID.fromString(readString(in));
        int count = in.readUnsignedByte(); if (count < 1 || count > 54) throw new IllegalArgumentException("invalid composed member count");
        var members = new ArrayList<ContainerPlayerEditObserved.Member>();
        for (int i = 0; i < count; i++) {
            var container = readSubject(in).value(); var account = readSubject(in).value(); var owner = readSubject(in).value();
            long epoch = in.readLong(); members.add(new ContainerPlayerEditObserved.Member(container, account, owner, epoch,
                    stacks(in, container), stacks(in, container)));
        }
        Optional<ContainerPlayerEditObserved.PlayerReturn> source = in.readBoolean()
                ? Optional.of(new ContainerPlayerEditObserved.PlayerReturn(readSubject(in).value(), readSubject(in).value(),
                    in.readLong(), in.readInt(), readString(in), in.readInt(), in.readInt())) : Optional.empty();
        Optional<ExactItemIngress> exact = in.readBoolean() ? Optional.of(switch (in.readUnsignedByte()) {
            case 1 -> new ExactItemIngress.Contribution(ExactItemStateCodec.read(in));
            case 2 -> new ExactItemIngress.Returned(ExactItemStateCodec.read(in), ExactItemStateCodec.read(in));
            default -> throw new IllegalArgumentException("unknown exact ingress provenance");
        }) : Optional.empty();
        return new ContainerPlayerEditObserved(player, interaction, members, source, exact);
    }); }
    private static void stacks(DataOutputStream out, List<FungiblePhysicalObservation.Stack> stacks) throws IOException {
        out.writeByte(stacks.size());
        for (var stack : stacks) {
            out.writeByte(((PhysicalStackAddress.ContainerSlot) stack.address()).slot().slot());
            writeString(out, stack.itemKind()); out.writeByte(stack.quantity());
        }
    }
    private static List<FungiblePhysicalObservation.Stack> stacks(DataInputStream in, SubjectId container) throws IOException {
        int count = in.readUnsignedByte(); if (count > 54) throw new IllegalArgumentException("unbounded composed layout");
        var stacks = new ArrayList<FungiblePhysicalObservation.Stack>();
        for (int i = 0; i < count; i++) stacks.add(new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, in.readUnsignedByte())),
                readString(in), in.readUnsignedByte()));
        return List.copyOf(stacks);
    }
}
