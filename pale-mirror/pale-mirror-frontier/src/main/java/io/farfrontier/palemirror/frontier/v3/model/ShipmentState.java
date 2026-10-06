package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.HashSet;

/** Bounded live/terminal transport records; no resource totals, wallets or actor poses. */
public record ShipmentState(Map<SubjectId, Shipment> shipments) {
    public static final int MAX_SHIPMENTS = 1024;
    public ShipmentState {
        shipments = Map.copyOf(Objects.requireNonNull(shipments));
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
    public static ShipmentState empty() { return new ShipmentState(Map.of()); }
    public ShipmentState admit(Shipment shipment) {
        if (shipments.containsKey(shipment.id()) || shipment.status() != Shipment.Status.AWAITING_LOAD
                || shipment.revision() != 1 || shipment.pendingPhysicalStep().isPresent() || shipment.reception().isPresent())
            throw new IllegalArgumentException("shipment admission needs one fresh declared obligation");
        var next = new LinkedHashMap<>(shipments); next.put(shipment.id(), shipment); return new ShipmentState(next);
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
        var next = new LinkedHashMap<>(shipments); next.put(replacement.id(), replacement); return new ShipmentState(next);
    }
    public ShipmentState retire(SubjectId id, long revision) {
        Shipment current = shipments.get(id);
        if (current == null || !current.terminal() || current.revision() != revision || current.reception().isPresent())
            throw new IllegalArgumentException("only an exact terminal shipment can retire");
        var next = new LinkedHashMap<>(shipments); next.remove(id); return new ShipmentState(next);
    }
    public ShipmentState resume(Shipment previous, io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId successor) {
        if (!previous.equals(shipments.get(previous.id()))) throw new IllegalArgumentException("shipment resumption has a stale predecessor");
        var next = new LinkedHashMap<>(shipments); next.put(previous.id(), previous.resumed(successor)); return new ShipmentState(next);
    }
    public boolean holds(SubjectId claimId) {
        return shipments.values().stream().anyMatch(s -> !s.terminal() && s.authorization().claimId().equals(claimId));
    }
}
