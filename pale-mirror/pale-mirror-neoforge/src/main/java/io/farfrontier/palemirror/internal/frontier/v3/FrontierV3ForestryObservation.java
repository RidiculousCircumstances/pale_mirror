package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.internal.forestry.ForestRegistration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Coalesced chunk-fact ingress, not a ticking forest or permanent loaded-chunk index. */
final class FrontierV3ForestryObservation {
    private static final Map<ServerLevel, LinkedHashSet<Long>> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    private FrontierV3ForestryObservation() { }
    static void loaded(ServerLevel level, ChunkPos region) {
        synchronized (PENDING) { PENDING.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(region.toLong()); }
    }
    static void unloaded(ServerLevel level, ChunkPos region) {
        synchronized (PENDING) { var pending = PENDING.get(level); if (pending != null) pending.remove(region.toLong()); }
    }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null); if (state == null || !state.bootstrap().ruleset().forestry().enabled()) return;
        for (int probe = 0; probe < 32; probe++) {
            long key;
            synchronized (PENDING) {
                var pending = PENDING.get(level); if (pending == null || pending.isEmpty()) return;
                key = pending.iterator().next(); pending.remove(key);
            }
            if (inspect(level, runtime, state, key)) return;
        }
    }
    private static boolean inspect(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   FrontierWorldState state, long key) {
        var region = new ChunkPos(key); var chunk = level.getChunkSource().getChunkNow(region.x, region.z); if (chunk == null) return false;
        var facts = chunk.getData(ForestRegistration.GENERATED).orElse(null); if (facts == null) return false;
        FrontierV3GenerationConfiguration.require(state.bootstrap(), facts.seed(), facts.configuration());
        var authored = GrayboxForestPlan.compile(state.bootstrap()).touching(facts.region());
        var fresh = facts.trees().stream().filter(tree -> state.forest().find(TreeReference.declaredBy(tree)).isEmpty() && !authored.contains(tree))
                .filter(tree -> {
                    for (int i = 0; i < tree.logs().size(); i++) if (!state.bootstrap().bounds().contains(tree.logs().position(tree.origin(), i))) return false;
                    return true;
                }).toList();
        if (fresh.isEmpty()) return false;
        // Ordinary block capture retires old projection authority first. A same-looking
        // new log is a new source generation, not proof that the old one regenerated.
        for (var tree : fresh) for (int i = 0; i < tree.logs().size(); i++)
            if (ForestKnowledge.at(state, tree.logs().position(tree.origin(), i)).isPresent()) { loaded(level, region); return true; }
        var result = FrontierV3CommandSubmission.submit(runtime, "forest-generation", "nature:forest", new ForestObservation.Generated(facts.configuration(), fresh));
        runtime.afterCommand(result, () -> { if (!(result instanceof CommandResult.Accepted)) loaded(level, region); });
        return true;
    }
}
