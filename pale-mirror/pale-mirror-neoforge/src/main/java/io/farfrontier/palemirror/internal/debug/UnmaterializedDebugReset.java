package io.farfrontier.palemirror.internal.debug;

import java.util.ArrayList;
import java.util.List;

import io.farfrontier.palemirror.internal.world.CampaignRegionPresentationStatus;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;

/** Fail-closed reset policy kept outside SavedData so persistence does not acquire a debug responsibility. */
public final class UnmaterializedDebugReset {
    private UnmaterializedDebugReset() { }

    public static List<String> blockers(PaleMirrorSavedData data) {
        List<String> blockers = new ArrayList<>();
        if (!data.testMines().isEmpty()) blockers.add("physical test mines=" + data.testMines().size());
        if (!data.vanillaMinecartRoutes().isEmpty()) blockers.add("vanilla minecart routes=" + data.vanillaMinecartRoutes().size());
        data.campaignRegions().values().forEach(region -> {
            if (region.status() != CampaignRegionPresentationStatus.PLANNED || region.nextOperationIndex() != 0) {
                blockers.add("campaign job " + region.id() + "=" + region.status() + "/op" + region.nextOperationIndex());
            }
        });
        if (!data.settlementDepots().isEmpty()) blockers.add("settlement depots=" + data.settlementDepots().size());
        if (!data.refugeeCamps().isEmpty()) blockers.add("refugee camps=" + data.refugeeCamps().size());
        if (!data.resourceTransfers().transfers().isEmpty()) blockers.add("resource transfers=" + data.resourceTransfers().transfers().size());
        if (!data.effectLeases().leases().isEmpty()) blockers.add("effect leases=" + data.effectLeases().leases().size());
        if (!data.quarantine().records().isEmpty()) blockers.add("quarantine records=" + data.quarantine().records().size());
        if (!data.threatCombat().actors().isEmpty()) blockers.add("combat actors=" + data.threatCombat().actors().size());
        if (!data.threatCombat().projectiles().isEmpty()) blockers.add("projectiles=" + data.threatCombat().projectiles().size());
        if (!data.worldState().developmentIntents().isEmpty()) blockers.add("development intents=" + data.worldState().developmentIntents().size());
        if (!data.materializationJobs().jobs().isEmpty()) blockers.add("materialization jobs=" + data.materializationJobs().jobs().size());
        if (!data.semanticSlots().slots().isEmpty()) blockers.add("semantic slots=" + data.semanticSlots().slots().size());
        if (!data.parcels().parcels().isEmpty()) blockers.add("managed parcels=" + data.parcels().parcels().size());
        if (!data.residentJourneyLeases().leases().isEmpty()) blockers.add("resident journey leases="
                + data.residentJourneyLeases().leases().size());
        if (!data.residentIdentities().retiredIds().isEmpty()) blockers.add("retired resident identities="
                + data.residentIdentities().retiredIds().size());
        data.worldRegistry().entries().stream().filter(entry -> !"minecraft:observed_village".equals(entry.templateId()))
                .forEach(entry -> blockers.add("managed world object=" + entry.id().value()));
        return List.copyOf(blockers);
    }

    /** Rechecks before clearing and never touches Minecraft blocks, entities, items, chunks, or foreign state. */
    public static void execute(PaleMirrorSavedData data) {
        List<String> blockers = blockers(data);
        if (!blockers.isEmpty()) throw new IllegalStateException("PM reset blocked: " + String.join(", ", blockers));
        data.testMines().clear();
        data.audienceMappings().clear();
        data.reconciliationLedger().clear();
        data.worldRegistry().clear();
        data.effectLeases().clear();
        data.quarantine().clear();
        data.threatCombat().clear();
        data.campaignRegions().clear();
        data.vanillaMinecartRoutes().clear();
        data.settlementObservations().clear();
        data.resourceTransfers().clear();
        data.settlementDepots().clear();
        data.refugeeCamps().clear();
        data.materializationJobs().clear();
        data.semanticSlots().clear();
        data.parcels().clear();
        data.residentJourneyLeases().clear();
        data.residentIdentities().clear();
        new io.farfrontier.palemirror.internal.domain.DomainTransaction(data,
                new io.farfrontier.palemirror.domain.DomainServices().commands()).execute(data.worldState(),
                        new io.farfrontier.palemirror.domain.DomainCommand.ResetWorldState("operator:unmaterialized-reset"));
    }
}
