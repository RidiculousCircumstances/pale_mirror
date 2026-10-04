package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Current bounded scout goals and captured arrivals; no legacy position-only decoder. */
final class ScoutPatrolCodec {
    private ScoutPatrolCodec() { }
    static void write(DataOutputStream out, StrategicPlanState plans) throws IOException {
        out.writeInt(plans.scoutPatrols().size());
        for (var entry : plans.scoutPatrols().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var journey = entry.getValue();
            ActorExecutionStateCodec.writeId(out, journey.executionId());
            out.writeLong(journey.goalRevision());
            position(out, journey.target());
        }
    }
    static Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ScoutPatrolJourney> read(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > StrategicPlanState.MAX_SCOUT_PATROLS)
            throw new IllegalArgumentException("scout journey count exceeds retention bound");
        var journeys = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ScoutPatrolJourney>();
        for (int index = 0; index < count; index++) {
            var journey = new ScoutPatrolJourney(ActorExecutionStateCodec.readId(in), in.readLong(), position(in));
            if (journeys.putIfAbsent(journey.executionId().actorId(), journey) != null)
                throw new IllegalArgumentException("duplicate retained scout journey");
        }
        return Map.copyOf(journeys);
    }
    static void writeAdvance(DataOutputStream out, ScoutPatrolAdvanced advance) throws IOException {
        ActorExecutionStateCodec.writeId(out, advance.executionId());
        out.writeLong(advance.goalRevision()); out.writeLong(advance.phase());
        position(out, advance.target()); position(out, advance.priorSurface());
        out.writeBoolean(advance.hotArrival().isPresent());
        if (advance.hotArrival().isPresent()) {
            var hot = advance.hotArrival().orElseThrow();
            FrontierWorldStateCodec.writeString(out, hot.actuation().body().actorId().value());
            out.writeLong(hot.actuation().body().physicalEpoch());
            ActorExecutionStateCodec.writeId(out, hot.actuation().execution());
            out.writeLong(hot.scopeRevision());
        }
    }
    static ScoutPatrolAdvanced readAdvance(DataInputStream in) throws IOException {
        var execution = ActorExecutionStateCodec.readId(in);
        long revision = in.readLong(), phase = in.readLong();
        var target = position(in); var prior = position(in);
        var hot = in.readBoolean() ? Optional.of(new ScoutPatrolAdvanced.HotArrival(new ActorActuationId(
                new ActorBodyId(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(FrontierWorldStateCodec.readString(in)),
                        in.readLong()), ActorExecutionStateCodec.readId(in)), in.readLong()))
                : Optional.<ScoutPatrolAdvanced.HotArrival>empty();
        return new ScoutPatrolAdvanced(execution, revision, phase, target, prior, hot);
    }
    private static void position(DataOutputStream out, SurfaceAnchor surface) throws IOException {
        out.writeInt(surface.x()); out.writeInt(surface.y()); out.writeInt(surface.z());
    }
    private static SurfaceAnchor position(DataInputStream in) throws IOException {
        return SurfaceAnchor.at(in.readInt(), in.readInt(), in.readInt());
    }
}
