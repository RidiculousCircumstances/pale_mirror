package io.farfrontier.palemirror.internal.world;

import java.util.List;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.LogisticsRouteContract;
import net.minecraft.server.MinecraftServer;

/** Reconciles generic, read-only route observations into the campaign's canonical capacity fact. */
public final class RegionalLogisticsRuntime {
    private RegionalLogisticsRuntime() { }

    public static List<DomainEvent> observe(MinecraftServer server, PaleMirrorSavedData data,
                                            DomainCommandProcessor commands, long proofWindowSteps) {
        LivingRegionState region = data.worldState().livingRegion(CampaignRegionBootstrapper.IRONHILL_ID).orElse(null);
        CampaignRegionRecord presentation = data.campaignRegions().get(CampaignRegionBootstrapper.IRONHILL_ID);
        if (region == null || presentation == null || presentation.primaryMineAnchor() == null
                || presentation.alternateMineAnchor() == null || !hasAcceptedCrisis(data, region)) return List.of();
        LogisticsRouteContract contract = new LogisticsRouteContract(region.alternateRouteId(), presentation.alternateMineAnchor(),
                presentation.settlementAnchor(), CampaignRegionBootstrapper.RED_VALLEY_DISPATCH,
                CampaignRegionBootstrapper.IRONHILL_RECEIVING);
        return AdapterRegistry.observeLogisticsRoute(server.overworld(), contract).map(observation -> {
            if (!observation.observed()) return List.<DomainEvent>of();
            boolean changed = observeEndpoints(data, presentation, observation.originTrainPresent(), observation.originCapacity(),
                    observation.originVehicleId(), true);
            changed |= observeEndpoints(data, presentation, observation.destinationTrainPresent(), observation.destinationCapacity(),
                    observation.destinationVehicleId(), false);
            int capacity = presentation.certifiedRouteCapacity(data.worldState().simulationStep(), proofWindowSteps);
            List<DomainEvent> events = data.worldState().route(region.alternateRouteId())
                    .filter(route -> route.observedCapacity() != capacity)
                    .map(ignored -> commands.execute(data.worldState(), new DomainCommand.ObserveRouteCapacity(
                            region.alternateRouteId(), capacity, "adapter:logistics:" + region.id())))
                    .orElseGet(List::of);
            if (changed || !events.isEmpty()) data.setDirty();
            return events;
        }).orElseGet(List::of);
    }

    /** Operator-facing facts for a real Create schedule test; no route state is changed by this query. */
    public static String describe(PaleMirrorSavedData data) {
        CampaignRegionRecord record = data.campaignRegions().get(CampaignRegionBootstrapper.IRONHILL_ID);
        if (record == null) return "No observed settlement is bound to the First Living Region.";
        if (record.alternateMineAnchor() == null) return "Red Valley mine is not materialized; approach "
                + record.alternateMineColumn().toShortString() + " first.";
        return "Create schedule: '" + CampaignRegionBootstrapper.RED_VALLEY_DISPATCH + "' at "
                + record.alternateMineAnchor().toShortString() + " -> '" + CampaignRegionBootstrapper.IRONHILL_RECEIVING
                + "' at " + record.settlementAnchor().toShortString() + "; vehicle origin=" + record.originVehicleId()
                + " step=" + record.originTrainSeenAtStep() + ", destination=" + record.destinationVehicleId() + " step="
                + record.destinationTrainSeenAtStep() + ", certified capacity="
                + record.certifiedRouteCapacity(data.worldState().simulationStep(), 8);
    }

    private static boolean hasAcceptedCrisis(PaleMirrorSavedData data, LivingRegionState region) {
        return region.status() == io.farfrontier.palemirror.domain.LivingRegionStatus.CRISIS_ACTIVE
                && data.worldState().scenarios().stream().anyMatch(scenario ->
                scenario.archetype() == ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS
                        && scenario.target().equals(region.settlementId()) && scenario.status() == ScenarioStatus.RESPOND);
    }

    private static boolean observeEndpoints(PaleMirrorSavedData data, CampaignRegionRecord presentation, boolean present,
                                            int capacity, String vehicleId, boolean origin) {
        if (!present || vehicleId.isBlank()) return false;
        return presentation.observeRouteEndpoint(origin, data.worldState().simulationStep(), capacity, vehicleId);
    }
}
