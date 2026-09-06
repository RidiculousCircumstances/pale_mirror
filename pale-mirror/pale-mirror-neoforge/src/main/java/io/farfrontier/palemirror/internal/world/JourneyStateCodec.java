package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.domain.JourneyMode;
import io.farfrontier.palemirror.domain.JourneyRiskPolicy;
import io.farfrontier.palemirror.domain.JourneyState;
import io.farfrontier.palemirror.domain.WorldJourney;
import io.farfrontier.palemirror.domain.WorldJourneySummary;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldPath;
import io.farfrontier.palemirror.domain.WorldPathNode;
import io.farfrontier.palemirror.domain.WorldState;
import io.farfrontier.palemirror.domain.WorldStateHydration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class JourneyStateCodec {
    private JourneyStateCodec() { }

    static void write(CompoundTag root, WorldState state) {
        ListTag paths = new ListTag(); state.worldPaths().forEach(path -> paths.add(writePath(path))); root.put("worldPaths", paths);
        ListTag journeys = new ListTag(); state.journeys().forEach(journey -> journeys.add(writeJourney(journey))); root.put("journeys", journeys);
        ListTag summaries = new ListTag();
        state.journeySummaries().forEach(summary -> {
            CompoundTag value = new CompoundTag();
            value.putString("outcome", summary.outcome().name());
            value.putLong("count", summary.count());
            value.putLong("losses", summary.confirmedLosses());
            value.putLong("lastCompleted", summary.lastCompletedStep());
            summaries.add(value);
        });
        root.put("journeySummaries", summaries);
    }

    static void read(CompoundTag root, WorldStateHydration.Builder state) {
        for (Tag raw : root.getList("worldPaths", Tag.TAG_COMPOUND)) state.path(readPath((CompoundTag) raw));
        for (Tag raw : root.getList("journeys", Tag.TAG_COMPOUND)) state.journey(readJourney((CompoundTag) raw));
        for (Tag raw : root.getList("journeySummaries", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            state.journeySummary(new WorldJourneySummary(JourneyState.valueOf(value.getString("outcome")),
                    value.getLong("count"), value.getLong("losses"), value.getLong("lastCompleted")));
        }
    }

    private static CompoundTag writePath(WorldPath path) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", path.id()); tag.putString("version", path.policyVersion());
        tag.putString("origin", path.originSiteId().value()); tag.putString("destination", path.destinationSiteId().value());
        ListTag nodes = new ListTag();
        path.nodes().forEach(node -> {
            CompoundTag value = new CompoundTag(); value.putString("id", node.id()); value.putString("dimension", node.dimensionId());
            value.putInt("x", node.x()); value.putInt("y", node.y()); value.putInt("z", node.z());
            value.putBoolean("safe", node.safeCheckpoint()); nodes.add(value);
        });
        tag.put("nodes", nodes); return tag;
    }

    private static WorldPath readPath(CompoundTag tag) {
        List<WorldPathNode> nodes = new ArrayList<>();
        for (Tag raw : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            nodes.add(new WorldPathNode(value.getString("id"), value.getString("dimension"), value.getInt("x"),
                    value.getInt("y"), value.getInt("z"), value.getBoolean("safe")));
        }
        return new WorldPath(tag.getString("id"), tag.getString("version"), id(tag, "origin"), id(tag, "destination"), nodes);
    }

    private static CompoundTag writeJourney(WorldJourney value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", value.id()); tag.putString("subject", value.subjectGroupId()); tag.putString("path", value.pathId());
        tag.putString("origin", value.originSiteId().value()); tag.putString("destination", value.destinationSiteId().value());
        tag.putString("mode", value.mode().name()); tag.putLong("riskSeed", value.riskSeed());
        tag.putLong("started", value.startedAtStep()); tag.putLong("duration", value.expectedDurationSteps());
        tag.putString("state", value.state().name()); tag.putLong("elapsed", value.elapsedSteps());
        tag.putInt("checkpoint", value.checkpointIndex()); tag.putInt("losses", value.confirmedLosses());
        tag.putString("diagnostic", value.diagnostic()); tag.putLong("revision", value.revision());
        JourneyRiskPolicy risk = value.riskPolicy();
        tag.putString("riskId", risk.id()); tag.putString("riskVersion", risk.version());
        tag.putInt("exposure", risk.exposureBasisPoints()); tag.putInt("maxLoss", risk.maximumLossPerStep());
        tag.putInt("guardMitigation", risk.guardMitigationBasisPoints());
        return tag;
    }

    private static WorldJourney readJourney(CompoundTag tag) {
        JourneyRiskPolicy risk = new JourneyRiskPolicy(tag.getString("riskId"), tag.getString("riskVersion"),
                tag.getInt("exposure"), tag.getInt("maxLoss"), tag.getInt("guardMitigation"));
        return new WorldJourney(tag.getString("id"), tag.getString("subject"), tag.getString("path"), id(tag, "origin"),
                id(tag, "destination"), JourneyMode.valueOf(tag.getString("mode")), risk, tag.getLong("riskSeed"),
                tag.getLong("started"), tag.getLong("duration"), JourneyState.valueOf(tag.getString("state")),
                tag.getLong("elapsed"), tag.getInt("checkpoint"), tag.getInt("losses"), tag.getString("diagnostic"),
                tag.getLong("revision"));
    }

    private static WorldObjectId id(CompoundTag tag, String key) { return new WorldObjectId(tag.getString(key)); }
}
