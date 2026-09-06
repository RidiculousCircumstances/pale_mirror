package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small deterministic local policy. It acts whether or not the Narrator exposes a scenario. */
public final class SettlementDecisionEngine {
    private final DomainEventFactory events;

    SettlementDecisionEngine(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> reconcile(WorldState state) {
        List<DomainEvent> produced = new ArrayList<>();
        state.communities().stream().sorted(Comparator.comparing(SettlementCommunity::id)).forEach(community -> {
            SettlementEconomy economy = state.economy(community.id()).orElseThrow();
            SettlementSecurity security = state.security(community.id()).orElseThrow();
            SettlementPolicy policy = state.settlementPolicy(community.id()).orElseThrow();
            ResourceAccount iron = economy.require(ResourceKind.IRON);
            boolean deficit = iron.reserveSteps().isPresent();
            long reserve = iron.reserveSteps().orElse(Long.MAX_VALUE);

            if (deficit && reserve < policy.rationReserveSteps() && community.setRationing(true)) {
                produced.add(event(state, DomainEventType.SETTLEMENT_RATIONING_STARTED, community.id(), "policy:reserve"));
            }
            if (deficit && reserve < policy.requestReserveSteps() && hasAlternateContract(state, community.id())
                    && community.setSupplyRequested(true)) {
                produced.add(event(state, DomainEventType.SETTLEMENT_SUPPLY_REQUESTED, community.id(), "policy:alternate-route"));
            }

            if (iron.availability() == ResourceAvailability.UNAVAILABLE) {
                community.setStableSupplySteps(0);
                if (security.applySupplyFailure(policy.defenceLossPerUnavailableStep())) {
                    produced.add(event(state, DomainEventType.SETTLEMENT_DEFENCE_DECLINED, community.id(), "policy:unavailable-iron"));
                }
                if (community.supplyRequested() && activeThreat(state, community.id())
                        && security.defenceReadiness() < security.baseDefence()
                        && community.setCrisisState(CrisisState.CRITICAL)) {
                    produced.add(event(state, DomainEventType.SETTLEMENT_CRISIS_DETECTED, community.id(), "policy:objective-crisis"));
                }
            } else if (iron.availability() == ResourceAvailability.AVAILABLE) {
                community.setStableSupplySteps(community.stableSupplySteps() + 1);
                if (community.crisisState() == CrisisState.CRITICAL && community.setCrisisState(CrisisState.RECOVERING)) {
                    produced.add(event(state, DomainEventType.SETTLEMENT_CRISIS_RECOVERING, community.id(), "policy:supply-restored"));
                }
                if (community.stableSupplySteps() >= policy.stableStepsToRecover()) {
                    boolean stabilized = community.setCrisisState(CrisisState.NONE);
                    if (community.setRationing(false)) {
                        produced.add(event(state, DomainEventType.SETTLEMENT_RATIONING_ENDED, community.id(), "policy:stable-supply"));
                    }
                    community.setSupplyRequested(false);
                    if (stabilized) {
                        produced.add(event(state, DomainEventType.SETTLEMENT_STABILIZED, community.id(), "policy:stable-supply"));
                    }
                }
            } else {
                community.setStableSupplySteps(0);
                if (community.crisisState() == CrisisState.NONE && deficit) community.setCrisisState(CrisisState.PRESSURED);
            }
        });
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
    }

    private static boolean hasAlternateContract(WorldState state, WorldObjectId communityId) {
        return state.livingRegions().stream().filter(region -> region.communityId().equals(communityId)).findFirst()
                .flatMap(region -> state.routeContract(region.alternateRouteId())).isPresent();
    }

    private static boolean activeThreat(WorldState state, WorldObjectId communityId) {
        return state.livingRegions().stream().filter(region -> region.communityId().equals(communityId))
                .map(region -> state.facility(region.primaryFacilityId()).orElse(null))
                .anyMatch(facility -> facility != null && facility.status() != FacilityStatus.OPERATIONAL);
    }

    private DomainEvent event(WorldState state, DomainEventType type, WorldObjectId subject, String causation) {
        return events.create(state, type, subject, causation);
    }
}
