package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.HashSet;

/** Bounded live/terminal transport records; no resource totals, wallets or actor poses. */
public record ShipmentState(Map<SubjectId, Shipment> shipments, Map<SubjectId, TransportMission> missions) {
    public static final int MAX_SHIPMENTS = 1024;
    public ShipmentState {
        shipments = Map.copyOf(Objects.requireNonNull(shipments));
        missions = Map.copyOf(Objects.requireNonNull(missions));
        if (missions.size() > io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS)
            throw new IllegalArgumentException("transport mission retention capacity reached");
        for (var entry : missions.entrySet()) if (!entry.getKey().equals(entry.getValue().id()))
            throw new IllegalArgumentException("foreign transport mission key");
        if (shipments.size() > MAX_SHIPMENTS) throw new IllegalArgumentException("shipment retention capacity reached");
        var claims = new HashSet<SubjectId>(); var actors = new HashSet<SubjectId>(); var carriedAccounts = new HashSet<SubjectId>();
        for (var entry : shipments.entrySet()) {
            Shipment value = entry.getValue();
            if (!entry.getKey().equals(value.id()) || !value.terminal()
                    && (!claims.add(value.authorization().claimId()) || !actors.add(value.execution().actorId())
                        || !carriedAccounts.add(value.carriedAccountId())))
                throw new IllegalArgumentException("shipment identity, allocation or courier has competing owners");
        }
    }
    public ShipmentState(Map<SubjectId, Shipment> shipments) { this(shipments, Map.of()); }
    public static ShipmentState empty() { return new ShipmentState(Map.of(), Map.of()); }
    public ShipmentState admit(Shipment shipment) {
        if (shipments.containsKey(shipment.id()) || shipment.status() != Shipment.Status.AWAITING_LOAD
                || shipment.revision() != 1 || shipment.pendingPhysicalStep().isPresent() || shipment.reception().isPresent())
            throw new IllegalArgumentException("shipment admission needs one fresh declared obligation");
        var next = new LinkedHashMap<>(shipments); next.put(shipment.id(), shipment); return new ShipmentState(next, missions);
    }
    public ShipmentState replace(Shipment previous, Shipment replacement) {
        if (!previous.equals(shipments.get(previous.id())) || !replacement.id().equals(previous.id())
                || !(replacement.reception().isPresent() && previous.status() == Shipment.Status.CARRYING
                    && previous.reception().isEmpty() && previous.unloaded(replacement.reception().orElseThrow()).equals(replacement)
                    || previous.reception().isPresent() && previous.acknowledged(previous.reception().orElseThrow().id()).equals(replacement)
                    || (replacement.status() == previous.status()
                    ? replacement.pendingPhysicalStep().isPresent() && previous.prepare(replacement.pendingPhysicalStep().orElseThrow()).equals(replacement)
                    : previous.withStatus(replacement.status()).equals(replacement))))
            throw new IllegalArgumentException("shipment transition has a stale predecessor");
        var next = new LinkedHashMap<>(shipments); next.put(replacement.id(), replacement); return new ShipmentState(next, missions);
    }
    public ShipmentState retire(SubjectId id, long revision) {
        Shipment current = shipments.get(id);
        if (current == null || !current.terminal() || current.revision() != revision || current.reception().isPresent())
            throw new IllegalArgumentException("only an exact terminal shipment can retire");
        if (missions.values().stream().anyMatch(m -> m.shipmentIds().contains(id)))
            throw new IllegalArgumentException("shipment retirement retains a transport mission reference");
        var next = new LinkedHashMap<>(shipments); next.remove(id); return new ShipmentState(next, missions);
    }
    public ShipmentState resume(Shipment previous, io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId successor) {
        if (!previous.equals(shipments.get(previous.id()))) throw new IllegalArgumentException("shipment resumption has a stale predecessor");
        var next = new LinkedHashMap<>(shipments); next.put(previous.id(), previous.resumed(successor)); return new ShipmentState(next, missions);
    }
    public boolean holds(SubjectId claimId) {
        return shipments.values().stream().anyMatch(s -> !s.terminal() && s.authorization().claimId().equals(claimId));
    }
    public ShipmentState admitMission(TransportMission mission) {
        if (missions.containsKey(mission.id()) || mission.stage() != TransportMission.Stage.LOADING || mission.revision() != 1)
            throw new IllegalArgumentException("mission admission requires a fresh declaration");
        var next = new LinkedHashMap<>(missions); next.put(mission.id(), mission); return new ShipmentState(shipments, next);
    }
    public ShipmentState advanceMission(TransportMission mission, TransportMission.Stage stage) {
        if (!mission.equals(missions.get(mission.id()))) throw new IllegalArgumentException("stale mission predecessor");
        var next = new LinkedHashMap<>(missions); next.put(mission.id(), mission.advance(stage)); return new ShipmentState(shipments, next);
    }
    public ShipmentState replaceSupplies(TransportMission mission,
            io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyLoad supplies) {
        if (!mission.equals(missions.get(mission.id()))) throw new IllegalArgumentException("stale provisioning predecessor");
        var next = new LinkedHashMap<>(missions); next.put(mission.id(), mission.withSupplies(supplies));
        return new ShipmentState(shipments, next);
    }
    public ShipmentState abortLoadingMission(TransportMission mission) {
        if (!mission.equals(missions.get(mission.id()))) throw new IllegalArgumentException("stale loading-abort predecessor");
        var next = new LinkedHashMap<>(missions); next.put(mission.id(), mission.abortLoading());
        return new ShipmentState(shipments, next);
    }
    public ShipmentState retireMission(TransportMission mission) {
        if (!mission.equals(missions.get(mission.id())) || mission.stage() != TransportMission.Stage.COMPLETE)
            throw new IllegalArgumentException("only an exact completed mission may retire");
        var next = new LinkedHashMap<>(missions); next.remove(mission.id());
        var retained = new LinkedHashMap<>(shipments);
        for (var id : mission.shipmentIds()) {
            var shipment = retained.get(id);
            if (shipment == null || !shipment.terminal() || shipment.reception().isPresent() || shipment.pendingPhysicalStep().isPresent()
                    || !shipment.transportMissionId().equals(java.util.Optional.of(mission.id())))
                throw new IllegalArgumentException("mission retirement retains unfinished shipment evidence");
            retained.remove(id);
        }
        return new ShipmentState(retained, next);
    }
}
