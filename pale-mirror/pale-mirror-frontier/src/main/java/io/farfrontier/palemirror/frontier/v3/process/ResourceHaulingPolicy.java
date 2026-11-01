package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Placement policy only; shipment/UAE own transport and source adapters own pickup. */
final class ResourceHaulingPolicy {
    static final String REVIEW = "frontier.resource_hauling.review";
    static ScheduledAction review(SubjectId home, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:resource-hauling-" + java.util.UUID.nameUUIDFromBytes(
                home.value().getBytes(java.nio.charset.StandardCharsets.UTF_8))), new SimInstant(tick), 12, home, REVIEW, 1);
    }
    static Optional<InternalShipmentDispatched> offer(FrontierWorldState state, ResidentProfile worker, long tick) {
        for(var request:requests(state,worker.settlementId(),tick)) {
            var prepared=request.prepare(state,worker,tick);if(prepared.isPresent())return prepared;
        }
        return Optional.empty();
    }
    /** Eligibility and admission share the same real requests; neither invokes pathfinding here. */
    static boolean available(FrontierWorldState state,ResidentProfile worker,long tick) {
        return !requests(state,worker.settlementId(),tick).isEmpty();
    }
    private static List<InternalHaulRequest> requests(FrontierWorldState state,SubjectId home,long tick) {
        var result=new ArrayList<>(ProductionStationSupply.requests(state,home,tick));
        var resources=state.inventory().fungibleResources();
        for(var source:ResourceSourceDiscovery.localHaulSources(state,home)) {
            if(source.container().filter(id->ReferenceContainerCustody.blocksCanonicalUse(state,id)).isPresent())continue;
            var account=FungibleResourceCustodySupport.accountsAt(resources,source.custody()).stream().findFirst().orElse(null);
            if(account==null)continue;
            var kinds=account.lotQuantities().keySet().stream().map(id->resources.lots().get(id).itemKind()).distinct().sorted().toList();
            for(var kind:kinds) {
                int quantity=Math.min(64,resources.unclaimedQuantity(account.id(),home,kind));
                if(quantity==0)continue;
                long first=state.shipments().accumulation().firstAvailable().getOrDefault(new HaulAccumulation.Key(home,account.id(),kind),tick);
                boolean pressure=source.container().filter(id->!ContainerStorageAdmission.receive(state,new ResourceCustody.Container(id,ContainerSectionId.STOCK),kind,64,Optional.empty())).isPresent();
                for(var destination:ResourceStorageSelection.candidates(state,home,kind,source.station(),Set.of(ResourceStorageRole.WAREHOUSE))) {
                    var candidate=InternalHaulRequest.select(state,home,source,destination,kind,quantity);
                    if(candidate.isEmpty())continue;
                    var portion=candidate.orElseThrow();
                    long distance=Math.abs((long)source.station().x()-destination.station().x())
                            +Math.abs((long)source.station().y()-destination.station().y())+Math.abs((long)source.station().z()-destination.station().z());
                    long travel=Math.multiplyExact(distance,state.bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge());
                    var batch=HaulBatchPolicy.decide(new HaulBatchPolicy.Request(portion.quantity(),64,travel,first,tick,
                            state.bootstrap().ruleset().extraction().reviewTicks(),receiverShortage(state,home,kind),pressure));
                    if(batch.dispatch())result.add(new InternalHaulRequest(source,destination,kind,batch.quantity()));
                }
            }
        }
        return List.copyOf(result);
    }
    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant instant) {
        if (!REVIEW.equals(action.kind()) || !review(action.subject(), action.dueAt().ticks()).equals(action))
            throw new IllegalArgumentException("resource hauling review has foreign identity");
        long now = Math.max(instant.ticks(), action.dueAt().ticks());
        var retirement = ShipmentRetention.collect(state, ShipmentRetention.MAX_RETIREMENTS, now);
        var events = new ArrayList<>(retirement.events()); var projected = retirement.state();
        var seen = new HashSet<HaulAccumulation.Key>();
        var resources = projected.inventory().fungibleResources();
        for (var source : ResourceSourceDiscovery.localHaulSources(projected, action.subject()))
            for (var account : FungibleResourceCustodySupport.accountsAt(resources, source.custody()))
                for (var lot : account.lotQuantities().keySet()) {
                    var kind = resources.lots().get(lot).itemKind();
                    if (resources.unclaimedQuantity(account.id(), action.subject(), kind) > 0)
                        seen.add(new HaulAccumulation.Key(action.subject(), account.id(), kind));
                }
        var reviewed = new HaulAccumulationReviewed(action.subject(), seen);
        projected = reviewed.apply(projected, action.subject(), now);
        events.add(new ProposedEvent(action.subject(), reviewed));
        for (var worker : ResidentWorkComposition.SELECTION.eligible(projected, action.subject(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS, now)) {
            Optional<InternalTransportMissionStarted> manifest;
            try {manifest=InternalTransportPlanning.offer(projected,worker,now);}
            catch(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) {break;}
            if(manifest.isPresent()) {
                var admitted=manifest.orElseThrow();projected=InternalTransportPlanning.admit(projected,admitted.transport().mission().id(),admitted,now);
                events.add(new ProposedEvent(admitted.transport().mission().id(),admitted));
                TransportMissionProcess.initialContinuations(admitted.transport().mission(),now+1)
                        .forEach(continuation->events.add(new ProposedEvent(continuation.subject(),new ScheduleEffect.Created(continuation))));
                continue;
            }
            Optional<InternalShipmentDispatched> offer;
            try { offer = offer(projected, worker, now); }
            catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) { break; }
            if (offer.isEmpty()) continue;
            var dispatched = offer.orElseThrow(); projected = InternalShipmentStateSupport.dispatch(projected, action.subject(), dispatched);
            events.add(new ProposedEvent(action.subject(), dispatched));
            events.add(new ProposedEvent(dispatched.shipment().id(), new ScheduleEffect.Created(ShipmentProcess.progress(dispatched.shipment().id(), Math.addExact(now, 1)))));
        }
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                review(action.subject(), Math.addExact(now, state.bootstrap().ruleset().extraction().reviewTicks())))));
        return List.copyOf(events);
    }
    private static boolean receiverShortage(FrontierWorldState state, SubjectId home, String kind) {
        var accepted = state.companies().goodsTrade().participants().economicState(home).accepted();
        if (accepted.isEmpty()) return false;
        var need = accepted.orElseThrow().needs().get(kind);
        if (need == null || need.immediate() == 0) return false;
        var ledger = state.inventory().fungibleResources();
        long covered = ResourceStorageSelection.containers(state, home, kind, Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                .flatMap(id -> FungibleResourceCustodySupport.accountAtContainer(state, id).stream())
                .mapToLong(account -> ledger.unclaimedQuantity(account.id(), home, kind)).sum();
        covered += state.shipments().shipments().values().stream().filter(shipment -> !shipment.terminal()
                && shipment.receiver().settlementId().equals(home) && shipment.itemKind().equals(kind))
                .mapToLong(Shipment::quantity).sum();
        return need.immediate() > covered;
    }
    private ResourceHaulingPolicy() { }
}
