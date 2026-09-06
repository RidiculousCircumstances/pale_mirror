package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** One canonical consequence path for a physical or simulated hive-organ destruction. */
final class FrontierHiveDestruction {
    private FrontierHiveDestruction() { }

    static List<FrontierEvent> destroy(FrontierWorldState state, FrontierHiveOrgan organ, String causationId) {
        return destroy(state, organ, causationId, null);
    }

    static List<FrontierEvent> destroy(FrontierWorldState state, FrontierHiveOrgan organ, String causationId,
                                       String continuingCampaignId) {
        if (!organ.destroy()) return List.of();
        FrontierHive hive = state.hive(organ.hiveId()).orElseThrow();
        state.reconcileHive(hive);
        List<FrontierEvent> events = new ArrayList<>();
        events.add(state.event(FrontierEvent.Type.HIVE_ORGAN_DESTROYED, organ.id(), causationId));
        LinkedHashMap<String, FrontierMorphogenesisProject> abortedProjects = new LinkedHashMap<>();
        state.abortMorphogenesisForSource(organ.id()).forEach(project -> abortedProjects.put(project.id(), project));
        state.harvesters().abortForSource(state, organ.id())
                .forEach(run -> events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, run.id(), causationId)));
        state.propagations().abortForSource(state, organ.id())
                .forEach(run -> events.add(state.event(FrontierEvent.Type.PROPAGATION_RUN_ABORTED, run.id(), causationId)));
        state.harvesters().abortForReceiver(state, organ.id())
                .forEach(run -> events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, run.id(), causationId)));
        if (hive.state() != FrontierHive.State.ACTIVE) {
            state.abortMorphogenesisForHive(hive.id()).forEach(project -> abortedProjects.put(project.id(), project));
            state.harvesters().abortForHive(state, hive.id())
                    .forEach(run -> events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, run.id(), causationId)));
            state.propagations().abortForHive(state, hive.id())
                    .forEach(run -> events.add(state.event(FrontierEvent.Type.PROPAGATION_RUN_ABORTED, run.id(), causationId)));
            for (FrontierAssault assault : state.abortAssaultsForHive(hive.id())) {
                events.add(state.event(FrontierEvent.Type.ASSAULT_RESOLVED, assault.id(), causationId));
                for (FrontierFieldOperation operation : state.beginReturnForAssault(assault.id())) {
                    events.add(state.event(FrontierEvent.Type.FIELD_OPERATION_STATE_CHANGED, operation.id(), causationId));
                }
            }
            for (FrontierCampaign campaign : state.beginWithdrawForHiveExcept(hive.id(), continuingCampaignId)) {
                events.add(state.event(FrontierEvent.Type.CAMPAIGN_STATE_CHANGED, campaign.id(), causationId));
            }
        }
        abortedProjects.values().forEach(project -> events.add(state.event(FrontierEvent.Type.MORPHOGENESIS_ABORTED,
                project.id(), causationId)));
        state.adaptationLedger().record(organ.kind() == FrontierHiveOrganKind.SYNAPSE
                ? FrontierDamageKind.SYNAPSE : FrontierDamageKind.COMBAT);
        return List.copyOf(events);
    }
}
