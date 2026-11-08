package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import java.io.*;
import java.util.*;

final class ResidentRoutineStateCodec {
  static void row(DataOutputStream out, ResidentRoutine r) throws IOException {
    out.writeUTF(r.actor().value());
    out.writeUTF(r.home().value());
    out.writeLong(r.sequence());
    out.writeByte(r.kind().wireTag());
    out.writeByte(r.phase().wireTag());
    out.writeBoolean(r.execution().isPresent());
    if (r.execution().isPresent())
      ActorExecutionStateCodec.writeId(out, r.execution().orElseThrow());
    FrontierWorldStateCodec.writePosition(out, r.goal().support());
    out.writeLong(r.startedAt());
    out.writeLong(r.phaseAt());
    out.writeLong(r.pauseTicks());
    out.writeLong(r.eligibleAt());
  }

  static ResidentRoutine row(DataInputStream in) throws IOException {
    var actor = new SubjectId(in.readUTF());
    var home = new SubjectId(in.readUTF());
    long sequence = in.readLong();
    var kind = ResidentRoutine.Kind.fromWireTag(in.readUnsignedByte());
    var phase = ResidentRoutine.Phase.fromWireTag(in.readUnsignedByte());
    var execution =
        in.readBoolean()
            ? Optional.of(ActorExecutionStateCodec.readId(in))
            : Optional
                .<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty();
    return new ResidentRoutine(
        actor,
        home,
        sequence,
        kind,
        phase,
        execution,
        new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),
        in.readLong(),
        in.readLong(),
        in.readLong(),
        in.readLong());
  }

  static void write(DataOutputStream out, ResidentRoutineState value) throws IOException {
    for (var entry : value.rows().entrySet())
      if (!entry.getKey().equals(entry.getValue().actor()))
        throw new IllegalArgumentException("routine key differs from its declared actor");
    out.writeInt(value.rows().size());
    for (var r :
        value.rows().values().stream()
            .sorted(Comparator.comparing(ResidentRoutine::actor))
            .toList()) row(out, r);
  }

  static ResidentRoutineState read(DataInputStream in) throws IOException {
    int size = UnitGroupStateCodec.count(in, SimulationCapacity.MAX_RESIDENTS);
    var rows = new LinkedHashMap<SubjectId, ResidentRoutine>();
    for (int i = 0; i < size; i++) {
      var row = row(in);
      if (rows.putIfAbsent(row.actor(), row) != null)
        throw new IllegalArgumentException("duplicate routine actor");
    }
    return new ResidentRoutineState(rows);
  }
}
