package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Shared immutable counterfactuals for the retained-checkpoint performance probes.
 * Uses ordinary named update APIs, never a native outcome or a world-file edit. */
final class ValuationProbeScenarios {
    record Sample(String name, FrontierWorldState state) { }
    static List<Sample> scenarios(FrontierWorldState state, GoodsParticipant home, GoodsParticipant foreign) {
        var samples = new ArrayList<Sample>(); samples.add(new Sample("base", state));
        samples.add(new Sample("irrelevant-resident-movement", move(state, foreign.settlementId())));
        samples.add(new Sample("irrelevant-foreign-stock", removeOne(state, foreign)));
        samples.add(new Sample("local-stock", removeOne(state, home)));
        var resident = state.humanPopulation().residentsIn(home.settlementId()).getFirst();
        var modifiers = resident.characteristics().workModifiers();
        var changed = modifiers.with(new ResidentWorkModifiers.Modifier(new SubjectId("modifier:reactor-experiment"), HumanCapability.INDUSTRY, 500));
        samples.add(new Sample("local-workforce", state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(
                state.humanPopulation().changeWorkModifiers(resident.id(), modifiers, changed)))));
        int day = state.humanPopulation().schedule(home.settlementId()).dayTicks();
        var schedule = new SettlementDailySchedule(day, List.of(new SettlementDailySchedule.Segment(0, 1, SettlementDailySchedule.Window.WORK),
                new SettlementDailySchedule.Segment(1, day, SettlementDailySchedule.Window.FREE)));
        samples.add(new Sample("local-schedule", state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(
                state.humanPopulation().withSchedule(home.settlementId(), schedule)))));
        var station = state.inventory().containers().values().stream().filter(c -> c.ownerId().equals(home.settlementId())
                && c.productionStation().isPresent()).sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        var conditions = ImmutableLookupMap.withEntry(state.structureConditions(), station.productionStation().orElseThrow().facilityId(), StructureCondition.DAMAGED);
        samples.add(new Sample("local-station-unavailable", state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(conditions))));
        return List.copyOf(samples);
    }
    static FrontierWorldState move(FrontierWorldState state, SubjectId home) {
        var id = state.humanPopulation().residentsIn(home).getFirst().id(); var old = state.actorLocations().get(id);
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(ImmutableLookupMap.withEntry(state.actorLocations(), id,
                old.withBody(old.body().offset(1, 0, 0)))));
    }
    static FrontierWorldState removeOne(FrontierWorldState state, GoodsParticipant p) {
        var ledger = state.inventory().fungibleResources();
        for (var kind : state.bootstrap().ruleset().goodsTrade().policies().get(p.policy())) {
            for (var id : GoodsParticipantStorage.containers(state, p, kind.itemKind()).stream().sorted().toList()) {
                var account = FungibleResourceCustodySupport.accountAtContainer(state, id).orElse(null);
                if (account == null || !account.claimQuantities().isEmpty()
                        || ledger.bindings().values().stream().anyMatch(b -> b.accountId().equals(account.id()))) continue;
                for (var entry : account.lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                    var lot = ledger.lots().get(entry.getKey());
                    if (!lot.economicOwnerId().equals(p.party().id()) || !lot.itemKind().equals(kind.itemKind())) continue;
                    return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory()
                            .withFungibleResources(ledger.destroy(account.id(), Map.of(entry.getKey(), 1), Map.of()))));
                }
            }
        }
        throw new IllegalStateException("checkpoint has no unbound unclaimed local stock for actual removal");
    }
    private ValuationProbeScenarios() { }
}
