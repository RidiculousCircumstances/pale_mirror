package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.domain.DevelopmentIntentState;
import io.farfrontier.palemirror.domain.DevelopmentIntentType;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRuntime;
import io.farfrontier.palemirror.internal.economy.SettlementDepotState;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Executes only pinned PM-owned development intents; settlement policy remains in the domain. */
public final class SettlementDevelopmentRuntime {
    private SettlementDevelopmentRuntime() { }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        boolean changed = false;
        for (var intent : data.worldState().developmentIntents().stream()
                .filter(value -> value.type() == DevelopmentIntentType.UPGRADE_STOREHOUSE).toList()) {
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
            var depot = data.settlementDepots().get(intent.communityId());
            if (depot == null || !depot.siteId().equals(intent.targetSiteId())) continue;
            if (depot.state() == SettlementDepotState.BLOCKED) {
                commands.execute(data.worldState(), new DomainCommand.CancelDevelopmentIntent(intent.id(), depot.diagnostic()));
                changed = true;
                continue;
            }
            if (depot.state() != SettlementDepotState.ACTIVE) continue;
            ServerLevel level = level(server, depot.dimensionId());
            if (level == null || !level.hasChunkAt(depot.anchor())) continue;
            String failure = SettlementDepotRuntime.upgradeStorehouse(level, depot);
            if (failure == null) commands.execute(data.worldState(), new DomainCommand.CompleteDevelopmentIntent(intent.id()));
            else commands.execute(data.worldState(), new DomainCommand.CancelDevelopmentIntent(intent.id(), failure));
            changed = true;
        }
        if (RefugeeCampRuntime.cleanupReturnedGroups(server, data, commands)) changed = true;
        return changed;
    }

    private static ServerLevel level(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) if (level.dimension().location().toString().equals(dimensionId)) return level;
        return null;
    }
}
