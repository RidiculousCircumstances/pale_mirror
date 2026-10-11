package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.HashSet;
import java.util.Set;

/** Both sides of a physical scene handoff address retained resident/service waits. */
final class SceneLeaseWakeKeys {
    private SceneLeaseWakeKeys() { }

    static Set<SubjectId> changed(FrontierWorldState previous, FrontierWorldState next) {
        if (previous.sceneLeases() == next.sceneLeases()) return Set.of();
        var keys = new HashSet<SubjectId>();
        for (var entry : previous.sceneLeases().entrySet()) {
            SceneLease updated = next.sceneLeases().get(entry.getKey());
            if (entry.getValue().equals(updated)) continue;
            add(previous, entry.getValue(), keys);
            if (updated != null) add(next, updated, keys);
        }
        for (var entry : next.sceneLeases().entrySet()) {
            if (!previous.sceneLeases().containsKey(entry.getKey())) add(next, entry.getValue(), keys);
        }
        return Set.copyOf(keys);
    }

    private static void add(FrontierWorldState state, SceneLease lease, Set<SubjectId> keys) {
        for (SceneMember member : lease.members()) {
            ResidentProfile resident = state.humanPopulation().resident(member.actorId());
            if (resident == null) continue;
            keys.add(resident.id());
            keys.addAll(ServiceAccessCoordinator.wakePoints(state, resident.id()));
            keys.add(FrontierWorldState.depotId(resident.settlementId()));
        }
    }
}
