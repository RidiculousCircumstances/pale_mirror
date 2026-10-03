package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Current-format execution identities; no recovered-family inference or body serialization. */
final class ActorExecutionStateCodec {
    private ActorExecutionStateCodec() { }
    static void write(DataOutputStream out, ActorExecutionState state) throws IOException {
        FrontierWorldStateCodec.writeCount(out, state.actors().size());
        for (ActorExecution actor : state.actors().values().stream().sorted(Comparator.comparing(ActorExecution::actorId)).toList()) {
            FrontierWorldStateCodec.writeString(out, actor.actorId().value());
            out.writeLong(actor.generation());
            writeOptional(out, actor.current());
            writeOptional(out, actor.suspended());
        }
    }
    static ActorExecutionState read(DataInputStream in) throws IOException {
        int count = FrontierWorldStateCodec.readCount(in);
        if (count > ActorExecutionState.MAX_ACTORS) throw new IllegalArgumentException("execution count exceeds retention bound");
        var actors = new LinkedHashMap<SubjectId, ActorExecution>();
        for (int index = 0; index < count; index++) {
            SubjectId actor = new SubjectId(FrontierWorldStateCodec.readString(in));
            ActorExecution execution = new ActorExecution(actor, in.readLong(), readOptional(in), readOptional(in));
            if (actors.putIfAbsent(actor, execution) != null) throw new IllegalArgumentException("duplicate execution actor");
        }
        return new ActorExecutionState(actors);
    }
    static void writeId(DataOutputStream out, ActorExecutionId id) throws IOException {
        FrontierWorldStateCodec.writeString(out, id.actorId().value());
        out.writeByte(FrontierWireTags.tag(id.activityKind()));
        FrontierWorldStateCodec.writeString(out, id.activityOwnerId().value());
        out.writeLong(id.generation());
    }
    static ActorExecutionId readId(DataInputStream in) throws IOException {
        return new ActorExecutionId(new SubjectId(FrontierWorldStateCodec.readString(in)),
                FrontierWireTags.require(ActorActivityKind.class, in.readUnsignedByte()),
                new SubjectId(FrontierWorldStateCodec.readString(in)), in.readLong());
    }
    private static void writeOptional(DataOutputStream out, Optional<ActorExecutionId> id) throws IOException {
        out.writeBoolean(id.isPresent());
        if (id.isPresent()) writeId(out, id.orElseThrow());
    }
    static void writeGroup(DataOutputStream out, ActorExecutionGroup group) throws IOException {
        out.writeShort(group.members().size());
        for (var id : group.members()) writeId(out, id);
    }
    static void writeOptionalGroup(DataOutputStream out, Optional<ActorExecutionGroup> group) throws IOException {
        out.writeBoolean(group.isPresent());
        if (group.isPresent()) writeGroup(out, group.orElseThrow());
    }
    static Optional<ActorExecutionGroup> readOptionalGroup(DataInputStream in) throws IOException {
        return in.readBoolean() ? Optional.of(readGroup(in)) : Optional.empty();
    }
    static ActorExecutionGroup readGroup(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        if (count < 1 || count > ActorExecutionState.MAX_ACTORS) throw new IOException("invalid execution group retention bound");
        var members = new java.util.ArrayList<ActorExecutionId>(count);
        for (int index = 0; index < count; index++) members.add(readId(in));
        return new ActorExecutionGroup(members);
    }
    private static Optional<ActorExecutionId> readOptional(DataInputStream in) throws IOException {
        return in.readBoolean() ? Optional.of(readId(in)) : Optional.empty();
    }
}
