package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Deterministic permission producer, not an assignment, actor or resource owner. */
public final class SettlementStaffingPolicy {
    private final List<SettlementStaffingPort> ports;
    public SettlementStaffingPolicy(List<SettlementStaffingPort> ports) {
        this.ports = List.copyOf(ports);
        if (ports.size() != ResidentWorkKind.values().length
                || !ports.stream().map(SettlementStaffingPort::kind).collect(java.util.stream.Collectors.toSet())
                    .equals(EnumSet.allOf(ResidentWorkKind.class)))
            throw new IllegalArgumentException("staffing composition requires every kind exactly once");
    }
    public ResidentWorkPermissions propose(FrontierWorldState state, SubjectId home) {
        var old = SettlementWorkPolicy.permissions(state, home);
        var assignments = HumanAssignmentProjection.compile(state);
        var household = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(home)).sorted(Comparator.comparing(ResidentProfile::id)).toList();
        var local = household.stream()
                .filter(resident -> !state.humanPopulation().migrations().containsKey(resident.id())
                        && state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .sorted(Comparator.comparing(ResidentProfile::id)).toList();
        Set<SubjectId> away = new HashSet<>();
        state.unitGroups().groups().values().stream()
                .filter(group -> group.phase() != io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Phase.CLOSED)
                .flatMap(group -> group.members().stream()).forEach(member -> away.add(member.actorId()));
        state.shipments().shipments().values().stream().filter(shipment -> !shipment.terminal())
                .forEach(shipment -> away.add(shipment.execution().actorId()));
        var demands = ports.stream().map(port -> {
            var demand = port.assess(state, home, state.bootstrap().ruleset().labour().entries().get(port.kind()));
            if (demand.kind() != port.kind() || demand.capability() != port.capability())
                throw new IllegalArgumentException("forged staffing family or capability declaration");
            return demand;
        }).sorted(Comparator.comparingInt(SettlementStaffingPort.Demand::priority)
                .thenComparingInt(value -> value.kind().wireTag())).toList();
        var next = new EnumMap<ResidentWorkKind, Map<SubjectId, Integer>>(ResidentWorkKind.class);
        var reserves = new EnumMap<ResidentWorkKind, Integer>(ResidentWorkKind.class);
        Set<SubjectId> allocatedCore = new HashSet<>();
        for (var demand : demands) {
            var workers = new LinkedHashMap<SubjectId, Integer>();
            // Busy ownership survives reassessment even when new work demand disappears.
            for (var resident : household) if (!assignments.idle(resident.id()) && old.permits(demand.kind(), resident.id()))
                workers.put(resident.id(), old.priority(demand.kind(), resident.id()));
            for (var id : demand.retainedWorkers()) {
                var resident = state.humanPopulation().resident(id);
                if (resident == null || !resident.settlementId().equals(home) || !old.permits(demand.kind(), id)
                        || assignments.idle(id)) throw new IllegalArgumentException("staffing demand lost its exact retained worker"
                        + "; home=" + home.value() + "; kind=" + demand.kind() + "; actor=" + id.value()
                        + "; permitted=" + old.permits(demand.kind(), id) + "; assignment=" + assignments.assignments().get(id));
            }
            Set<SubjectId> selected = new HashSet<>();
            demand.retainedWorkers().stream().filter(id -> !away.contains(id))
                    .filter(id -> local.stream().anyMatch(resident -> resident.id().equals(id))).forEach(selected::add);
            var candidates = local.stream()
                    .filter(resident -> !away.contains(resident.id()) && assignments.idle(resident.id())
                            && (demand.minimumLocalStaff() == 0 || !allocatedCore.contains(resident.id()))
                            && resident.capability(demand.capability()) > 0)
                    .sorted(Comparator.comparingInt((ResidentProfile resident) -> old.permits(demand.kind(), resident.id()) ? 0 : 1)
                            .thenComparing(Comparator.comparingInt((ResidentProfile resident) -> resident.capability(demand.capability())).reversed())
                            .thenComparing(ResidentProfile::id)).toList();
            for (var resident : candidates) {
                if (selected.size() >= demand.targetWorkers() || workers.size() >= ResidentWorkPermissions.MAX_WORKERS_PER_KIND) break;
                selected.add(resident.id()); workers.put(resident.id(), demand.priority());
            }
            // Essential local rosters are disjoint. Optional work remains a lower-priority offer.
            if (demand.minimumLocalStaff() > 0) allocatedCore.addAll(selected);
            next.put(demand.kind(), Map.copyOf(workers));
            long livingLocal = workers.keySet().stream().filter(id -> !away.contains(id))
                    .filter(id -> state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE).count();
            reserves.put(demand.kind(), (int) Math.min(demand.minimumLocalStaff(), livingLocal));
        }
        return new ResidentWorkPermissions(next, reserves);
    }
}
