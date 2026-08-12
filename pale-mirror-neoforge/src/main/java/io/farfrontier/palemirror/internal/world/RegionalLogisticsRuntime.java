package io.farfrontier.palemirror.internal.world;

import java.util.List;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.LogisticsRouteContract;
import net.minecraft.server.MinecraftServer;

/** Reconciles generic, read-only route observations into the campaign's canonical capacity fact. */
public final class RegionalLogisticsRuntime {
    private RegionalLogisticsRuntime() { }

    public static List<DomainEvent> observe(MinecraftServer server, PaleMirrorSavedData data,
                                            DomainCommandExecutor commands, long proofWindowSteps) {
        java.util.List<DomainEvent> all = new java.util.ArrayList<>();
        data.worldState().livingRegions().stream().sorted(java.util.Comparator.comparing(LivingRegionState::id))
                .forEach(region -> {
                    CampaignRegionRecord presentation = data.campaignRegions().get(region.id());
                    if (presentation == null || presentation.primaryMineAnchor() == null
                            || presentation.alternateMineAnchor() == null) return;
                    all.addAll(observeRegion(server, data, commands, proofWindowSteps, region, presentation));
                });
        return List.copyOf(all);
    }

    private static List<DomainEvent> observeRegion(MinecraftServer server, PaleMirrorSavedData data,
                                                    DomainCommandExecutor commands, long proofWindowSteps,
                                                    LivingRegionState region, CampaignRegionRecord presentation) {
        RegionBindings bindings = RegionBindings.fromRegionId(region.id());
        LogisticsRouteContract contract = new LogisticsRouteContract(region.alternateRouteId(), presentation.alternateMineAnchor(),
                presentation.settlementAnchor(), bindings.alternateDispatchStation(), bindings.receivingStation());
        return AdapterRegistry.observeLogisticsRoute(server.overworld(), contract).map(observation -> {
            if (!observation.observed()) return List.<DomainEvent>of();
            boolean changed = observeEndpoints(data, presentation, observation.originTrainPresent(), observation.originCapacity(),
                    observation.originVehicleId(), true);
            changed |= observeEndpoints(data, presentation, observation.destinationTrainPresent(), observation.destinationCapacity(),
                    observation.destinationVehicleId(), false);
            int capacity = presentation.certifiedRouteCapacity(data.worldState().simulationStep(), proofWindowSteps);
            String observationId = capacity <= 0 ? "" : "create:" + presentation.originVehicleId() + ":"
                    + presentation.originTrainSeenAtStep() + ":" + presentation.destinationTrainSeenAtStep();
            List<DomainEvent> events = capacity <= 0 ? List.of() : data.worldState().routeContract(region.alternateRouteId())
                    .filter(route -> !route.lastObservationId().equals(observationId))
                    .map(ignored -> commands.execute(data.worldState(), new DomainCommand.ValidateRouteContract(
                            region.alternateRouteId(), capacity, data.worldState().simulationStep(), observationId,
                            "adapter:logistics:" + region.id())))
                    .orElseGet(List::of);
            if (changed || !events.isEmpty()) data.setDirty();
            return events;
        }).orElseGet(List::of);
    }

    /** Operator-facing facts for a real Create schedule test; no route state is changed by this query. */
    public static String describe(PaleMirrorSavedData data) {
        String result = data.campaignRegions().values().stream().sorted(java.util.Comparator.comparing(CampaignRegionRecord::id))
                .map(record -> describeRegion(data, record)).reduce((left, right) -> left + "\n" + right).orElse("");
        return result.isBlank() ? "No observed settlement is bound to an iron_frontier region." : result;
    }

    private static String describeRegion(PaleMirrorSavedData data, CampaignRegionRecord record) {
        if (record.alternateMineAnchor() == null) return record.displayName() + ": alternate mine is not materialized; approach "
                + record.alternateMineColumn().toShortString() + " first.";
        LivingRegionState region = data.worldState().livingRegion(record.id()).orElse(null);
        var contract = region == null ? null : data.worldState().routeContract(region.alternateRouteId()).orElse(null);
        String contractStatus = contract == null ? "missing" : contract.status() + "/"
                + contract.health(data.worldState().simulationStep()) + "/" + contract.freshness(data.worldState().simulationStep())
                + " capacity=" + contract.transferableCapacity(data.worldState().simulationStep())
                + " validatedAt=" + contract.lastSuccessfulValidationStep();
        RegionBindings bindings = RegionBindings.fromRegionId(record.id());
        return record.displayName() + " Create schedule: '" + bindings.alternateDispatchStation() + "' at "
                + record.alternateMineAnchor().toShortString() + " -> '" + bindings.receivingStation()
                + "' at " + record.settlementAnchor().toShortString() + "; vehicle origin=" + record.originVehicleId()
                + " step=" + record.originTrainSeenAtStep() + ", destination=" + record.destinationVehicleId() + " step="
                + record.destinationTrainSeenAtStep() + ", certified capacity="
                + record.certifiedRouteCapacity(data.worldState().simulationStep(), 8) + ", contract=" + contractStatus;
    }

    private static boolean observeEndpoints(PaleMirrorSavedData data, CampaignRegionRecord presentation, boolean present,
                                            int capacity, String vehicleId, boolean origin) {
        if (!present || vehicleId.isBlank()) return false;
        return presentation.observeRouteEndpoint(origin, data.worldState().simulationStep(), capacity, vehicleId);
    }
}
