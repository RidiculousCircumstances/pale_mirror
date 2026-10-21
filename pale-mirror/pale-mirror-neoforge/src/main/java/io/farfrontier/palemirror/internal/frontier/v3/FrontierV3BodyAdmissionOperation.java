package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import net.minecraft.nbt.*;
import java.util.*;

/** Body-owned outbox, co-written with the exact admission/rollback in its actor
 * row. The common physical coordinator never interprets these body semantics. */
record FrontierV3BodyAdmissionOperation(WorldId world, FrontierV3ActorOwnerBinding binding, long residence,
        FrontierV3BodyInsertionJournal.Undo undo, FrontierV3OperationReceipt operation, boolean rejected) {
    FrontierV3BodyAdmissionOperation {
        Objects.requireNonNull(world); Objects.requireNonNull(binding); Objects.requireNonNull(undo); Objects.requireNonNull(operation);
        if (residence < 1 || !undo.binding().equals(binding) || operation.cancelled() || operation.ownerClosed()
                || !operation.applied().isEmpty() || !operation.saved().isEmpty()
                || operation.declaration().protocol() != FrontierV3OperationReceipt.Protocol.BODY_ADMISSION
                || !operation.declaration().owner().equals(binding.declaration().actorId())
                || !operation.declaration().operation().equals(world.value() + "/residence:" + residence)
                || !operation.declaration().fingerprint().equals(fingerprint(world, binding, residence, undo))
                || operation.declaration().participants().size() != 1)
            throw new IllegalArgumentException("incomplete body admission operation");
        var participant = operation.declaration().participants().iterator().next();
        if (participant.medium() != FrontierV3OperationReceipt.Medium.ENTITIES || participant.incarnation() < 1
                || !participant.identity().equals(binding.declaration().entityId().toString()))
            throw new IllegalArgumentException("body admission has a foreign native participant");
    }
    SubjectId actor() { return binding.declaration().actorId(); }
    FrontierV3OperationReceipt.Participant participant() { return operation.declaration().participants().iterator().next(); }
    FrontierV3BodyAdmissionOperation rejection() { return new FrontierV3BodyAdmissionOperation(world, binding, residence, undo, operation, true); }
    static FrontierV3OperationReceipt.Declaration declaration(WorldId world, FrontierV3ActorOwnerBinding binding, long residence,
            FrontierV3BodyInsertionJournal.Undo undo, FrontierV3MinecraftOperation.Participant port) {
        return new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_ADMISSION,
                binding.declaration().actorId(), world.value() + "/residence:" + residence, fingerprint(world, binding, residence, undo), Set.of(port.identity()));
    }
    private static String fingerprint(WorldId world, FrontierV3ActorOwnerBinding binding, long residence, FrontierV3BodyInsertionJournal.Undo undo) {
        var tag = new CompoundTag(); tag.putString("world", world.value()); tag.put("binding", binding.save()); tag.putLong("residence", residence); tag.put("undo", undo.save());
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(FrontierV3CarrierJournalImage.encode(tag))); }
        catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("format", 1); tag.putString("actor", actor().value()); tag.putString("world", world.value());
        tag.put("binding", binding.save()); tag.putLong("residence", residence); tag.put("undo", undo.save());
        tag.put("operation", FrontierV3OperationReceiptCodec.write(operation)); tag.putBoolean("rejected", rejected); return tag;
    }
    static FrontierV3BodyAdmissionOperation load(CompoundTag tag) {
        if (tag.getInt("format") != 1 || tag.size() != 8 || !tag.contains("rejected", Tag.TAG_BYTE)
                || !tag.contains("residence", Tag.TAG_LONG)) throw new IllegalArgumentException("invalid body operation schema");
        var result = new FrontierV3BodyAdmissionOperation(new WorldId(tag.getString("world")), FrontierV3ActorOwnerBinding.load(tag.getCompound("binding")),
                tag.getLong("residence"), FrontierV3BodyInsertionJournal.Undo.load(tag.getCompound("undo")),
                FrontierV3OperationReceiptCodec.read(tag.getCompound("operation")), tag.getBoolean("rejected"));
        if (!result.actor().value().equals(tag.getString("actor"))) throw new IllegalArgumentException("body operation crosses actor row");
        return result;
    }
}
