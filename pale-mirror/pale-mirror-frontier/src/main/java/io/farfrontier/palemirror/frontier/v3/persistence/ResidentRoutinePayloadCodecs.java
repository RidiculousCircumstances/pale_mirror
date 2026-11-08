package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import java.util.*;

final class ResidentRoutinePayloadCodecs {
  static PayloadCodecs codecs() {
    return new PayloadCodecs(
        List.of(
                RoutineEvent.Requested.TYPE,
                RoutineEvent.Started.TYPE,
                RoutineEvent.Arrived.TYPE,
                RoutineEvent.Seated.TYPE,
                RoutineEvent.Ended.TYPE)
            .stream()
            .map(ResidentRoutinePayloadCodecs::codec)
            .toList());
  }

  private static PayloadCodec codec(String type) {
    return new PayloadCodec() {
      public String type() {
        return type;
      }

      public byte[] encode(FrontierPayload value) {
        return FrontierWorldPayloadCodecs.encodeProduction(
            out -> {
              switch (value) {
                case RoutineEvent.Requested e -> {
                  CombatWire.actuation(out, e.actor());
                  out.writeByte(e.kind().wireTag());
                  FrontierWorldStateCodec.writePosition(out, e.goal().support());
                  ResidentRoutineStateCodec.seat(out, e.seat());
                }
                case RoutineEvent.Started e -> ResidentRoutineStateCodec.row(out, e.routine());
                case RoutineEvent.Arrived e -> {
                  ActorExecutionStateCodec.writeId(out, e.execution());
                  out.writeLong(e.sequence());
                }
                case RoutineEvent.Seated e -> {
                  ActorExecutionStateCodec.writeId(out, e.execution());
                  out.writeLong(e.sequence());
                }
                case RoutineEvent.Ended e -> {
                  ActorExecutionStateCodec.writeId(out, e.execution());
                  out.writeLong(e.sequence());
                }
                default -> throw new IllegalArgumentException("foreign routine payload");
              }
            });
      }

      public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(
            bytes,
            in ->
                switch (type) {
                  case RoutineEvent.Requested.TYPE ->
                      new RoutineEvent.Requested(
                          CombatWire.actuation(in),
                          ResidentRoutine.Kind.fromWireTag(in.readUnsignedByte()),
                          new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),
                          ResidentRoutineStateCodec.seat(in));
                  case RoutineEvent.Started.TYPE ->
                      new RoutineEvent.Started(ResidentRoutineStateCodec.row(in));
                  case RoutineEvent.Arrived.TYPE ->
                      new RoutineEvent.Arrived(ActorExecutionStateCodec.readId(in), in.readLong());
                  case RoutineEvent.Seated.TYPE ->
                      new RoutineEvent.Seated(ActorExecutionStateCodec.readId(in), in.readLong());
                  case RoutineEvent.Ended.TYPE ->
                      new RoutineEvent.Ended(ActorExecutionStateCodec.readId(in), in.readLong());
                  default -> throw new IllegalArgumentException("unknown routine payload");
                });
      }
    };
  }
}
