package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.domain.DevelopmentIntentState;
import io.farfrontier.palemirror.domain.DevelopmentIntentType;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRuntime;
import io.farfrontier.palemirror.internal.materialization.JobState;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Executes only pinned PM-owned development intents; settlement policy remains in the domain. */
public final class SettlementDevelopmentRuntime {
    private SettlementDevelopmentRuntime() { }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands) {
        boolean changed = false;
        for (var intent : data.worldState().developmentIntents().stream()
                .filter(value -> value.type() == DevelopmentIntentType.UPGRADE_STOREHOUSE
                        || value.type() == DevelopmentIntentType.RECONSTRUCT_PLACE).toList()) {
            if (intent.state() == DevelopmentIntentState.PLANNED) {
                // A presented development opportunity is a genuine player
                // decision. A declined or unpresented intent remains
                // autonomous; an offered one waits until the audience accepts.
                boolean awaitingAudience = data.worldState().scenarios().stream()
                        .filter(scenario -> scenario.archetype() == ScenarioArchetype.DEVELOPMENT_OPPORTUNITY)
                        .filter(scenario -> scenario.target().equals(intent.communityId()))
                        .anyMatch(scenario -> scenario.status() == ScenarioStatus.OFFERED);
                if (awaitingAudience) continue;
                if (!commands.execute(data.worldState(), new DomainCommand.StartDevelopmentIntent(intent.id())).isEmpty()) changed = true;
                continue;
            }
            if (intent.state() != DevelopmentIntentState.MATERIALIZING) continue;
            if (intent.type() == DevelopmentIntentType.RECONSTRUCT_PLACE) continue;
            var depot = data.settlementDepots().get(intent.communityId());
            if (depot == null || !depot.siteId().equals(intent.targetSiteId())) continue; // core-only legacy target
            var job = data.materializationJobs().activeFor(depot.siteId().value(), SettlementDepotRuntime.CHANNEL).orElse(null);
            if (job != null && job.state() == JobState.BLOCKED) {
                commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(intent.id(), job.lastError()));
                changed = true;
                continue;
            }
            if (job == null || job.state() != JobState.COMPLETED
                    || !job.policyId().equals("pale_mirror:depot_upgraded")) continue;
            commands.execute(data.worldState(), new DomainCommand.CompleteDevelopmentIntent(intent.id()));
            changed = true;
        }
        if (AuthoredSettlementProjectRuntime.tick(server, data, commands)) changed = true;
        if (RefugeeCampRuntime.cleanupReturnedGroups(server, data, commands)) changed = true;
        return changed;
    }

}
