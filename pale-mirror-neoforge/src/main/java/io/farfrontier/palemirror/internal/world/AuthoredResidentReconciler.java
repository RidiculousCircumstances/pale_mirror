package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.SettlementCohort;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Converts confirmed visual-carrier deaths into canonical PM commands exactly once. */
public final class AuthoredResidentReconciler {
    private AuthoredResidentReconciler() { }

    public static List<DomainEvent> reconcile(MinecraftServer server, PaleMirrorSavedData data,
                                               DomainCommandProcessor commands) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider == null) return List.of();
        List<DomainEvent> events = new ArrayList<>();
        for (var observation : provider.drainResidentDeaths(server.overworld())) {
            RegionBindings ids = RegionBindings.forAuthored(observation.regionPlanId());
            if (data.worldState().livingRegion(ids.regionId()).isEmpty()) {
                throw new IllegalStateException("Resident death references unknown authored region " + ids.regionId());
            }
            if (!data.reconciliationLedger().recordIfNew(observation.observationId())) continue;
            events.addAll(commands.execute(data.worldState(), new DomainCommand.ConfirmSettlementResidentDeath(
                    ids.communityId(), ids.regionId() + ":residents", SettlementCohort.valueOf(observation.cohort()),
                    observation.residentId(), observation.observationId())));
            data.residentIdentities().retire(observation.residentId());
            data.setDirty();
        }
        for (var observation : provider.drainJourneyObservations(server.overworld())) {
            if (!data.reconciliationLedger().recordIfNew(observation.observationId())) continue;
            switch (observation.type()) {
                case IDENTITY_RETIRED -> {
                    ResidentJourneyLease lease = data.residentJourneyLeases().lease(observation.residentId());
                    if (lease == null || !lease.journeyId().equals(observation.journeyId())) {
                        throw new IllegalStateException("Journey identity hand-off references an unknown lease");
                    }
                    lease.ready();
                }
                case CHECKPOINT_REACHED -> {
                    ResidentJourneyLease lease = data.residentJourneyLeases().lease(observation.residentId());
                    if (lease != null && lease.journeyId().equals(observation.journeyId())) {
                        lease.checkpoint(observation.checkpointIndex());
                    }
                    events.addAll(commands.execute(data.worldState(), new DomainCommand.ObserveJourneyCheckpoint(
                            observation.journeyId(), observation.checkpointIndex(), observation.observationId())));
                }
                case BLOCKED -> events.addAll(commands.execute(data.worldState(), new DomainCommand.ObserveJourneyBlocked(
                        observation.journeyId(), observation.diagnostic(), observation.observationId())));
            }
            data.setDirty();
        }
        return List.copyOf(events);
    }
}
